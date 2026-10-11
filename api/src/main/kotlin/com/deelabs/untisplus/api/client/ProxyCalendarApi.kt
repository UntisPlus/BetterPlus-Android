package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

/**
 * Calendar (`.ics`) subscription tokens (`docs/PROXY-API.md` §9). Idempotent per
 * target: calling again for the same element returns the existing token/url.
 */
open class ProxyCalendarApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = ApiClient.DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun token(
		baseUrl: String,
		request: ProxyCalendarTokenRequest,
		school: String? = null
	): ProxyCalendarToken =
		post(proxyUrl(baseUrl, "/api/calendar/token", school), request).body()
}
