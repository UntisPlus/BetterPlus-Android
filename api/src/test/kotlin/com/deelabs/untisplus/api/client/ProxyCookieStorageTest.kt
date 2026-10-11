package com.deelabs.untisplus.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyCookieStorageTest {
	@Test
	fun `cookie set by login client is replayed by a separate proxy client`() = runBlocking {
		val storage = ProxyCookieStorage()

		val engine = MockEngine { request ->
			if (request.url.encodedPath == "/WebUntis/jsonrpc_intern.do") {
				respond(
					content = "{}",
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.SetCookie, "JSESSIONID=s3cr3t; Path=/")
				)
			} else {
				respond(
					content = request.headers[HttpHeaders.Cookie].orEmpty(),
					status = HttpStatusCode.OK
				)
			}
		}

		// Two distinct clients, the way the app uses a JSON-RPC login client and a
		// proxy REST client, sharing one cookie store.
		val loginClient = HttpClient(engine) { installCookieStorage(storage) }
		val proxyClient = HttpClient(engine) { installCookieStorage(storage) }

		loginClient.get("https://api-untis.deelabs.tech/WebUntis/jsonrpc_intern.do")

		val cookieHeader = proxyClient.get("https://api-untis.deelabs.tech/status").bodyAsText()

		assertEquals("JSESSIONID=s3cr3t", cookieHeader)
	}

	@Test
	fun `clear drops the stored session`() = runBlocking {
		val storage = ProxyCookieStorage()

		val engine = MockEngine { request ->
			if (request.url.encodedPath == "/WebUntis/jsonrpc_intern.do") {
				respond(
					content = "{}",
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.SetCookie, "JSESSIONID=s3cr3t; Path=/")
				)
			} else {
				respond(
					content = request.headers[HttpHeaders.Cookie].orEmpty(),
					status = HttpStatusCode.OK
				)
			}
		}

		val client = HttpClient(engine) { installCookieStorage(storage) }
		client.get("https://api-untis.deelabs.tech/WebUntis/jsonrpc_intern.do")

		storage.clear()

		val cookieHeader = client.get("https://api-untis.deelabs.tech/status").bodyAsText()

		assertEquals("", cookieHeader)
	}
}
