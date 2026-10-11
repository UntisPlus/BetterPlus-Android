package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyNtfyRequest
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTarget
import com.deelabs.untisplus.api.model.proxy.ProxyWebhookRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySubscriptionsApiTest {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

	private fun apiFor(
		handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
	) = ProxySubscriptionsApi(
		HttpClient(MockEngine(handler)) {
			install(ContentNegotiation) { json(json, contentType = ContentType.Application.Json) }
		}
	)

	@Test
	fun `personal webhook request serializes personal flag and url`() {
		val body = json.encodeToString(
			ProxyWebhookRequest.serializer(),
			ProxyWebhookRequest(ProxySubscriptionTarget(personal = true), "https://hook.test/x", "s3cr3t")
		)

		assertTrue(body.contains("\"personal\":true"))
		assertTrue(body.contains("\"url\":\"https://hook.test/x\""))
		assertTrue(body.contains("\"secret\":\"s3cr3t\""))
	}

	@Test
	fun `class ntfy request serializes classId and topic`() {
		val body = json.encodeToString(
			ProxyNtfyRequest.serializer(),
			ProxyNtfyRequest(ProxySubscriptionTarget(classId = 5000), "my-topic")
		)

		assertTrue(body.contains("\"classId\":5000"))
		assertTrue(body.contains("\"topic\":\"my-topic\""))
	}

	@Test
	fun `notification options uses the notifications path`() = runBlocking {
		var path: String? = null

		val api = apiFor { request ->
			path = request.url.encodedPath
			respond(
				content = """{"school":"demo","class":[],"teacher":[],"room":[],"subject":[]}""",
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		api.notificationOptions("https://api-untis.deelabs.tech")

		assertEquals("/api/notifications/options", path)
	}

	@Test
	fun `ntfy topics list uses the ntfy path`() = runBlocking {
		var path: String? = null

		val api = apiFor { request ->
			path = request.url.encodedPath
			respond(
				content = """{"topics":[{"id":1,"topic":"t","elementType":"class","elementId":5000,"name":"5a"}]}""",
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val topics = api.ntfyTopics("https://api-untis.deelabs.tech")

		assertEquals("/api/ntfy", path)
		assertEquals(1, topics.topics.size)
		assertEquals("t", topics.topics.single().topic)
		assertEquals(5000L, topics.topics.single().elementId)
	}

	@Test
	fun `create webhook posts the request body to the webhooks path`() = runBlocking {
		var method: HttpMethod? = null
		var path: String? = null

		val api = apiFor { request ->
			method = request.method
			path = request.url.encodedPath
			respond(
				content = """{"id":42}""",
				status = HttpStatusCode.Created,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val created = api.createWebhook(
			"https://api-untis.deelabs.tech",
			ProxyWebhookRequest(ProxySubscriptionTarget(roomId = 456), "https://hook.test/x")
		)

		assertEquals(HttpMethod.Post, method)
		assertEquals("/api/webhooks", path)
		assertEquals(42L, created.id)
	}

	@Test
	fun `create ntfy posts the request body and parses the created id`() = runBlocking {
		var method: HttpMethod? = null
		var path: String? = null

		val api = apiFor { request ->
			method = request.method
			path = request.url.encodedPath
			respond(
				content = """{"id":7}""",
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val created = api.createNtfy(
			"https://api-untis.deelabs.tech",
			ProxyNtfyRequest(ProxySubscriptionTarget(teacherId = 123), "topic", "https://ntfy.sh")
		)

		assertEquals(HttpMethod.Post, method)
		assertEquals("/api/ntfy", path)
		assertEquals(7L, created.id)
	}

	@Test
	fun `test and delete target the subscription id`() = runBlocking {
		val paths = mutableListOf<String>()

		val api = apiFor { request ->
			paths += "${request.method.value} ${request.url.encodedPath}"
			respond(
				content = if (request.method == HttpMethod.Post) """{"ok":true}""" else """{"deleted":true}""",
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		api.testNtfy("https://api-untis.deelabs.tech", 42)
		api.deleteNtfy("https://api-untis.deelabs.tech", 42)

		assertEquals("POST /api/ntfy/42/test", paths.first())
		assertEquals("DELETE /api/ntfy/42", paths.last())
	}

	@Test
	fun `routes append the school query parameter`() = runBlocking {
		val urls = mutableListOf<String>()

		val api = apiFor { request ->
			urls += request.url.toString()
			respond(
				content = """{"webhooks":[]}""",
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		api.webhooks("https://api-untis.deelabs.tech", "My School")

		assertEquals(
			"https://api-untis.deelabs.tech/api/webhooks?school=My%20School",
			urls.single()
		)
	}
}
