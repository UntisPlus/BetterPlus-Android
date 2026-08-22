package com.deelabs.untisplus.api.model.untis.masterdata

import kotlinx.serialization.Serializable

@Serializable
data class EventReasonGroup(
	val id: Long,
	val name: String,
	val longName: String,
	val active: Boolean
)
