package com.deelabs.untisplus.ui.pages.login.sso

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.SchoolInfo
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.helpers.SerializationUtils.getJSON
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class SsoLoginViewModel @Inject constructor(
	private val navigator: AppNavigator,
	savedStateHandle: SavedStateHandle
) : ViewModel() {
	companion object {
		const val TAG = "SsoLogin"

		private const val HARVEST_THROTTLE_MS = 2500L
	}

	private val args = savedStateHandle.toRoute<AppRoutes.LoginSso>()

	val schoolInfo: SchoolInfo? = getJSON().decodeFromString<SchoolInfo?>(args.schoolInfoSerialized)

	/** The official school server root, e.g. https://schuldorf.webuntis.com/WebUntis */
	private val serverRoot: String = User.buildApiUrl("", schoolInfo).toString()

	val startUrl: String = computeStartUrl()

	init {
		// Fresh environment each time the SSO screen opens: a new ViewModel is created per
		// navigation, so drop the previous session's cookies and DOM storage here. Otherwise
		// the WebView silently reuses the last account (auto-login) when someone tries to
		// sign in with a different account at the same school.
		CookieManager.getInstance().removeAllCookies(null)
		CookieManager.getInstance().flush()
		WebStorage.getInstance().deleteAllData()
	}

	private fun computeStartUrl(): String {
		val info = schoolInfo ?: return serverRoot
		val advertisedEntry = if (info.useMobileServiceUrlAndroid == true && !info.mobileServiceUrl.isNullOrBlank())
			info.mobileServiceUrl
		else
			info.serverUrl

		if (advertisedEntry.isNullOrBlank() || !advertisedEntry.contains("://")) {
			// Legacy servers: classic SPA entry point
			return serverRoot.toUri().buildUpon()
				.appendPath("index.html")
				.appendQueryParameter("school", info.loginName.orEmpty())
				.toString()
		}

		// Newer Untis deployments advertise a full web entry URL (incl. path and/or school query).
		val uri = advertisedEntry.toUri()
		if (uri.getQueryParameter("school").isNullOrBlank()) {
			return uri.buildUpon()
				.appendQueryParameter("school", info.loginName.orEmpty())
				.toString()
		}
		return uri.toString()
	}

	var pageLoading by mutableStateOf(true)
		private set

	var harvestError by mutableStateOf<Int?>(null)
		private set

	var waitingForLogin by mutableStateOf(true)
		private set

	/** The URL the WebView should load next (detected SSO entry or manual web-login fallback). */
	var webTargetUrl by mutableStateOf<String?>(null)
		private set

	/** True when the school advertises no SSO provider at all. */
	var ssoNotAvailable by mutableStateOf(false)
		private set

	private var harvesting = false
	private var lastAttemptElapsed = 0L

	fun goBack() {
		navigator.popBackStack()
	}

	/**
	 * Reads the school's web entry page and extracts the SSO provider login URL it
	 * advertises in its bootstrap config (e.g. "ssoOffice365LoginUrl":"oauth2/authorization/office365").
	 * Falls back to showing the plain web login when no provider is configured.
	 */
	fun detectAndPrepare() {
		if (webTargetUrl != null || ssoNotAvailable) return
		viewModelScope.launch {
			val loginUrl = detectSsoLoginUrl()
			if (loginUrl != null) {
				Log.i(TAG, "Detected SSO login URL: $loginUrl")
				webTargetUrl = loginUrl
			} else {
				Log.i(TAG, "School does not advertise any SSO provider")
				ssoNotAvailable = true
				pageLoading = false
			}
		}
	}

	fun openWebLogin() {
		ssoNotAvailable = false
		webTargetUrl = startUrl
	}

	/** Opens the SSO/web-login URL in the system browser as a fallback. */
	fun browserUrl(): String = webTargetUrl ?: startUrl

	/** Default username for the manual login-capture dialog, taken from the school login name. */
	val captureUsername: String get() = schoolInfo?.loginName.orEmpty()

	var showCaptureDialog by mutableStateOf(false)
		private set

	fun showCapture() {
		showCaptureDialog = true
	}

	fun dismissCapture() {
		showCaptureDialog = false
	}

	/** Completes login from the credentials captured manually after using the browser. */
	fun submitCapture(username: String, key: String) {
		dismissCapture()
		if (username.isBlank() || key.isBlank()) return
		SsoLoginResultBus.publish(SsoLoginResult(username.trim(), key.trim()))
		navigator.popBackStack()
	}

	private suspend fun detectSsoLoginUrl(): String? {
		val html = fetchText(startUrl) ?: return null
		val provider = SsoProvider.fromConfig(html) ?: return null

		// Relative paths (e.g. "oauth2/authorization/office365", "oidc/login") resolve
		// against the school's WebUntis context root.
		return resolveAgainstServerRoot(provider.loginUrlPath)
	}

	private fun resolveAgainstServerRoot(url: String): String {
		if (url.contains("://")) return url
		if (url.startsWith("/")) {
			val serverUri = serverRoot.toUri()
			return "${serverUri.scheme}://${serverUri.encodedAuthority}$url"
		}
		return "$serverRoot/$url"
	}

	fun onPageStarted() {
		pageLoading = true
	}

	fun onPageFinished() {
		pageLoading = false
		onWebViewEvent()
	}

	fun onWebViewEvent() {
		viewModelScope.launch {
			maybeHarvest(force = false)
		}
	}

	fun retryHarvest() {
		harvestError = null
		viewModelScope.launch {
			maybeHarvest(force = true)
		}
	}

	/**
	 * Passive checks are throttled but never capped — the screen stays open until
	 * either the harvest succeeds or the user backs out.
	 */
	private suspend fun maybeHarvest(force: Boolean) {
		if (harvesting || schoolInfo == null) return
		if (!force) {
			if (SystemClock.elapsedRealtime() - lastAttemptElapsed < HARVEST_THROTTLE_MS) return
			val cookie = CookieManager.getInstance().getCookie(serverRoot)
			if (cookie?.contains("JSESSIONID=") != true) return
		}

		harvesting = true
		lastAttemptElapsed = SystemClock.elapsedRealtime()
		try {
			val result = fetchAccessData()
			if (result != null) {
				SsoLoginResultBus.publish(SsoLoginResult(result.first, result.second))
				navigator.popBackStack()
			} else if (force) {
				harvestError = R.string.ssologin_error
			}
		} finally {
			harvesting = false
		}
	}

	private suspend fun fetchText(url: String, cookieHeader: String? = null): String? {
		return try {
			withContext(Dispatchers.IO) {
				val connection = URL(url).openConnection() as HttpURLConnection
				try {
					connection.connectTimeout = 10_000
					connection.readTimeout = 10_000
					connection.instanceFollowRedirects = true
					connection.setRequestProperty("Accept", "text/html,application/json,*/*")
					cookieHeader?.let { connection.setRequestProperty("Cookie", it) }
					connection.setRequestProperty(
						"User-Agent",
						"Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 UntisPlus"
					)

					val code = connection.responseCode
					val stream = if (code in 200..299) connection.inputStream else connection.errorStream
					val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
					if (code in 200..299) body else null.also { Log.d(TAG, "GET $url -> $code body=${body.take(300)}") }
				} finally {
					connection.disconnect()
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "Failed to fetch $url", e)
			null
		}
	}

	/**
	 * Fetches the app shared secret with the web session cookies.
	 * The endpoint is the same one the WebUntis web app uses to show the QR code
	 * ("Profile -> Data access"), so the response contains username + key.
	 */
	private suspend fun fetchAccessData(): Pair<String, String>? {
		val cookieHeader = withContext(Dispatchers.Main) {
			CookieManager.getInstance().getCookie(serverRoot)
		}

		val body = fetchText("$serverRoot/api/profile/access", cookieHeader) ?: return null
		Log.v(TAG, "profile/access -> body=${body.take(600).redactSecret()}")
		return parseAccessData(body)
	}

	private fun parseAccessData(body: String): Pair<String, String>? {
		return try {
			val root = getJSON().parseToJsonElement(body).jsonObject
			// Current servers nest credentials under data.appCredentials; handle flatter shapes too.
			val data = (root["data"] as? JsonObject) ?: root
			val credentials = (data["appCredentials"] as? JsonObject) ?: data
			val user = credentials["user"]?.jsonPrimitive?.contentOrNull
				?: data["user"]?.jsonPrimitive?.contentOrNull
				?: data["userName"]?.jsonPrimitive?.contentOrNull
			val key = credentials["key"]?.jsonPrimitive?.contentOrNull
				?: data["key"]?.jsonPrimitive?.contentOrNull
				?: data["secret"]?.jsonPrimitive?.contentOrNull
			if (user.isNullOrBlank() || key.isNullOrBlank()) null
			else user to key
		} catch (_: Exception) {
			null
		}
	}
}

private fun String.redactSecret(): String = replace(
	Regex("\"key\"\\s*:\\s*\"[A-Za-z0-9]+\""),
	"\"key\":\"[REDACTED]\""
)
