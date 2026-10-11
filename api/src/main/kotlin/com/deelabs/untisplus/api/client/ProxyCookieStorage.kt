package com.deelabs.untisplus.api.client

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.fillDefaults
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [CookiesStorage] with a [clear] method.
 *
 * Ktor's bundled `AcceptAllCookiesStorage` has no way to drop the session, so
 * this lets the app wipe the proxy `JSESSIONID` on sign-out / user deletion.
 */
class ProxyCookieStorage : CookiesStorage {
	private val mutex = Mutex()
	private val container = mutableListOf<Cookie>()

	override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
		container.filter { it.matches(requestUrl) }
	}

	override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
		if (cookie.name.isBlank()) return
		mutex.withLock {
			container.removeAll { it.name == cookie.name && it.matches(requestUrl) }
			container.add(cookie.fillDefaults(requestUrl))
		}
	}

	suspend fun clear() = mutex.withLock {
		container.clear()
	}

	override fun close() = Unit
}
