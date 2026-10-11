package com.deelabs.untisplus.api.model.proxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models for the `untis-proxy`'s own endpoints (not stock WebUntis). The wire
 * contract is `docs/PROXY-API.md`.
 */

// --- §8 meta ---

@Serializable
data class ProxyStatus(
	val status: String,
	val mode: String? = null,
	val version: String? = null,
	@SerialName("uptime_sec") val uptimeSec: Long? = null
)

@Serializable
data class ProxyHealth(
	val status: String,
	@SerialName("uptime_sec") val uptimeSec: Long? = null,
	val schools: Int? = null,
	val degraded: Int? = null
)

@Serializable
data class ProxyMe(
	val username: String,
	val level: String,
	val permissions: ProxyPermissions = ProxyPermissions()
)

@Serializable
data class ProxyPermissions(
	val recon: Boolean = false,
	val boosted: Boolean = false,
	val editor: Boolean = false
)

@Serializable
data class ProxyError(
	val error: String? = null
)

// --- §3 homework ---

@Serializable
data class ProxyHomeworkFlag(
	val homeworkId: Long,
	val done: Boolean,
	val doneAt: String? = null
)

@Serializable
data class ProxyHomeworkFlags(
	val school: String? = null,
	val flags: List<ProxyHomeworkFlag> = emptyList()
)

@Serializable
data class ProxyHomeworkDoneRequest(
	val homeworkId: Long,
	val done: Boolean
)

// --- §4 absence notes ---

@Serializable
data class ProxyAbsenceNote(
	val absenceKey: Long,
	val note: String? = null,
	val updatedAt: String? = null
)

@Serializable
data class ProxyAbsenceNotes(
	val school: String? = null,
	val notes: List<ProxyAbsenceNote> = emptyList()
)

@Serializable
data class ProxyAbsenceNoteRequest(
	val absenceKey: Long,
	val note: String
)

// --- §5 subscriptions ---

/**
 * The element a webhook/ntfy subscription points at, using the same keys as the
 * calendar mint (`classId`, `personal`/`personId`, `teacherId`, `roomId`,
 * `subjectId`). At most one is set; none means the school-wide target, which is
 * privileged-only. `docs/PROXY-API.md` §5.
 */
data class ProxySubscriptionTarget(
	val classId: Long? = null,
	val personId: Long? = null,
	val personal: Boolean? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null
)

@Serializable
data class ProxyWebhook(
	val id: Long,
	val school: String? = null,
	@SerialName("elementType") val elementType: String? = null,
	@SerialName("elementId") val elementId: Long? = null,
	val name: String? = null,
	val classId: Long? = null,
	val url: String,
	val createdBy: String? = null
)

@Serializable
data class ProxyWebhooks(
	val webhooks: List<ProxyWebhook> = emptyList()
)

@Serializable
data class ProxyWebhookRequest(
	val classId: Long? = null,
	val personId: Long? = null,
	val personal: Boolean? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null,
	val url: String,
	val secret: String? = null
) {
	constructor(target: ProxySubscriptionTarget, url: String, secret: String? = null) : this(
		target.classId, target.personId, target.personal,
		target.teacherId, target.roomId, target.subjectId, url, secret
	)
}

@Serializable
data class ProxyNtfyTopic(
	val id: Long,
	val school: String? = null,
	@SerialName("elementType") val elementType: String? = null,
	@SerialName("elementId") val elementId: Long? = null,
	val name: String? = null,
	val classId: Long? = null,
	val topic: String,
	val baseUrl: String? = null,
	val createdBy: String? = null
)

@Serializable
data class ProxyNtfyTopics(
	val topics: List<ProxyNtfyTopic> = emptyList()
)

@Serializable
data class ProxyNtfyRequest(
	val classId: Long? = null,
	val personId: Long? = null,
	val personal: Boolean? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null,
	val topic: String,
	val baseUrl: String? = null
) {
	constructor(target: ProxySubscriptionTarget, topic: String, baseUrl: String? = null) : this(
		target.classId, target.personId, target.personal,
		target.teacherId, target.roomId, target.subjectId, topic, baseUrl
	)
}

