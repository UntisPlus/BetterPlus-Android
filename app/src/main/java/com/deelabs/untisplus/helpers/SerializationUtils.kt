package com.deelabs.untisplus.helpers

import com.deelabs.untisplus.BuildConfig
import kotlinx.serialization.json.Json

object SerializationUtils {
	@Deprecated("Replace with injected value")
	fun getJSON() = Json {
		ignoreUnknownKeys = !BuildConfig.DEBUG
		isLenient = !BuildConfig.DEBUG
		encodeDefaults = true
	}
}
