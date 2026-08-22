package com.deelabs.untisplus.api.model.untis

import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable

@Serializable
data class Timetable(
	val displayableStartDate: Date,
	val displayableEndDate: Date,
	val periods: List<Period> = emptyList()
)
