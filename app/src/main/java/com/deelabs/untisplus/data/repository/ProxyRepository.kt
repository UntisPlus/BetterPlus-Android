package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.client.ProxyAbsenceApi
import com.deelabs.untisplus.api.client.ProxyCalendarApi
import com.deelabs.untisplus.api.client.ProxyHomeworkApi
import com.deelabs.untisplus.api.client.ProxyStatusApi
import com.deelabs.untisplus.api.client.ProxyStreamEvent
import com.deelabs.untisplus.api.client.ProxySubscriptionsApi
import com.deelabs.untisplus.api.client.ProxyTimetableApi
import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
import com.deelabs.untisplus.api.model.proxy.ProxyCreated
import com.deelabs.untisplus.api.model.proxy.ProxyDeleted
import com.deelabs.untisplus.api.model.proxy.ProxyHealth
import com.deelabs.untisplus.api.model.proxy.ProxyHomeworkFlag
import com.deelabs.untisplus.api.model.proxy.ProxyHomeworkFlags
import com.deelabs.untisplus.api.model.proxy.ProxyMe
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyTopics
import com.deelabs.untisplus.api.model.proxy.ProxyPermissions
import com.deelabs.untisplus.api.model.proxy.ProxyStatus
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTest
import com.deelabs.untisplus.api.model.proxy.ProxyTimetableChanges
import com.deelabs.untisplus.api.model.proxy.ProxyWebhooks
import com.deelabs.untisplus.data.database.entities.User
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for the `untis-proxy`'s own endpoints (`/status`, `/me`, and the
 * `/api/…` feature surface).
 *
 * The proxy authenticates these with the `JSESSIONID` cookie captured during the
 * intern login. On a `401` the session is silently re-established from the
 * stored app secret and the call is retried once; if that fails the caller sees
 * the error (and the existing re-login UI can take over).
 */
