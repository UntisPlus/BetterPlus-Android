package com.deelabs.untisplus.api.model.response

import com.deelabs.untisplus.api.model.untis.SchoolInfo
import kotlinx.serialization.Serializable

@Serializable
data class SchoolSearchResponse(
		val result: SchoolSearchResult? = null
) : BaseResponse()

@Serializable
data class SchoolSearchResult(
		val size: Int,
		val schools: List<SchoolInfo>
)
