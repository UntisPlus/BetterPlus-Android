package com.deelabs.untisplus.api.model.untis.absence

import com.deelabs.untisplus.api.serializer.DateTime
import kotlinx.serialization.Serializable

@Serializable
data class StudentAbsence(
	val id: Long,
	val studentId: Long,
	val klasseId: Long,
	val startDateTime: DateTime,
	val endDateTime: DateTime,
	val owner: Boolean,
	val excused: Boolean,
	val excuse: Excuse?,
	val absenceReasonId: Long,
	val absenceReason: String,
	val text: String,
	val manualNotificationStatus: String? = null, // observed: "NOT_NOTIFIED",
	val autoNotificationStatus: String? = null, // observed: "NO_AUTO_PARENT_NOTIFICATION_AND_NO_ATTEMPT",
	val studentOfAge: Boolean? = null, // observed: false
	// untis-proxy injected (docs/PROXY-API.md §6.7); null/absent upstream.
	val note: String? = null,
	val noteUpdatedAt: String? = null,
	val derived: AbsenceDerived? = null,
)

@Serializable
data class AbsenceDerived(
	val classId: Long? = null,
	val className: String? = null,
	val weekday: String? = null,
	val date: String? = null,
	val subject: String? = null,
	val reason: String? = null,
)
