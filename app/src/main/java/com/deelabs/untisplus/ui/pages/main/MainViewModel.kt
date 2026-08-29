package com.deelabs.untisplus.ui.pages.main

import androidx.lifecycle.ViewModel
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.settings.model.UserSettings
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
	private val userRepository: UserRepository,
	userSettingsRepository: UserSettingsRepository,
	val appNavigator: AppNavigator
) : ViewModel() {

	// Expose the userState directly
	val userState: StateFlow<UserRepository.UserState> = userRepository.userState

	// Expose the Flow<UserSettings> from the repository so Compose can collect it
	val userSettingsFlow: Flow<UserSettings> = userSettingsRepository.getSettings()

	// A small “one‐time event” for deep‐link data
	private val _pendingIntentData = MutableStateFlow<String?>(null)
	val pendingIntentData: StateFlow<String?> = _pendingIntentData

	fun onDeepLink(dataString: String) {
		_pendingIntentData.value = dataString
	}

	fun consumeIntentData() {
		_pendingIntentData.value = null
	}
}
