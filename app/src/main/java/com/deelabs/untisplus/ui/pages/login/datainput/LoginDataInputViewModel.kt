package com.deelabs.untisplus.ui.pages.login.datainput

import android.util.Log
import android.util.Patterns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.deelabs.untisplus.BuildConfig
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.client.SchoolSearchApi
import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.api.exception.UntisApiException
import com.deelabs.untisplus.api.model.response.UntisErrorCode
import com.deelabs.untisplus.api.model.response.UserDataResult
import com.deelabs.untisplus.api.model.untis.MasterData
import com.deelabs.untisplus.api.model.untis.SchoolInfo
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.data.cache.ApiCacheCleaner
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.User.Companion.buildApiUrl
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.services.WeekLogicService
import com.deelabs.untisplus.helpers.ErrorMessageDictionary
import com.deelabs.untisplus.helpers.SerializationUtils.getJSON
import com.deelabs.untisplus.services.CodeScanService
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.pages.ActivityViewModel
import com.deelabs.untisplus.ui.pages.login.sso.SsoLoginResultBus
import com.deelabs.untisplus.ui.pages.login.sso.SsoProvider
import crocodile8.universal_cache.FromCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named

const val DEMO_API_URL = "https://api.sapuseven.com/untis/testing"

enum class ApiProvider {
	OFFICIAL,
	UNTISPLUS,
	CUSTOM
}

