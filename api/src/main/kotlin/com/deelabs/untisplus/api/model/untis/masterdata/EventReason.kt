package com.deelabs.untisplus.api.model.untis.masterdata

import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import kotlinx.serialization.Serializable

@Serializable
data class EventReason(
	val id: Long,
	val name: String,
	val longName: String,
	val elementType: ElementType,
	val groupId: Long,
	val active: Boolean
)
