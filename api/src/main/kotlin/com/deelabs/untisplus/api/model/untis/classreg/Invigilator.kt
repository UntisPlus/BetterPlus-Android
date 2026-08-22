package com.deelabs.untisplus.api.model.untis.classreg

import com.deelabs.untisplus.api.serializer.Time
import kotlinx.serialization.Serializable

@Serializable
data class Invigilator(
	val id: Long,
	val startTime: Time,
	val endTime: Time
)
