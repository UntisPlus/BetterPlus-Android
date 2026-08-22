package com.deelabs.untisplus.api.model.untis.timetable

import com.deelabs.untisplus.api.model.untis.Attachment
import kotlinx.serialization.Serializable


@Serializable
data class LessonTopic(
	val periodId: Long,
	val text: String,
	val attachments: List<Attachment>,
	val startDateTime: String,
	val endDateTime: String,
	val teachingMethodId: Long
)
