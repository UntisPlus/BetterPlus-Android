package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyCalendarApiTest {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

	private fun apiFor(
		handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
	) = ProxyCalendarApi(
		HttpClient(MockEngine(handler)) {
			install(ContentNegotiation) { json(json, contentType = ContentType.Application.Json) }
		}
	)

	@Test
	fun `proxy url appends school as query parameter`() {
		assertEquals(
			"https://api-untis.deelabs.tech/api/calendar/token?school=My%20School",
			proxyUrl("https://api-untis.deelabs.tech", "/api/calendar/token", "My School")
		)
	}

	@Test
	fun `proxy url omits school when blank`() {
		assertEquals(
			"https://api-untis.deelabs.tech/api/calendar/token",
			proxyUrl("https://api-untis.deelabs.tech", "/api/calendar/token", null)
		)
		assertEquals(
			"https://api-untis.deelabs.tech/api/calendar/token",
			proxyUrl("https://api-untis.deelabs.tech", "/api/calendar/token", "")
		)
	}

	@Test
	fun `class token request serializes classId`() {
		val body = json.encodeToString(
			ProxyCalendarTokenRequest.serializer(),
			ProxyCalendarTokenRequest(classId = 5000)
		)

		assertTrue(body.contains("\"classId\":5000"))
	}

	@Test
	fun `token response parses url and token`() {
		val response = json.decodeFromString(
			ProxyCalendarToken.serializer(),
			"""
			{
				"type": "class", "id": 5000, "name": "5a",
				"token": "abc123", "url": "https://api-untis.deelabs.tech/api/calendar/abc123.ics",
				"timezone": "Europe/Berlin", "days": 30, "horizon": "2025-11-01", "classId": 5000
			}
			""".trimIndent()
		)

		assertEquals("abc123", response.token)
		assertEquals("https://api-untis.deelabs.tech/api/calendar/abc123.ics", response.url)
		assertEquals(5000L, response.classId)
		assertEquals(5000L, response.id)
		assertEquals(30, response.days)
		assertEquals("2025-11-01", response.horizon)
		assertFalse(response.url.isBlank())
	}

	@Test
	fun `options response parses every target list`() {
		val options = json.decodeFromString(
			ProxyCalendarOptions.serializer(),
			"""
			{
				"school": "demo",
				"personal": {"personId": 5006, "name": "Alice"},
				"class": [{"id": 5000, "name": "5a"}, {"id": 5001, "name": "5b"}],
				"teacher": [{"id": 123, "name": "Mr. Teacher"}],
				"room": [{"id": 456, "name": "R1"}],
				"subject": [{"id": 789, "name": "Math"}],
				"technik": {"id": 2000000000, "name": "Technik"}
			}
			""".trimIndent()
		)

		assertEquals(5006L, options.personal?.personId)
		assertEquals(2, options.classes.size)
		assertEquals("5a", options.classes.first().name)
		assertEquals(1, options.teacher.size)
		assertEquals(456L, options.room.single().id)
		assertEquals(789L, options.subject.single().id)
		assertEquals(2000000000L, options.technik?.id)
	}

	@Test
	fun `options tolerate missing optional groups`() {
		val options = json.decodeFromString(
			ProxyCalendarOptions.serializer(),
			"""{"school":"s","class":[],"teacher":[],"room":[],"subject":[]}"""
		)

		assertNull(options.personal)
		assertNull(options.technik)
		assertTrue(options.classes.isEmpty())
	}

	@Test
	fun `updateDays issues a PATCH to the token`() = runBlocking {
		var method: HttpMethod? = null
		var path: String? = null

		val api = apiFor { request ->
			method = request.method
			path = request.url.encodedPath
			respond(
				content = """
					{"type":"class","id":5000,"token":"abc123",
					 "url":"https://api-untis.deelabs.tech/api/calendar/abc123.ics","days":90}
				""".trimIndent(),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val token = api.updateDays("https://api-untis.deelabs.tech", "abc123", 90)

		assertEquals(HttpMethod.Patch, method)
		assertEquals("/api/calendar/abc123", path)
		assertEquals(90, token.days)
	}

	@Test
	fun `tokens parses a list`() = runBlocking {
		val api = apiFor {
			respond(
				content = """
					[{"type":"student","personId":5006,"token":"t1",
					  "url":"https://api-untis.deelabs.tech/api/calendar/t1.ics"}]
				""".trimIndent(),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val tokens = api.tokens("https://api-untis.deelabs.tech")

		assertEquals(1, tokens.size)
		assertEquals("student", tokens.single().type)
		assertEquals(5006L, tokens.single().personId)
	}

	@Test
	fun `revoke issues a DELETE to the token`() = runBlocking {
		var method: HttpMethod? = null
		var path: String? = null

		val api = apiFor { request ->
			method = request.method
			path = request.url.encodedPath
			respond(content = "", status = HttpStatusCode.NoContent)
		}

		api.revoke("https://api-untis.deelabs.tech", "abc123")

		assertEquals(HttpMethod.Delete, method)
		assertEquals("/api/calendar/abc123", path)
	}

	@Test
	fun `options and tokens append the school query parameter`() = runBlocking {
		val paths = mutableListOf<String>()

		val api = apiFor { request ->
			paths += request.url.toString()
			respond(
				content = if (request.url.encodedPath.endsWith("options")) "{}" else "[]",
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		api.options("https://api-untis.deelabs.tech", "My School")
		api.tokens("https://api-untis.deelabs.tech", "My School")

		assertEquals(
			"https://api-untis.deelabs.tech/api/calendar/options?school=My%20School",
			paths.first()
		)
		assertEquals(
			"https://api-untis.deelabs.tech/api/calendar/tokens?school=My%20School",
			paths.last()
		)
	}

	@Test
	fun `token response without horizon still parses`() {
		val response = json.decodeFromString(
			ProxyCalendarToken.serializer(),
			"""{"token":"abc123","url":"https://x/api/calendar/abc123.ics","classId":5000}"""
		)

		assertNull(response.horizon)
		assertFalse(response.url.isBlank())
	}
}
