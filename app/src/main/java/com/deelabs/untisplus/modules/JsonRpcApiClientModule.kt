package com.deelabs.untisplus.modules

import com.deelabs.untisplus.api.client.AbsenceApi
import com.deelabs.untisplus.api.client.ClassRegApi
import com.deelabs.untisplus.api.client.MessagesApi
import com.deelabs.untisplus.api.client.OfficeHoursApi
import com.deelabs.untisplus.api.client.ProxyAbsenceApi
import com.deelabs.untisplus.api.client.ProxyCalendarApi
import com.deelabs.untisplus.api.client.ProxyCookieStorage
import com.deelabs.untisplus.api.client.ProxyHomeworkApi
import com.deelabs.untisplus.api.client.ProxyStatusApi
import com.deelabs.untisplus.api.client.ProxySubscriptionsApi
import com.deelabs.untisplus.api.client.ProxyTimetableApi
import com.deelabs.untisplus.api.client.SchoolSearchApi
import com.deelabs.untisplus.api.client.TimetableApi
import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.api.client.installCookieStorage
import com.deelabs.untisplus.api.client.installSse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JsonRpcApiClientModule {
	@Provides
	@Singleton
	fun provideHttpClientEngineFactory(): HttpClientEngineFactory<*> = CIO

	/**
	 * Process-wide cookie store. The proxy sets `JSESSIONID` during the intern
	 * login (`getUserData2017`); every client that talks to the proxy shares this
	 * instance so the session rides along on later `/api/…` calls.
	 */
	@Provides
	@Singleton
	fun provideCookieStorage(): ProxyCookieStorage = ProxyCookieStorage()

	@Provides
	@Singleton
	fun provideCookiesStorage(cookieStorage: ProxyCookieStorage): CookiesStorage = cookieStorage

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

	/**
	 * Plain client for fetching external `.ics` files. Responses are read as raw
	 * text, so no content negotiation is installed, and `expectSuccess` stays off
	 * so conditional `304 Not Modified` responses can be handled by the caller.
	 */
	@Provides
	@Singleton
	@Named("ics")
	fun provideIcsHttpClient(httpClientEngineFactory: HttpClientEngineFactory<*>): HttpClient =
		HttpClient(httpClientEngineFactory.create()) {
			install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 2)
				retryOnException(maxRetries = 2)
				exponentialDelay()
			}
			expectSuccess = false
		}

	@Provides
	@Singleton
	fun provideSchoolSearchApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): SchoolSearchApi = SchoolSearchApi(engineFactory, { it.installCookieStorage(cookiesStorage) })

	@Provides
	@Singleton
	fun provideUserDataApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): UserDataApi = UserDataApi(engineFactory, { it.installCookieStorage(cookiesStorage) })

	@Provides
	@Singleton
	fun provideTimetableApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): TimetableApi = TimetableApi(engineFactory, { it.installCookieStorage(cookiesStorage) })

	@Provides
	@Singleton
	fun provideMessagesApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): MessagesApi = MessagesApi(engineFactory, { it.installCookieStorage(cookiesStorage) })

	@Provides
	@Singleton
	fun provideClassRegApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): ClassRegApi = ClassRegApi(engineFactory, { it.installCookieStorage(cookiesStorage) })

	@Provides
	@Singleton
	fun provideAbsenceApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): AbsenceApi = AbsenceApi(engineFactory, { it.installCookieStorage(cookiesStorage) })

	@Provides
	@Singleton
	fun provideOfficeHoursApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): OfficeHoursApi = OfficeHoursApi(engineFactory, { it.installCookieStorage(cookiesStorage) })

	@Provides
	@Singleton
	fun provideProxyStatusApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): ProxyStatusApi = ProxyStatusApi(
		engineFactory,
		{
			it.installCookieStorage(cookiesStorage)
			it.expectSuccess = true
		}
	)

	@Provides
	@Singleton
	fun provideProxyHomeworkApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): ProxyHomeworkApi = ProxyHomeworkApi(
		engineFactory,
		{
			it.installCookieStorage(cookiesStorage)
			it.expectSuccess = true
		}
	)

	@Provides
	@Singleton
	fun provideProxyAbsenceApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): ProxyAbsenceApi = ProxyAbsenceApi(
		engineFactory,
		{
			it.installCookieStorage(cookiesStorage)
			it.expectSuccess = true
		}
	)

	@Provides
	@Singleton
	fun provideProxyCalendarApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): ProxyCalendarApi = ProxyCalendarApi(
		engineFactory,
		{
			it.installCookieStorage(cookiesStorage)
			it.expectSuccess = true
		}
	)

	@Provides
	@Singleton
	fun provideProxySubscriptionsApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): ProxySubscriptionsApi = ProxySubscriptionsApi(
		engineFactory,
		{
			it.installCookieStorage(cookiesStorage)
			it.expectSuccess = true
		}
	)

	@Provides
	@Singleton
	fun provideProxyTimetableApi(
		engineFactory: HttpClientEngineFactory<*>,
		cookiesStorage: CookiesStorage
	): ProxyTimetableApi = ProxyTimetableApi(
		engineFactory,
		{
			it.installCookieStorage(cookiesStorage)
			it.installSse()
			it.expectSuccess = true
		}
	)
}
