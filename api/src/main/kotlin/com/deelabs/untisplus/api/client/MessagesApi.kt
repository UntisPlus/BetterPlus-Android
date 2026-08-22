package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.exception.UntisApiException
import com.deelabs.untisplus.api.model.request.MessagesOfDayParams
import com.deelabs.untisplus.api.model.request.RequestData
import com.deelabs.untisplus.api.model.response.MessagesOfDayResponse
import com.deelabs.untisplus.api.model.response.MessagesOfDayResult
import com.deelabs.untisplus.api.model.untis.Auth
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json
import java.time.LocalDate

open class MessagesApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun getMessagesOfDay(
		apiUrl: String,
		date: LocalDate,
		user: String?,
		key: String?
	): MessagesOfDayResult {
		val body = RequestData(
			method = METHOD_GET_MESSAGES,
			params = listOf(
				MessagesOfDayParams(
					date = date,
					auth = Auth(user, key)
				)
			)
		)

		val response: MessagesOfDayResponse = request(apiUrl, body).body()

		return response.result ?: throw UntisApiException(response.error)
	}
}
