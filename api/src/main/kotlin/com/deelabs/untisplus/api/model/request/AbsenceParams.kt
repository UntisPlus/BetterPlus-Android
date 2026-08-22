package com.deelabs.untisplus.api.model.request

import com.deelabs.untisplus.api.model.untis.Auth
import com.deelabs.untisplus.api.serializer.Time
import kotlinx.serialization.Serializable

@Serializable
data class CreateImmediateAbsenceParams(
	val periodId: Long,
	val studentId: Long,
	val startTime: Time,
	val endTime: Time,
	val auth: Auth
) : BaseParams()

@Serializable
data class DeleteAbsenceParams(
	val absenceId: Long,
	val auth: Auth
) : BaseParams()

@Serializable
data class SubmitAbsencesCheckedParams(
	val periodIds: Set<Long>,
	val auth: Auth
) : BaseParams()
