package com.deelabs.untisplus.api.model.untis.absence

import kotlinx.serialization.Serializable

@Serializable
data class StudentAttendance(
	val studentId: Long,
	val startDateTime: String,
	val endDateTime: String
)
