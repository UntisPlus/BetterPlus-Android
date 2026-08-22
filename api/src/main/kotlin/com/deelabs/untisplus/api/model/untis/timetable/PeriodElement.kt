package com.deelabs.untisplus.api.model.untis.timetable

import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import kotlinx.serialization.Serializable

@Serializable
data class PeriodElement(
	val type: ElementType,
	val id: Long,
	val orgId: Long = id
)
