package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyAbsenceNote
import com.deelabs.untisplus.api.model.proxy.ProxyAbsenceNoteRequest
import com.deelabs.untisplus.api.model.proxy.ProxyAbsenceNotes
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

/**
 * Private per-student absence notes (`docs/PROXY-API.md` §4), keyed by the
 * absence id. Clearing a note means sending an empty string, not omitting it.
 */
open class ProxyAbsenceApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = ApiClient.DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun notes(baseUrl: String, school: String? = null): ProxyAbsenceNotes =
		get(proxyUrl(baseUrl, "/api/absence/notes", school)).body()

	open suspend fun setNote(
		baseUrl: String,
		absenceKey: Long,
		note: String,
		school: String? = null
	): ProxyAbsenceNote =
		post(
			proxyUrl(baseUrl, "/api/absence/notes", school),
			ProxyAbsenceNoteRequest(absenceKey, note)
		).body()
}