@Singleton
class ProxyRepository @Inject constructor(
	private val userRepository: UserRepository,
	private val userDataApi: UserDataApi,
	private val proxyStatusApi: ProxyStatusApi,
	private val proxyHomeworkApi: ProxyHomeworkApi,
	private val proxyAbsenceApi: ProxyAbsenceApi,
	private val proxyCalendarApi: ProxyCalendarApi,
	private val proxySubscriptionsApi: ProxySubscriptionsApi,
	private val proxyTimetableApi: ProxyTimetableApi,
) {
	private val _permissions = MutableStateFlow<ProxyPermissionsState>(ProxyPermissionsState.Unknown)

	/** Last known proxy tier/permissions for the active user, or unknown. */
	val permissions: StateFlow<ProxyPermissionsState> = _permissions

	/** Whether proxy-only features apply to the current user. */
	val isAvailable: Boolean
		get() = userRepository.currentUser?.usesUntisPlusProxy == true

	// --- §8 meta ---

	suspend fun status(): ProxyStatus = withSession { base, _ -> proxyStatusApi.status(base) }

	suspend fun health(): ProxyHealth = withSession { base, _ -> proxyStatusApi.health(base) }

	/** Fetches `/me` and records the permissions for tier gating. */
	suspend fun refreshMe(): ProxyMe = withSession { base, _ -> proxyStatusApi.me(base) }.also {
		_permissions.value = ProxyPermissionsState.Known(it.permissions)
	}

	// --- §3 homework ---

	suspend fun homeworkFlags(): ProxyHomeworkFlags = withSession { base, school ->
		proxyHomeworkApi.flags(base, school)
	}

	suspend fun setHomeworkDone(homeworkId: Long, done: Boolean): ProxyHomeworkFlag =
		withSession { base, school ->
			proxyHomeworkApi.setDone(base, homeworkId, done, school)
		}

	// --- §4 absence notes ---

	suspend fun absenceNotes() = withSession { base, school -> proxyAbsenceApi.notes(base, school) }

	suspend fun setAbsenceNote(absenceKey: Long, note: String) = withSession { base, school ->
		proxyAbsenceApi.setNote(base, absenceKey, note, school)
	}

	// --- §5 subscriptions ---

	suspend fun webhooks(): ProxyWebhooks = withSession { base, school ->
		proxySubscriptionsApi.webhooks(base, school)
	}

	suspend fun createWebhook(classId: Long, url: String): ProxyCreated = withSession { base, school ->
		proxySubscriptionsApi.createWebhook(base, classId, url, school)
	}

	suspend fun testWebhook(id: Long): ProxySubscriptionTest = withSession { base, school ->
		proxySubscriptionsApi.testWebhook(base, id, school)
	}

	suspend fun deleteWebhook(id: Long): ProxyDeleted = withSession { base, school ->
		proxySubscriptionsApi.deleteWebhook(base, id, school)
	}

	suspend fun ntfyTopics(): ProxyNtfyTopics = withSession { base, school ->
		proxySubscriptionsApi.ntfyTopics(base, school)
	}

	suspend fun createNtfy(classId: Long, topic: String, ntfyBaseUrl: String? = null): ProxyCreated =
		withSession { base, school ->
			proxySubscriptionsApi.createNtfy(base, classId, topic, ntfyBaseUrl, school)
		}

	suspend fun testNtfy(id: Long): ProxySubscriptionTest = withSession { base, school ->
		proxySubscriptionsApi.testNtfy(base, id, school)
	}

	suspend fun deleteNtfy(id: Long): ProxyDeleted = withSession { base, school ->
		proxySubscriptionsApi.deleteNtfy(base, id, school)
	}

	// --- §9 calendar ---

	suspend fun calendarToken(request: ProxyCalendarTokenRequest): ProxyCalendarToken =
		withSession { base, school ->
			proxyCalendarApi.token(base, request, school)
		}

	// --- §7 change detection ---

	/** Polls for changes; `null` means "nothing new" (HTTP 304). */
	suspend fun timetableChanges(
		classId: Long,
		since: Long,
		sinceEvents: Long? = null
	): ProxyTimetableChanges? = withSession { base, school ->
		proxyTimetableApi.changes(base, classId, since, sinceEvents, school)
	}

	/** Opens the SSE change feed. Returns an empty flow when the proxy is unused. */
	fun timetableStream(): Flow<ProxyStreamEvent> {
		val user = userRepository.currentUser ?: return flowOf()
		if (!user.usesUntisPlusProxy) return flowOf()
		return proxyTimetableApi.stream(user.proxyBaseUrl.toString(), user.proxySchoolName)
	}

	private suspend fun <T> withSession(block: suspend (baseUrl: String, school: String?) -> T): T {
		val user = userRepository.currentUser
			?: throw IllegalStateException("No active user")
		check(user.usesUntisPlusProxy) { "UntisPlus proxy is not configured for this user" }
		val baseUrl = user.proxyBaseUrl.toString()
		val school = user.proxySchoolName

		return try {
			block(baseUrl, school)
		} catch (e: ClientRequestException) {
			if (e.response.status != HttpStatusCode.Unauthorized) throw e
			if (!refreshSession(user)) throw e
			block(baseUrl, school)
		}
	}

	/**
	 * Re-runs the intern login with the stored credentials, minting a new
	 * `JSESSIONID` in the shared cookie store. Returns false when the account has
	 * no stored secret to log in with (e.g. anonymous).
	 */
	private suspend fun refreshSession(user: User): Boolean {
		val key = user.key ?: return false
		return try {
			userDataApi.getUserData(user.jsonRpcApiUrl.toString(), user.user, key)
			true
		} catch (e: Exception) {
			false
		}
	}

	sealed interface ProxyPermissionsState {
		data object Unknown : ProxyPermissionsState
		data class Known(val permissions: ProxyPermissions) : ProxyPermissionsState
	}
}
