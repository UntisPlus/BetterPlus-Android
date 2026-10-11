package com.deelabs.untisplus.api.client

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.sse.SSE

/**
 * Installs cookie handling backed by a caller-supplied [storage].
 *
 * The `untis-proxy` authenticates its own API endpoints (`/me`, `/api/…`) with the
 * `JSESSIONID` cookie it sets during `getUserData2017` login. Passing the same
 * [CookiesStorage] instance to several clients (the intern JSON-RPC client used
 * for login and the REST client used for the proxy endpoints) makes that cookie
 * ride along on later calls.
 */
fun HttpClientConfig<*>.installCookieStorage(storage: CookiesStorage) {
	install(HttpCookies) {
		this.storage = storage
	}
}

/** Installs the SSE client plugin used by `/api/timetable/stream`. */
fun HttpClientConfig<*>.installSse() {
	install(SSE)
}
