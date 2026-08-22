package com.deelabs.untisplus.api.model.request

import com.deelabs.untisplus.api.model.untis.Auth
import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable


@Serializable
data class OfficeHoursParams(
	val klasseId: Long,
	val startDate: Date,
	val auth: Auth
) : BaseParams()
