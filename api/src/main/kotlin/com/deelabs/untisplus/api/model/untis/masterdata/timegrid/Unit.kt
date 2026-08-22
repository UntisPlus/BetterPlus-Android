package com.deelabs.untisplus.api.model.untis.masterdata.timegrid

import com.deelabs.untisplus.api.serializer.Time
import kotlinx.serialization.Serializable

@Serializable
data class Unit(
	val label: String,
	val startTime: Time,
	val endTime: Time
)
