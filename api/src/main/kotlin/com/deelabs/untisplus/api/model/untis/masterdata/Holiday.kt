package com.deelabs.untisplus.api.model.untis.masterdata

import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable

@Serializable
data class Holiday(
	val id: Long,
	val name: String = "",
	val longName: String = "",
	val startDate: Date? = null,
	val endDate: Date? = null
)
