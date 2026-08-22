package com.deelabs.untisplus.api.model.response

import kotlinx.serialization.Serializable

@Serializable
data class AppSharedSecretResponse(
		val result: String? = null
) : BaseResponse()