@HiltViewModel
class LoginDataInputViewModel @Inject constructor(
	private val userDao: UserDao,
	private val schoolSearchApi: SchoolSearchApi,
	private val userDataApi: UserDataApi,
	private val timetableRepository: TimetableRepository,
	private val weekLogicService: WeekLogicService,
	private val navigator: AppNavigator,
	private val userRepository: UserRepository,
	private val codeScanService: CodeScanService,
	@Named("cacheDir") private val cacheDir: File,
	private val savedStateHandle: SavedStateHandle
) : ActivityViewModel() {
	private val args = savedStateHandle.toRoute<AppRoutes.LoginDataInput>()

	private val existingUserId = if (args.userId == -1L) null else args.userId

	private var existingUser: User? = null

	val isExistingUser = existingUserId != null

	val useStoredPassword
		get() = isExistingUser && loginData.password.value.isNullOrEmpty() && loginData.storedPassword != null

	val loginData = LoginData()

	var apiProvider by mutableStateOf(ApiProvider.UNTISPLUS)

	var advanced by mutableStateOf(false)

	var searchMode by mutableStateOf(false)

	var validate by mutableStateOf(false)
		private set

	var loading by mutableStateOf(false)
		private set

	var errorText: Int? by mutableStateOf(null)
		private set

	var errorTextRaw: String? by mutableStateOf(null)
		private set

	var showQrCodeErrorDialog by mutableStateOf(false)
		private set

	var showSecondFactorInput by mutableStateOf(false)
		private set

	val showProfileUpdate = args.profileUpdate

	var schoolIdLocked by mutableStateOf(false)

	var anonymousSupported by mutableStateOf<Boolean?>(null)
		private set

	private var anonymousSupportJob: Job? = null

	val schoolNameValid = derivedStateOf {
		loginData.schoolName.value?.isNotEmpty() ?: false
	}

	val usernameValid = derivedStateOf {
		loginData.username.value?.isNotEmpty() ?: false || (loginData.anonymous.value == true)
	}

	val apiUrlValid = derivedStateOf {
		loginData.apiUrl.value?.let {
			it.isEmpty() || Patterns.WEB_URL.matcher(it).matches()
		} ?: true
	}

	val customEndpointValid: Boolean
		get() = apiProvider != ApiProvider.CUSTOM ||
			User.normalizeApiEndpoint(loginData.apiUrl.value.orEmpty()) != null

	val isHttpCustomEndpoint: Boolean
		get() = apiProvider == ApiProvider.CUSTOM &&
			User.normalizeApiEndpoint(loginData.apiUrl.value.orEmpty())?.first == "http"

	private val schoolInfoFromSearch = args.schoolInfoSerialized?.let {
		getJSON().decodeFromString<SchoolInfo>(it)
	}

	/** SSO identity provider the school advertises, once detected from its web page. */
	var ssoProvider by mutableStateOf<SsoProvider?>(null)
		private set

	/** SSO login requires the resolved school server and a provider enabled at build time. */
	val ssoAvailable: Boolean
		get() = schoolInfoFromSearch != null && loginData.anonymous.value != true &&
			ssoProvider != null && ssoProvider!!.type != SsoProvider.Type.UNKNOWN &&
			BuildConfig.SSO_ENABLED_PROVIDERS.contains(ssoProvider!!.type.name)

	fun onSsoLoginClick() {
		val serialized = args.schoolInfoSerialized ?: return
		navigator.navigate(AppRoutes.LoginSso(schoolInfoSerialized = serialized))
	}

	val codeScanResultHandler: (String?) -> Unit = {
		try {
			it?.let { loadFromData(it) }
		} catch (_: Exception) {
			showQrCodeErrorDialog = true
		}
	}

	init {
		viewModelScope.launch(Dispatchers.IO) {
			existingUserId?.let { userDao.getById(it) }?.let {
				existingUser = it
				loginData.loadFromUser(it)
				apiProvider = when {
					it.customApiHost?.isNotBlank() == true -> ApiProvider.CUSTOM
					it.useUntisPlusApi -> ApiProvider.UNTISPLUS
					else -> ApiProvider.OFFICIAL
				}
				advanced = apiProvider != ApiProvider.UNTISPLUS
			}
		}

		if (args.demoLogin) {
			loginData.anonymous.value = true
			loginData.schoolName.value = "demo"
			apiProvider = ApiProvider.CUSTOM
			advanced = true
			loginData.apiUrl.value = DEMO_API_URL

			loadData()
		}

		schoolInfoFromSearch?.let {
			loginData.schoolName.value = schoolInfoFromSearch.loginName
			schoolIdLocked = true
			checkAnonymousSupport(it)
			checkSsoProvider(it)
		}

		if (showProfileUpdate) {
			loadData()
		}

		codeScanResultHandler(args.autoLoginData)

		// A completed SSO login hands over username + app shared secret; log in with them directly.
		viewModelScope.launch {
			SsoLoginResultBus.result.collect { result ->
				result ?: return@collect
				SsoLoginResultBus.consume()

				loginData.username.value = result.username
				loginData.password.value = result.key
				loginData.skipAppSecret.value = true

				if (schoolNameValid.value && usernameValid.value) {
					errorText = null
					errorTextRaw = null
					loadData()
				}
			}
		}
	}

	fun setCodeScanLauncher(launcher: ManagedActivityResultLauncher<ScanOptions, ScanIntentResult>) {
		codeScanService.setLauncher(launcher)
	}

	fun onLoginClick() {
		validate = true
		if (schoolNameValid.value && usernameValid.value && apiUrlValid.value && customEndpointValid) {
			errorText = null
			errorTextRaw = null
			loadData()
		}
	}

	private fun loadFromData(data: String?) {
		if (data == null) return
		val appLinkData = data.toUri()

		if (appLinkData.isHierarchical && appLinkData.scheme == "untis" && appLinkData.host == "setschool") {
			// Untis-native values
			loginData.schoolName.value = appLinkData.getQueryParameter("school")
			loginData.username.value = appLinkData.getQueryParameter("user")
			loginData.password.value = appLinkData.getQueryParameter("key")

			// Custom values
			loginData.anonymous.value = appLinkData.getBooleanQueryParameter("anonymous", false)
			loginData.apiUrl.value = appLinkData.getQueryParameter("apiUrl")?.takeIf { it.isNotEmpty() }
			loginData.skipAppSecret.value = appLinkData.getBooleanQueryParameter("skipAppSecret", false)

			if (loginData.apiUrl.value?.isNotEmpty() == true) {
				apiProvider = ApiProvider.CUSTOM
				advanced = true
			}

			loadData()
		} else {
			showQrCodeErrorDialog = true
		}
	}

	private fun loadData() = viewModelScope.launch {
		loading = true

		try {
			val schoolInfo = loadSchoolInfo() ?: run {
				errorText = R.string.logindatainput_error_invalid_school
				return@launch
			}

			val apiHost = resolveEndpointOrigin().orEmpty()
			val untisApiUrl = User.buildJsonRpcApiUrl(
				buildApiUrl(apiHost, schoolInfo),
				schoolInfo.loginName
			).toString()

			val endpointChanged = existingUser?.let { it.jsonRpcApiUrl.toString() != untisApiUrl } == true

			val appSharedSecret = loadAppSharedSecret(untisApiUrl)

			val userData = loadUserData(untisApiUrl, appSharedSecret)
			val user = buildUser(apiHost, appSharedSecret, schoolInfo, userData)

			withContext(Dispatchers.IO) {
				if (endpointChanged) ApiCacheCleaner.clearApiCaches(cacheDir)
				val userId = saveUser(user, userData.masterData)
				userRepository.switchUser(userId)
				preloadTimetable()
				navigator.navigate(AppRoutes.Timetable()) {
					popUpTo(0) // Pop all previous routes
				}
			}
		} catch (e: UntisApiException) {
			Log.e(LoginDataInputViewModel::class.simpleName, "loadData Untis error", e)

			val errorTextRes = ErrorMessageDictionary.getErrorMessageResource(e.error?.code, false)
			errorText = errorTextRes ?: R.string.errormessagedictionary_generic
			if (e.error?.code == UntisErrorCode.REQUIRE2_FACTOR_AUTHENTICATION_TOKEN) {
				showSecondFactorInput = true
			} else {
				errorTextRaw = when (e.error?.code) {
					else -> if (errorTextRes == null) e.error?.message else null
				}
			}
		} catch (e: Exception) {
			Log.e(LoginDataInputViewModel::class.simpleName, "loadData error", e)
			errorText = R.string.errormessagedictionary_generic
			errorTextRaw = e.message
		} finally {
			loading = false
		}
	}

	/**
	 * Returns the API origin (scheme + authority) to use for the selected provider,
	 * or null if the official school server should be used.
	 */
	private fun resolveEndpointOrigin(): String? {
		return when (apiProvider) {
			ApiProvider.OFFICIAL -> null
			ApiProvider.UNTISPLUS -> "https://${User.UNTISPLUS_API_HOST}"
			ApiProvider.CUSTOM -> User.normalizeApiEndpoint(loginData.apiUrl.value.orEmpty())
				?.let { (scheme, authority) -> "$scheme://$authority" }
		}
	}

	private fun buildUser(
		apiHost: String,
		appSharedSecret: String,
		schoolInfo: SchoolInfo,
		userData: UserDataResult
	): User {
		val user = User(
			existingUserId ?: 0,
			loginData.profileName.value.orEmpty(),
			apiHost,
			schoolInfo,
			null,
			if (loginData.anonymous.value != true) loginData.username.value else null,
			if (loginData.anonymous.value != true) appSharedSecret else null,
			loginData.anonymous.value == true,
			apiProvider == ApiProvider.UNTISPLUS,
			if (apiProvider == ApiProvider.CUSTOM) apiHost.takeIf { it.isNotEmpty() } else null,
			userData.masterData.timeGrid ?: TimeGrid.generateDefault(),
			userData.masterData.timeStamp,
			userData.userData,
			userData.settings,
		)
		return user
	}

	private fun preloadTimetable() {
		viewModelScope.launch {
			try {
				val user = userRepository.currentUser!!
				val element = user.userData.getPeriodElement()
					?: user.userData.getFallbackPeriodElements().firstOrNull() ?: return@launch
				val startDate = weekLogicService.currentWeekStartDate()
				timetableRepository.timetableSource().get(
					params = TimetableRepository.TimetableParams(
						elementId = element.id,
						elementType = element.type,
						startDate = startDate,
					),
					fromCache = FromCache.CACHED_THEN_LOAD,
					additionalKey = user.id
				).collect {}
			} catch (e: Exception) {
				Log.w(LoginDataInputViewModel::class.simpleName, "Timetable preload failed", e)
			}
		}
	}

	private suspend fun saveUser(user: User, masterData: MasterData): Long {
		val userId = existingUserId?.also {
			userDao.update(user)
		} ?: run {
			userDao.insert(user)
		}

		userDao.deleteUserData(userId)
		userDao.insertMasterData(userId, masterData)
		return userId
	}

	private suspend fun loadSchoolInfo(): SchoolInfo? {
		return schoolInfoFromSearch ?: run {
			if (apiProvider == ApiProvider.CUSTOM && !loginData.apiUrl.value.isNullOrBlank()) SchoolInfo(
				server = "",
				useMobileServiceUrlAndroid = true,
				useMobileServiceUrlIos = true,
				address = "",
				displayName = loginData.schoolName.value.orEmpty(),
				loginName = loginData.schoolName.value.orEmpty(),
				schoolId = loginData.schoolName.value?.toLongOrNull() ?: 0,
				serverUrl = loginData.apiUrl.value.orEmpty(),
				mobileServiceUrl = loginData.apiUrl.value
			)
			else {
				val school = loginData.schoolName.value.orEmpty()
				val schoolId = school.toLongOrNull()

				val schoolSearchResult = schoolId?.let {
					schoolSearchApi.searchSchools(schoolId = it)
				} ?: schoolSearchApi.searchSchools(schoolName = school)

				if (schoolSearchResult.size == 1) schoolSearchResult.schools.first()
				else
					// Usually, there is only one result, but if there are multiple, we try to find the one that matches the loginName or schoolId
					schoolSearchResult.schools.find { schoolInfoResult ->
						schoolInfoResult.schoolId == schoolId || schoolInfoResult.loginName.equals(
							school,
							true
						)
					}
			}
		}
	}

	/**
	 * This method tries to get the app secret from the supplied password.
	 * If the call fails, the password is assumed to be the app secret already and is returned directly.
	 */
	private suspend fun loadAppSharedSecret(untisApiUrl: String): String {
		if (loginData.anonymous.value == true) return ""

		// Credentials already come as username + key (SSO login / QR with skip flag).
		if (loginData.skipAppSecret.value == true) return loginData.password.value.orEmpty()

		if (useStoredPassword) return loginData.storedPassword!!

		return try {
			userDataApi.getAppSharedSecret(
				untisApiUrl,
				loginData.username.value.orEmpty(),
				loginData.password.value.orEmpty(),
				loginData.secondFactor.value
			)
		} catch (e: UntisApiException) {
			// Throw certain errors, ignore others
			if (e.error?.code == UntisErrorCode.REQUIRE2_FACTOR_AUTHENTICATION_TOKEN) throw e
			else loginData.password.value.orEmpty()
		}
	}

	private suspend fun loadUserData(untisApiUrl: String, appSharedSecret: String): UserDataResult {
		val user = if (loginData.anonymous.value == true) null else loginData.username.value
		return userDataApi.getUserData(untisApiUrl, user, appSharedSecret)
	}

	fun goBack() {
		if (searchMode) searchMode = false
		else navigator.popBackStack()
	}

	fun onQrCodeErrorDialogDismiss() {
		showQrCodeErrorDialog = false
	}

	fun onCodeScanClick() {
		codeScanService.scanCode(codeScanResultHandler)
	}

	fun selectSchool(it: SchoolInfo) {
		loginData.schoolName.value = it.loginName
		searchMode = false
		checkAnonymousSupport(it)
	}

	private fun checkAnonymousSupport(schoolInfo: SchoolInfo?) {
		if (schoolInfo == null) return
		anonymousSupportJob?.cancel()
		anonymousSupported = null
		anonymousSupportJob = viewModelScope.launch(Dispatchers.IO) {
			anonymousSupported = try {
				val apiHost = resolveEndpointOrigin().orEmpty()
				val untisApiUrl = User.buildJsonRpcApiUrl(
					buildApiUrl(apiHost, schoolInfo),
					schoolInfo.loginName
				).toString()
				userDataApi.getUserData(untisApiUrl, null, "")
				true
			} catch (_: Throwable) {
				false
			}
		}
	}

	private fun checkSsoProvider(schoolInfo: SchoolInfo?) {
		val info = schoolInfo ?: run { ssoProvider = null; return }
		val advertised = if (info.useMobileServiceUrlAndroid == true && !info.mobileServiceUrl.isNullOrBlank())
			info.mobileServiceUrl
		else
			info.serverUrl
		if (advertised.isNullOrBlank() || !advertised.contains("://")) return

		viewModelScope.launch(Dispatchers.IO) {
			val startUrl = if (advertised.toUri().getQueryParameter("school").isNullOrBlank())
				advertised.toUri().buildUpon()
					.appendQueryParameter("school", info.loginName.orEmpty())
					.toString()
			else advertised

			ssoProvider = try {
				val connection = URL(startUrl).openConnection() as HttpURLConnection
				try {
					connection.connectTimeout = 10_000
					connection.readTimeout = 10_000
					connection.instanceFollowRedirects = true
					connection.setRequestProperty("Accept", "text/html,application/json,*/*")
					connection.setRequestProperty(
						"User-Agent",
						"Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 UntisPlus"
					)
					val code = connection.responseCode
					val stream = if (code in 200..299) connection.inputStream else connection.errorStream
					val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
					SsoProvider.fromConfig(body)
				} finally {
					connection.disconnect()
				}
			} catch (_: Throwable) {
				null
			}
		}
	}

	class LoginData(
		initialProfileName: String? = null,
		initialSchoolName: String? = null,
		initialAnonymous: Boolean? = null,
		initialUsername: String? = null,
		initialApiUrl: String? = null,
	) {
		val profileName = mutableStateOf(initialProfileName)
		val schoolName = mutableStateOf(initialSchoolName)
		val anonymous = mutableStateOf(initialAnonymous)
		val username = mutableStateOf(initialUsername)
		val password = mutableStateOf<String?>(null)
		val secondFactor = mutableStateOf<String?>(null)
		val apiUrl = mutableStateOf(initialApiUrl)
		val skipAppSecret = mutableStateOf<Boolean?>(null)
		var storedPassword: String? = null

		fun loadFromUser(user: User) {
			profileName.value = user.profileName
			schoolName.value = user.schoolId ?: user.schoolInfo?.loginName
			anonymous.value = user.anonymous
			username.value = user.user
			apiUrl.value = user.apiHost
			storedPassword = user.key
		}
	}
}
