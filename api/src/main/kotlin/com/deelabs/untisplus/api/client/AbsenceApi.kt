package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.exception.UntisApiException
import com.deelabs.untisplus.api.model.request.RequestData
import com.deelabs.untisplus.api.model.request.StudentAbsencesParams
import com.deelabs.untisplus.api.model.response.StudentAbsencesResponse
import com.deelabs.untisplus.api.model.response.StudentAbsencesResult
import com.deelabs.untisplus.api.model.untis.Auth
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json
import java.time.LocalDate

open class AbsenceApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun getStudentAbsences(
		apiUrl: String,
		startDate: LocalDate,
		endDate: LocalDate,
		includeExcused: Boolean,
		includeUnExcused: Boolean,
		user: String?,
		key: String?
	): StudentAbsencesResult {
		val body = RequestData(
			method = METHOD_GET_ABSENCES,
			params = listOf(
				StudentAbsencesParams(
					startDate = startDate,
					endDate = endDate,
					includeExcused = includeExcused,
					includeUnExcused = includeUnExcused,
					auth = Auth(user, key)
				)
			)
		)

		val response: StudentAbsencesResponse = request(apiUrl, body).body()

		return response.result ?: throw UntisApiException(response.error)
	}
}
