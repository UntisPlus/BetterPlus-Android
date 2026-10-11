package com.deelabs.untisplus.data.repository

import android.content.Context
import android.net.Uri
import com.deelabs.untisplus.data.database.entities.ImportedCalendarDao
import com.deelabs.untisplus.data.database.entities.ImportedCalendarEntity
import com.deelabs.untisplus.data.database.entities.ImportedCalendarEventEntity
import com.deelabs.untisplus.ical.ICalParser
import com.deelabs.untisplus.ical.RecurrenceExpander
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Stores and refreshes calendars imported from external `.ics` feeds. Fetched
 * files are parsed with [ICalParser] and recurring events are expanded into
 * concrete occurrences across a moving horizon.
 */
@Singleton
class ImportedCalendarRepository @Inject constructor(
	private val userRepository: UserRepository,
	private val importedCalendarDao: ImportedCalendarDao,
	@Named("ics") private val httpClient: HttpClient,
	@ApplicationContext private val context: Context,
) {
	companion object {
		private const val PAST_WEEKS = 2L
		private const val HORIZON_WEEKS = 16L

		/** Normalizes `webcal://` subscriptions to their HTTPS equivalent. */
		fun normalizeUrl(raw: String): String {
			val trimmed = raw.trim()
			return when {
				trimmed.startsWith("webcal://", ignoreCase = true) -> "https://" + trimmed.substring(9)
				trimmed.contains("://") -> trimmed
				else -> "https://$trimmed"
			}
		}
	}

	fun calendarsFlow(): Flow<List<ImportedCalendarEntity>> {
		val user = userRepository.currentUser ?: return flowOf(emptyList())
		return importedCalendarDao.calendarsFlow(user.id)
	}

	fun eventsFlow(from: LocalDate, to: LocalDate): Flow<List<ImportedCalendarEventEntity>> {
		val user = userRepository.currentUser ?: return flowOf(emptyList())
		return importedCalendarDao.eventsFlow(
			userId = user.id,
			from = ImportedCalendarEventEntity.format(from.atStartOfDay()),
			to = ImportedCalendarEventEntity.format(to.atTime(LocalTime.MAX)),
		)
	}

	suspend fun events(from: LocalDate, to: LocalDate): List<ImportedCalendarEventEntity> {
		val user = userRepository.currentUser ?: return emptyList()
		return importedCalendarDao.events(
			userId = user.id,
			from = ImportedCalendarEventEntity.format(from.atStartOfDay()),
			to = ImportedCalendarEventEntity.format(to.atTime(LocalTime.MAX)),
		)
	}

	suspend fun addUrl(name: String, url: String, color: Int): Long {
		val userId = userRepository.currentUser?.id ?: return -1
		val id = importedCalendarDao.insertCalendar(
			ImportedCalendarEntity(
				userId = userId,
				name = name.ifBlank { url },
				url = normalizeUrl(url),
				source = ImportedCalendarEntity.SOURCE_URL,
				color = color,
			)
		)
		refresh(id)
		return id
	}

	suspend fun importFile(name: String, uri: Uri, color: Int): Long = withContext(Dispatchers.IO) {
		val userId = userRepository.currentUser?.id ?: return@withContext -1L
		val directory = File(context.filesDir, "imported_calendars").apply { mkdirs() }
		val file = File(directory, "calendar_${System.currentTimeMillis()}.ics")
		context.contentResolver.openInputStream(uri)?.use { input ->
			file.outputStream().use { output -> input.copyTo(output) }
		} ?: return@withContext -1L

		val id = importedCalendarDao.insertCalendar(
			ImportedCalendarEntity(
				userId = userId,
				name = name.ifBlank { file.name },
				localPath = file.absolutePath,
				source = ImportedCalendarEntity.SOURCE_FILE,
				color = color,
			)
		)
		refresh(id)
		id
	}

	suspend fun delete(calendar: ImportedCalendarEntity) {
		importedCalendarDao.deleteCalendar(calendar)
		calendar.localPath?.let { runCatching { File(it).delete() } }
	}

	suspend fun setEnabled(calendar: ImportedCalendarEntity, enabled: Boolean) {
		importedCalendarDao.updateCalendar(calendar.copy(enabled = enabled))
	}

	suspend fun setColor(calendar: ImportedCalendarEntity, color: Int) {
		importedCalendarDao.updateCalendar(calendar.copy(color = color))
		refresh(calendar.id)
	}

	suspend fun refreshAll() {
		importedCalendarDao.enabledCalendars().forEach { calendar ->
			runCatching { refresh(calendar.id) }
		}
	}

	suspend fun refresh(calendarId: Long) {
		val calendar = importedCalendarDao.calendar(calendarId) ?: return

		val source: String
		val etag: String?
		val lastModified: String?

		try {
			if (calendar.source == ImportedCalendarEntity.SOURCE_FILE) {
				source = calendar.localPath?.let { File(it).readText() } ?: return
				etag = null
				lastModified = null
			} else {
				val url = calendar.url ?: return
				val response = httpClient.get(url) {
					calendar.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
					calendar.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
				}

				if (response.status == HttpStatusCode.NotModified) {
					importedCalendarDao.updateRefreshState(
						id = calendarId,
						lastRefresh = System.currentTimeMillis(),
						etag = calendar.etag,
						lastModified = calendar.lastModified,
						lastError = null,
					)
					return
				}
				if (!response.status.isSuccess()) {
					importedCalendarDao.updateRefreshState(
						id = calendarId,
						lastRefresh = calendar.lastRefresh,
						etag = calendar.etag,
						lastModified = calendar.lastModified,
						lastError = "HTTP ${response.status.value}",
					)
					return
				}

				source = response.bodyAsText()
				etag = response.headers[HttpHeaders.ETag]
				lastModified = response.headers[HttpHeaders.LastModified]
			}
		} catch (e: Exception) {
			importedCalendarDao.updateRefreshState(
				id = calendarId,
				lastRefresh = calendar.lastRefresh,
				etag = calendar.etag,
				lastModified = calendar.lastModified,
				lastError = e.message ?: "Error",
			)
			return
		}

		val today = LocalDate.now()
		val instances = RecurrenceExpander.expand(
			events = ICalParser.parse(source),
			from = today.minusWeeks(PAST_WEEKS),
			to = today.plusWeeks(HORIZON_WEEKS),
		)

		importedCalendarDao.replaceEvents(
			calendarId = calendarId,
			events = instances.map { instance ->
				ImportedCalendarEventEntity(
					calendarId = calendarId,
					start = ImportedCalendarEventEntity.format(instance.start),
					end = ImportedCalendarEventEntity.format(instance.end),
					summary = instance.summary,
					description = instance.description,
					location = instance.location,
					allDay = instance.allDay,
					color = calendar.color,
				)
			},
		)
		importedCalendarDao.updateRefreshState(
			id = calendarId,
			lastRefresh = System.currentTimeMillis(),
			etag = etag,
			lastModified = lastModified,
			lastError = null,
		)
	}
}
