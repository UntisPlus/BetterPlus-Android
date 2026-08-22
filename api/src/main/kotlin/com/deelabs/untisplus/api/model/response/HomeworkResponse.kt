package com.deelabs.untisplus.api.model.response

import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import com.deelabs.untisplus.api.model.untis.timetable.Lesson
import kotlinx.serialization.Serializable

@Serializable
data class HomeworkResponse(
		val result: HomeworkResult? = null
) : BaseResponse()

@Serializable
data class HomeworkResult(
		val homeWorks: List<HomeWork>,
		val lessonsById: Map<Long, Lesson>
)
