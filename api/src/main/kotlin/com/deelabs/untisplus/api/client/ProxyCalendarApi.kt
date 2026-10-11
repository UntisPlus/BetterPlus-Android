package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

open class ProxyCalendarApi : ApiClient {
	constructor(
		httpClientEngineFactory: HttpClientEngineFactory<*>? = null,
		httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
		jsonBlock: Json = ApiClient.DEFAULT_JSON
	) : super(httpClientEngineFactory, httpClientConfig, jsonBlock)

	/** Builds on an already-configured client (used by tests). */
	constructor(httpClient: HttpClient) : super(httpClient)

	open suspend fun token(
		baseUrl: String,
		request: ProxyCalendarTokenRequest,
		school: String? = null
	): ProxyCalendarToken =
		post(proxyUrl(baseUrl, "/api/calendar/token", school), request).body()

	/** Lists the calendar targets the signed-in user can subscribe to. */
	open suspend fun options(
		baseUrl: String,
		school: String? = null
	): ProxyCalendarOptions =
		get(proxyUrl(baseUrl, "/api/calendar/options", school)).body()

	/** Lists only the tokens the requesting user created. */
	open suspend fun tokens(
		baseUrl: String,
		school: String? = null
	): List<ProxyCalendarToken> =
		get(proxyUrl(baseUrl, "/api/calendar/tokens", school)).body()

	/** Changes the lookahead window (`days`) of one of the caller's own tokens. */
	open suspend fun updateDays(
		baseUrl: String,
		token: String,
		days: Int,
		school: String? = null
	): ProxyCalendarToken =
		patch(
			proxyUrl(baseUrl, "/api/calendar/${token.encodeURLPathPart()}", school),
			CalendarDaysRequest(days)
		).body()

	/** Revokes one of the caller's own calendar subscriptions. */
	open suspend fun revoke(
		baseUrl: String,
		token: String,
		school: String? = null
	) {
		delete(proxyUrl(baseUrl, "/api/calendar/${token.encodeURLPathPart()}", school))
	}

	@Serializable
	private data class CalendarDaysRequest(val days: Int)
}
