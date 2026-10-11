package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyHomeworkDoneRequest
import com.deelabs.untisplus.api.model.proxy.ProxyHomeworkFlag
import com.deelabs.untisplus.api.model.proxy.ProxyHomeworkFlags
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

/**
 * Proxy homework "done" flags (`docs/PROXY-API.md` §3). The mark is per session
 * user and independent of WebUntis' own `completed` field.
 */
open class ProxyHomeworkApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = ApiClient.DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun flags(baseUrl: String, school: String? = null): ProxyHomeworkFlags =
		get(proxyUrl(baseUrl, "/api/homework/flags", school)).body()

	open suspend fun setDone(
		baseUrl: String,
		homeworkId: Long,
		done: Boolean,
		school: String? = null
	): ProxyHomeworkFlag =
		post(
			proxyUrl(baseUrl, "/api/homework/done", school),
			ProxyHomeworkDoneRequest(homeworkId, done)
		).body()
}
