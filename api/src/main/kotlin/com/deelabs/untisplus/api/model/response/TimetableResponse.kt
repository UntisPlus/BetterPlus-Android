package com.deelabs.untisplus.api.model.response

import com.deelabs.untisplus.api.model.untis.MasterData
import com.deelabs.untisplus.api.model.untis.Timetable
import kotlinx.serialization.Serializable

@Serializable
data class TimetableResponse(
		val result: TimetableResult? = null
) : BaseResponse()

@Serializable
data class TimetableResult(
	val timetable: Timetable,
	val masterData: MasterData
)
