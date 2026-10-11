package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyCreated
import com.deelabs.untisplus.api.model.proxy.ProxyDeleted
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyRequest
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyTopics
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTest
import com.deelabs.untisplus.api.model.proxy.ProxyWebhookRequest
import com.deelabs.untisplus.api.model.proxy.ProxyWebhooks
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

/**
 * Webhook and ntfy push subscriptions (`docs/PROXY-API.md` §5). A subscription
 * targets one element the caller may watch (their own class, their personal
 * timetable, or — with recon/boosted/staff — a teacher/room/subject); creating
 * is refused for anything else.
 */
open class ProxySubscriptionsApi : ApiClient {
	constructor(
		httpClientEngineFactory: HttpClientEngineFactory<*>? = null,
		httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
		jsonBlock: Json = ApiClient.DEFAULT_JSON
	) : super(httpClientEngineFactory, httpClientConfig, jsonBlock)

	/** Builds on an already-configured client (used by tests). */
	constructor(httpClient: HttpClient) : super(httpClient)

	/**
	 * Lists the timetable targets the caller may watch — the calendar catalog
	 * minus the synthetic Technik aggregate (the change poller cannot deliver
	 * it).
	 */
	open suspend fun notificationOptions(baseUrl: String, school: String? = null): ProxyCalendarOptions =
		get(proxyUrl(baseUrl, "/api/notifications/options", school)).body()

	open suspend fun webhooks(baseUrl: String, school: String? = null): ProxyWebhooks =
		get(proxyUrl(baseUrl, "/api/webhooks", school)).body()

	open suspend fun createWebhook(
		baseUrl: String,
		request: ProxyWebhookRequest,
		school: String? = null
	): ProxyCreated = post(proxyUrl(baseUrl, "/api/webhooks", school), request).body()

	open suspend fun testWebhook(baseUrl: String, id: Long, school: String? = null): ProxySubscriptionTest =
		post(proxyUrl(baseUrl, "/api/webhooks/$id/test", school), null).body()

	open suspend fun deleteWebhook(baseUrl: String, id: Long, school: String? = null): ProxyDeleted =
		delete(proxyUrl(baseUrl, "/api/webhooks/$id", school)).body()

	open suspend fun ntfyTopics(baseUrl: String, school: String? = null): ProxyNtfyTopics =
		get(proxyUrl(baseUrl, "/api/ntfy", school)).body()

	open suspend fun createNtfy(
		baseUrl: String,
		request: ProxyNtfyRequest,
		school: String? = null
	): ProxyCreated = post(proxyUrl(baseUrl, "/api/ntfy", school), request).body()

	open suspend fun testNtfy(baseUrl: String, id: Long, school: String? = null): ProxySubscriptionTest =
		post(proxyUrl(baseUrl, "/api/ntfy/$id/test", school), null).body()

	open suspend fun deleteNtfy(baseUrl: String, id: Long, school: String? = null): ProxyDeleted =
		delete(proxyUrl(baseUrl, "/api/ntfy/$id", school)).body()
}