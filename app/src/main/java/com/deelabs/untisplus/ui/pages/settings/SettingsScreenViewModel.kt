package com.deelabs.untisplus.ui.pages.settings

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_REPOSITORY_API
import com.deelabs.untisplus.data.model.github.GitHubUser
import com.deelabs.untisplus.data.repository.GlobalSettingsRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.services.AutoMuteService
import com.deelabs.untisplus.services.AutoMuteServiceZenRuleImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Named

@SuppressLint("NewApi")
@HiltViewModel(assistedFactory = SettingsScreenViewModel.Factory::class)
class SettingsScreenViewModel @AssistedInject constructor(
	globalSettingsRepository: GlobalSettingsRepository,
	private val userRepository: UserRepository,
	internal val userSettingsRepository: UserSettingsRepository,
	internal val autoMuteService: AutoMuteService,
	internal val savedStateHandle: SavedStateHandle,
	internal val masterDataRepository: MasterDataRepository,
	@Named("json") private val httpClient: HttpClient,
	@Assisted val colorScheme: ColorScheme,
	private val proxyRepository: ProxyRepository,
) : ViewModel() {
	@AssistedFactory
	interface Factory {
		fun create(colorScheme: ColorScheme): SettingsScreenViewModel
	}

	init {
		if (autoMuteService is AutoMuteServiceZenRuleImpl) {
			autoMuteService.setUser(userRepository.currentUser!!)
		}
		// Refresh the proxy tier/permissions for feature gating; ignore failures.
		if (proxyRepository.isAvailable) {
			viewModelScope.launch {
				runCatching { proxyRepository.refreshMe() }
			}
		}
	}

	val globalRepository = globalSettingsRepository

	private val _contributors = MutableStateFlow<List<GitHubUser>>(emptyList())
	val contributors: StateFlow<List<GitHubUser>> = _contributors

	private val _contributorsError = MutableStateFlow<Throwable?>(null)
	val contributorsError: StateFlow<Throwable?> = _contributorsError

	fun resetColors() = viewModelScope.launch {
		userSettingsRepository.updateSettings {
			clearBackgroundRegular()
			clearBackgroundRegularPast()
			clearBackgroundExam()
			clearBackgroundExamPast()
			clearBackgroundIrregular()
			clearBackgroundIrregularPast()
			clearBackgroundCancelled()
			clearBackgroundCancelledPast()
		}
	}

	fun currentUserId() = userRepository.currentUser?.id

	/** Whether the untis-proxy features apply to the active account. */
	val proxyAvailable: Boolean
		get() = proxyRepository.isAvailable

	/** Fetches (or creates) the account's `.ics` calendar subscription URL. */
	suspend fun calendarSubscriptionUrl(): Result<String> = runCatching {
		val classId = userRepository.currentUser?.proxyClassId
			?: error("This account has no class to subscribe to")
		proxyRepository.calendarToken(classId).url
	}

	suspend fun loadContributors() {
		_contributorsError.value = null

		try {
			_contributors.value = httpClient.get("$URL_GITHUB_REPOSITORY_API/contributors").body()
		} catch (e: Exception) {
			_contributorsError.value = e
		}
	}
}
