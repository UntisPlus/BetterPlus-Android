package com.deelabs.untisplus.api.model.response

import com.deelabs.untisplus.api.model.untis.MasterData
import com.deelabs.untisplus.api.model.untis.Person
import com.deelabs.untisplus.api.model.untis.Timetable
import com.deelabs.untisplus.api.model.untis.timetable.PeriodData
import kotlinx.serialization.Serializable

@Serializable
data class PeriodDataResponse(
		val result: PeriodDataResult? = null
) : BaseResponse()

@Serializable
data class PeriodDataResult(
	val dataByTTId: Map<Long, PeriodData>,
	val referencedStudents: List<Person>
)