@Serializable
data class ProxySubscriptionTest(
	val ok: Boolean = false,
	val status: Int? = null,
	val topic: String? = null,
	val server: String? = null
)

@Serializable
data class ProxyDeleted(
	val deleted: Boolean = false
)

@Serializable
data class ProxyCreated(
	val id: Long
)

// --- §7 change detection ---

@Serializable
data class ProxyChangeDigest(
	val title: String? = null,
	val summary: String? = null,
	val lines: List<String> = emptyList(),
	val changed: Int = 0,
	val added: Int = 0,
	val removed: Int = 0
)

/**
 * A single changed period. The row fields are deliberately **PascalCase** — the
 * proxy serialises a Go struct without JSON tags (`PROXY-API.md` §7.1).
 */
@Serializable
data class ProxyTimetableChangeRow(
	@SerialName("PeriodID") val periodId: Long,
	@SerialName("Kind") val kind: String,
	@SerialName("Start") val start: String,
	@SerialName("End") val end: String,
	@SerialName("Subject") val subject: String? = null,
	@SerialName("Room") val room: String? = null,
	@SerialName("Teacher") val teacher: String? = null,
	@SerialName("Description") val description: String? = null,
	@SerialName("ModVer") val modVer: Int? = null
) {
	companion object {
		const val KIND_ADDED = "ADDED"
		const val KIND_CHANGED = "CHANGED"
		const val KIND_REMOVED = "REMOVED"
	}
}

@Serializable
data class ProxyTimetableChanges(
	val school: String? = null,
	val classId: Long? = null,
	val since: Long = 0,
	val current: Long = 0,
	val eventVersion: Long? = null,
	val sinceEvents: Long? = null,
	val changes: List<ProxyTimetableChangeRow> = emptyList()
)

/**
 * Union of the three SSE event payloads on `/api/timetable/stream`
 * (`PROXY-API.md` §6). [event] is the SSE `event:` name.
 */
@Serializable
data class ProxySseEventPayload(
	val event: String? = null,
	val school: String? = null,
	val classId: Long? = null,
	val current: Long? = null,
	val version: Long? = null,
	val eventVersion: Long? = null,
	val reason: String? = null,
	val changes: List<ProxyTimetableChangeRow> = emptyList()
)

// --- §9 calendar ---

@Serializable
data class ProxyCalendarTokenRequest(
	val classId: Long? = null,
	val personal: Boolean? = null,
	val personId: Long? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null,
	val timezone: String? = null,
	val days: Int? = null
)

@Serializable
data class ProxyCalendarToken(
	val type: String? = null,
	val id: Long? = null,
	val name: String? = null,
	val token: String,
	val url: String,
	val timezone: String? = null,
	val days: Int? = null,
	val horizon: String? = null,
	val created: String? = null,
	val lastUsed: String? = null,
	val createdBy: String? = null,
	val classId: Long? = null,
	val personId: Long? = null
)

/** One subscribable calendar target, as listed by `/api/calendar/options`. */
@Serializable
data class ProxyCalendarOption(
	val id: Long,
	val name: String = ""
)

/** The signed-in user's own personal timetable target. */
@Serializable
data class ProxyCalendarPerson(
	val personId: Long,
	val name: String = ""
)

@Serializable
data class ProxyCalendarOptions(
	val school: String? = null,
	val personal: ProxyCalendarPerson? = null,
	@SerialName("class") val classes: List<ProxyCalendarOption> = emptyList(),
	val teacher: List<ProxyCalendarOption> = emptyList(),
	val room: List<ProxyCalendarOption> = emptyList(),
	val subject: List<ProxyCalendarOption> = emptyList(),
	val technik: ProxyCalendarOption? = null
)
