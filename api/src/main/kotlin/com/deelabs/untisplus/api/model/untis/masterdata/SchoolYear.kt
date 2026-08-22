package com.deelabs.untisplus.api.model.untis.masterdata

import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable

@Serializable
data class SchoolYear(
	val id: Long,
	val name: String,
	val startDate: Date,
	val endDate: Date
)
