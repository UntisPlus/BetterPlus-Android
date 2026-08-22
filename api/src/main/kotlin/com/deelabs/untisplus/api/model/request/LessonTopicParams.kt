package com.deelabs.untisplus.api.model.request

import com.deelabs.untisplus.api.model.untis.Auth
import kotlinx.serialization.Serializable

@Serializable
data class SubmitLessonTopicParams(
	val ttId: Long,
	val lessonTopic: String,
	val auth: Auth
) : BaseParams()
