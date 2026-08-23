package com.deelabs.untisplus.ui.pages.login.apiprovider

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.User.Companion.normalizeApiEndpoint
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ApiProviderSelectViewModel @Inject constructor(
	private val userDao: UserDao,
	private val userRepository: UserRepository,
	private val userDataApi: UserDataApi,
	private val navigator: AppNavigator,
	@Named("cacheDir") private val cacheDir: File,
) : ViewModel() {
	companion object {
		const val TAG = "ApiProviderSelect"
	}
	enum class Provider {
		OFFICIAL,
		UNTISPLUS,
		CUSTOM
	}

	var selection by mutableStateOf(Provider.OFFICIAL)

	var customUrl by mutableStateOf("")

	var saving by mutableStateOf(false)
		private set

	val customUrlValid: Boolean
		get() = normalizeApiEndpoint(customUrl) != null

	val isHttpCustom: Boolean
		get() = normalizeApiEndpoint(customUrl)?.first == "http"

	init {
		userRepository.currentUser?.let { user ->
			customUrl = user.customApiHost.orEmpty()
			selection = when {
				user.customApiHost?.isNotBlank() == true -> Provider.CUSTOM
				user.useUntisPlusApi -> Provider.UNTISPLUS
				else -> Provider.OFFICIAL
			}
		}
	}

	fun onContinue() {
		val user = userRepository.currentUser ?: run {
			navigator.navigate(AppRoutes.Timetable()) { popUpTo(0) }
			return
		}

		if (selection == Provider.CUSTOM && normalizeApiEndpoint(customUrl) == null) return
		viewModelScope.launch {
			saving = true
			try {
				withContext(Dispatchers.IO) {
					if (selection != Provider.OFFICIAL) clearApiCaches()
					// Persist the effective endpoint as the profile's API path so the
					// "Untis API path" field in profile editing reflects what is in use.
					val updated = when (selection) {
						Provider.OFFICIAL -> user.copy(
							useUntisPlusApi = false,
							customApiHost = null
						)

						Provider.UNTISPLUS -> user.copy(
							apiHost = "https://${User.UNTISPLUS_API_HOST}",
							useUntisPlusApi = true,
							customApiHost = null
						)

						Provider.CUSTOM -> {
							val (scheme, authority) = normalizeApiEndpoint(customUrl)!!
							user.copy(
								apiHost = "$scheme://$authority",
								useUntisPlusApi = false,
								customApiHost = "$scheme://$authority"
							)
						}
					}
					userDao.update(updated)
					userRepository.switchUser(updated)
					if (selection != Provider.OFFICIAL) {
						// Force a complete re-login against the new endpoint using the
						// cached credentials, and refresh all persisted user data.
						try {
							val jsonRpcUrl = User.buildJsonRpcApiUrl(
								updated.apiUrl,
								updated.schoolInfo?.loginName.orEmpty()
							).toString()
							val refreshed = userDataApi.getUserData(
								jsonRpcUrl,
								updated.user,
								updated.key.orEmpty()
							)
							val relogged = updated.copy(
								timeGrid = refreshed.masterData.timeGrid ?: updated.timeGrid,
								masterDataTimestamp = refreshed.masterData.timeStamp,
								userData = refreshed.userData,
								settings = refreshed.settings
							)
							userDao.update(relogged)
							userDao.deleteUserData(updated.id)
							userDao.insertMasterData(updated.id, refreshed.masterData)
							userRepository.switchUser(relogged)
						} catch (e: Exception) {
							Log.e(TAG, "Re-login via selected API failed", e)
						}
					}
				}
				navigator.navigate(AppRoutes.Timetable()) {
					popUpTo(0) // Pop all previous routes
				}
			} finally {
				saving = false
			}
		}
	}

	private fun clearApiCaches() {
		listOf("timetable", "periodData", "infocenter", "messenger")
			.asSequence()
			.map { File(cacheDir, it) }
			.filter { it.exists() }
			.forEach { it.deleteRecursively() }
	}
}
