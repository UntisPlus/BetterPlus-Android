package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyCalendarApiTest {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

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
				"type": "class", "id": "5000", "name": "5a",
				"token": "abc123", "url": "https://api-untis.deelabs.tech/api/calendar/abc123.ics",
				"timezone": "Europe/Berlin", "classId": 5000
			}
			""".trimIndent()
		)

		assertEquals("abc123", response.token)
		assertEquals("https://api-untis.deelabs.tech/api/calendar/abc123.ics", response.url)
		assertEquals(5000L, response.classId)
		assertFalse(response.url.isBlank())
	}
}
