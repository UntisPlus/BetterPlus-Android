package com.deelabs.untisplus.api.model.untis.masterdata

import kotlinx.serialization.Serializable

@Serializable
data class ExcuseStatus(
	val id: Long,
	val name: String,
	val longName: String,
	val excused: Boolean,
	val active: Boolean
)
