package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.exception.UntisApiException
import com.deelabs.untisplus.api.model.request.AppSharedSecretParams
import com.deelabs.untisplus.api.model.request.AuthTokenParams
import com.deelabs.untisplus.api.model.request.RequestData
import com.deelabs.untisplus.api.model.request.UserDataParams
import com.deelabs.untisplus.api.model.response.AppSharedSecretResponse
import com.deelabs.untisplus.api.model.response.AuthTokenResponse
import com.deelabs.untisplus.api.model.response.UserDataResponse
import com.deelabs.untisplus.api.model.response.UserDataResult
import com.deelabs.untisplus.api.model.untis.Auth
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

open class UserDataApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	jsonBlock: Json = DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	open suspend fun getAppSharedSecret(
		apiUrl: String,
		user: String,
		password: String,
		token: String? = null
	): String {
		val body = RequestData(
			method = METHOD_GET_APP_SHARED_SECRET,
			params = listOf(AppSharedSecretParams(user, password, token))
		)

		val response: AppSharedSecretResponse = request(apiUrl, body).body()

		return response.result ?: throw UntisApiException(response.error)
	}

	open suspend fun getAuthToken(
		apiUrl: String,
		user: String?,
		key: String?
	): String {
		val body = RequestData(
			method = METHOD_GET_AUTH_TOKEN,
			params = listOf(AuthTokenParams(auth = Auth(user, key)))
		)

		val response: AuthTokenResponse = request(apiUrl, body).body()

		return response.result?.token ?: throw UntisApiException(response.error)
	}

	open suspend fun getUserData(
		apiUrl: String,
		user: String?,
		key: String?
	): UserDataResult {
		val body = RequestData(
			method = METHOD_GET_USER_DATA,
			params = listOf(UserDataParams(auth = Auth(user, key)))
		)

		val response: UserDataResponse = request(apiUrl, body).body()

		return response.result ?: throw UntisApiException(response.error)
	}
}
