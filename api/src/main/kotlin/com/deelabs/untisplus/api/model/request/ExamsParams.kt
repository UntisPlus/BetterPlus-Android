package com.deelabs.untisplus.api.model.request

import com.deelabs.untisplus.api.model.untis.Auth
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable

@Serializable
data class ExamsParams(
	val id: Long,
	val type: ElementType,
	val startDate: Date,
	val endDate: Date,
	val auth: Auth
) : BaseParams()
