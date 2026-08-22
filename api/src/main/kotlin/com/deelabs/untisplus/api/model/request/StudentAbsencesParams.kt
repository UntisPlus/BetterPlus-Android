package com.deelabs.untisplus.api.model.request

import com.deelabs.untisplus.api.model.untis.Auth
import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable

@Serializable
data class StudentAbsencesParams(
	val startDate: Date,
	val endDate: Date,
	val includeExcused: Boolean,
	val includeUnExcused: Boolean,
	val auth: Auth
) : BaseParams()
