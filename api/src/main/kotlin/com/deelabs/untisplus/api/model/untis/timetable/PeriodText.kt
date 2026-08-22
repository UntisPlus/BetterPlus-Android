package com.deelabs.untisplus.api.model.untis.timetable

import com.deelabs.untisplus.api.model.untis.Attachment
import kotlinx.serialization.Serializable

@Serializable
data class PeriodText(
	val lesson: String,
	val substitution: String,
	val info: String,
	val attachments: List<Attachment>? = null,
)
