package com.deelabs.untisplus.api.model.untis.timetable

import com.deelabs.untisplus.api.serializer.DateTime
import kotlinx.serialization.Serializable

@Serializable
data class OfficeHour(
	val id: Long,
	val startDateTime: DateTime,
	val endDateTime: DateTime,
	val teacherId: Long,
	val imageId: Long,
	val email: String?,
	val phone: String?,
	val displayNameRooms: String,
	val displayNameTeacher: String,
	val registrationPossible: Boolean,
	val registered: Boolean
)
