package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyCreated
import com.deelabs.untisplus.api.model.proxy.ProxyDeleted
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyRequest
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyTopics
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTest
import com.deelabs.untisplus.api.model.proxy.ProxyWebhookRequest
import com.deelabs.untisplus.api.model.proxy.ProxyWebhooks
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

/**
 * Webhook and ntfy push subscriptions (`docs/PROXY-API.md` §5). Creating a
 * subscription is gated on the `boosted` proxy tier.
 */
open class ProxySubscriptionsApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = ApiClient.DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun webhooks(baseUrl: String, school: String? = null): ProxyWebhooks =
		get(proxyUrl(baseUrl, "/api/webhooks", school)).body()

	open suspend fun createWebhook(
		baseUrl: String,
		classId: Long,
		url: String,
		school: String? = null
	): ProxyCreated = post(proxyUrl(baseUrl, "/api/webhooks", school), ProxyWebhookRequest(classId, url)).body()

	open suspend fun testWebhook(baseUrl: String, id: Long, school: String? = null): ProxySubscriptionTest =
		post(proxyUrl(baseUrl, "/api/webhooks/$id/test", school), null).body()

	open suspend fun deleteWebhook(baseUrl: String, id: Long, school: String? = null): ProxyDeleted =
		delete(proxyUrl(baseUrl, "/api/webhooks/$id", school)).body()

	open suspend fun ntfyTopics(baseUrl: String, school: String? = null): ProxyNtfyTopics =
		get(proxyUrl(baseUrl, "/api/ntfy/topics", school)).body()

	open suspend fun createNtfy(
		baseUrl: String,
		classId: Long,
		topic: String,
		baseUrlTopic: String? = null,
		school: String? = null
	): ProxyCreated =
		post(proxyUrl(baseUrl, "/api/ntfy/topics", school), ProxyNtfyRequest(classId, topic, baseUrl)).body()

	open suspend fun testNtfy(baseUrl: String, id: Long, school: String? = null): ProxySubscriptionTest =
		post(proxyUrl(baseUrl, "/api/ntfy/topics/$id/test", school), null).body()

	open suspend fun deleteNtfy(baseUrl: String, id: Long, school: String? = null): ProxyDeleted =
		delete(proxyUrl(baseUrl, "/api/ntfy/topics/$id", school)).body()
}
