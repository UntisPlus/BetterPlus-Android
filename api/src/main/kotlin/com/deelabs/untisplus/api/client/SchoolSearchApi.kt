package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.exception.UntisApiException
import com.deelabs.untisplus.api.model.request.RequestData
import com.deelabs.untisplus.api.model.request.SchoolSearchParams
import com.deelabs.untisplus.api.model.response.SchoolSearchResponse
import com.deelabs.untisplus.api.model.response.SchoolSearchResult
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

open class SchoolSearchApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = ApiClient.DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	@OptIn(ExperimentalSerializationApi::class)
	open suspend fun searchSchools(
		search: String? = null,
		schoolId: Long = 0,
		schoolName: String = ""
	): SchoolSearchResult {
		val body = RequestData(
			method = ApiClient.METHOD_SEARCH_SCHOOLS,
			params = listOf(SchoolSearchParams(search, schoolId, schoolName))
		)

		val response: SchoolSearchResponse = request(DEFAULT_SCHOOLSEARCH_URL, body).body()

		return response.result ?: throw UntisApiException(response.error)
	}
}
