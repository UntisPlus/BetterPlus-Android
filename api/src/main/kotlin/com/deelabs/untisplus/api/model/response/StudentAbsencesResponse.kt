package com.deelabs.untisplus.api.model.response

import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import kotlinx.serialization.Serializable

@Serializable
data class StudentAbsencesResponse(
	val result: StudentAbsencesResult? = null
) : BaseResponse()

@Serializable
data class StudentAbsencesResult(
	val absences: List<StudentAbsence>
)
