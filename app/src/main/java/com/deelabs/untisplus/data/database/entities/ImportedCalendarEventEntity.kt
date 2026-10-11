package com.deelabs.untisplus.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A single concrete occurrence of an imported calendar event. Recurring events
 * are expanded into one row per occurrence within the configured horizon.
 * [start] and [end] are stored as ISO-8601 local date-times so range queries are
 * simple string comparisons.
 */
@Entity(
	tableName = "ImportedCalendarEvent",
	indices = [Index("calendarId"), Index("start")],
	foreignKeys = [ForeignKey(
		entity = ImportedCalendarEntity::class,
		parentColumns = ["id"],
		childColumns = ["calendarId"],
		onDelete = ForeignKey.CASCADE
	)]
)
data class ImportedCalendarEventEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val calendarId: Long,
	val start: String,
	val end: String,
	val summary: String = "",
	val description: String? = null,
	val location: String? = null,
	val allDay: Boolean = false,
	val color: Int = ImportedCalendarEntity.DEFAULT_COLOR,
) {
	companion object {
		private val FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME

		fun format(dateTime: LocalDateTime): String = dateTime.format(FORMATTER)

		fun parse(value: String): LocalDateTime? =
			runCatching { LocalDateTime.parse(value, FORMATTER) }.getOrNull()
	}
}
