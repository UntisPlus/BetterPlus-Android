package com.deelabs.untisplus.api.client

import io.ktor.http.encodeURLParameter

/**
 * Builds a proxy URL by appending [path] to the origin [base] and, when [school]
 * is present, the `?school=` query parameter the proxy expects.
 */
internal fun proxyUrl(base: String, path: String, school: String? = null): String =
	if (school.isNullOrBlank()) "$base$path" else "$base$path?school=${school.encodeURLParameter()}"
