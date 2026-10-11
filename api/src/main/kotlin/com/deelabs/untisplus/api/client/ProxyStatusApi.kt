package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyHealth
import com.deelabs.untisplus.api.model.proxy.ProxyMe
import com.deelabs.untisplus.api.model.proxy.ProxyStatus
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

/**
 * Client for the `untis-proxy`'s public and session-scoped meta endpoints:
 * `GET /status`, `GET /healthz` (public) and `GET /me` (needs the `JSESSIONID`
 * cookie captured at login).
 *
 * [baseUrl] is the proxy origin (`scheme://authority`, no `/WebUntis`, no
 * trailing slash) — see `docs/PROXY-API.md` §1.
 */
open class ProxyStatusApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = ApiClient.DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun status(baseUrl: String): ProxyStatus =
		get("$baseUrl/status").body()

	open suspend fun health(baseUrl: String): ProxyHealth =
		get("$baseUrl/healthz").body()

	open suspend fun me(baseUrl: String): ProxyMe =
		get("$baseUrl/me").body()
}
