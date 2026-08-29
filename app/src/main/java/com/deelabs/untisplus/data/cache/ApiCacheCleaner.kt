package com.deelabs.untisplus.data.cache

import java.io.File

/**
 * Clears all JSON-RPC response caches that are tied to a specific API endpoint.
 * Must be called whenever a profile's effective API endpoint changes,
 * so cached responses from the previous endpoint are not served.
 */
object ApiCacheCleaner {
	private val CACHE_DIRECTORIES = listOf("timetable", "periodData", "infocenter", "messenger")

	fun clearApiCaches(cacheDir: File) {
		CACHE_DIRECTORIES.asSequence()
			.map { File(cacheDir, it) }
			.filter { it.exists() }
			.forEach { it.deleteRecursively() }
	}
}
