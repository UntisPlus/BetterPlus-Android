package com.deelabs.untisplus.api.model.untis.masterdata

import com.deelabs.untisplus.api.model.untis.enumeration.DutyType
import kotlinx.serialization.Serializable

@Serializable
data class Duty(
	val id: Long,
	val name: String,
	val longName: String,
	val type: DutyType
)
