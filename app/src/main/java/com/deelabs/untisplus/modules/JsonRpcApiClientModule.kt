package com.deelabs.untisplus.modules

import com.deelabs.untisplus.api.client.AbsenceApi
import com.deelabs.untisplus.api.client.ClassRegApi
import com.deelabs.untisplus.api.client.MessagesApi
import com.deelabs.untisplus.api.client.OfficeHoursApi
import com.deelabs.untisplus.api.client.SchoolSearchApi
import com.deelabs.untisplus.api.client.TimetableApi
import com.deelabs.untisplus.api.client.UserDataApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JsonRpcApiClientModule {
	@Provides
	@Singleton
	fun provideHttpClientEngineFactory(): HttpClientEngineFactory<*> = CIO

	@Provides
	@Singleton
	@Named("json")
	fun provideJsonHttpClient(httpClientEngineFactory: HttpClientEngineFactory<*>): HttpClient =
		HttpClient(httpClientEngineFactory.create()) {
			install(ContentNegotiation) {
				json()
			}
			install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 3)
				retryOnException(maxRetries = 3)
				exponentialDelay()
			}
			expectSuccess = true
		}

	@Provides
	@Singleton
	fun provideSchoolSearchApi(engineFactory: HttpClientEngineFactory<*>): SchoolSearchApi =
		SchoolSearchApi(engineFactory)

	@Provides
	@Singleton
	fun provideUserDataApi(engineFactory: HttpClientEngineFactory<*>): UserDataApi = UserDataApi(engineFactory)

	@Provides
	@Singleton
	fun provideTimetableApi(engineFactory: HttpClientEngineFactory<*>): TimetableApi = TimetableApi(engineFactory)

	@Provides
	@Singleton
	fun provideMessagesApi(engineFactory: HttpClientEngineFactory<*>): MessagesApi = MessagesApi(engineFactory)

	@Provides
	@Singleton
	fun provideClassRegApi(engineFactory: HttpClientEngineFactory<*>): ClassRegApi = ClassRegApi(engineFactory)

	@Provides
	@Singleton
	fun provideAbsenceApi(engineFactory: HttpClientEngineFactory<*>): AbsenceApi = AbsenceApi(engineFactory)

	@Provides
	@Singleton
	fun provideOfficeHoursApi(engineFactory: HttpClientEngineFactory<*>): OfficeHoursApi = OfficeHoursApi(engineFactory)
}
