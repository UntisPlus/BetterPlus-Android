package com.deelabs.untisplus.api.model.request

import com.deelabs.untisplus.api.model.untis.Auth
import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable

@Serializable
data class TimetableParams(
	val id: Long,
	val type: String,
	val startDate: Date,
	val endDate: Date,
	val masterDataTimestamp: Long, // TODO: Try how the response behaves depending on changes to this value
	val timetableTimestamp: Long,
	val timetableTimestamps: List<Long>,
	val auth: Auth
) : BaseParams()
