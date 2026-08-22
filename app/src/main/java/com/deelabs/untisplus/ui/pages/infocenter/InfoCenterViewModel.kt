package com.deelabs.untisplus.ui.pages.infocenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.api.model.untis.enumeration.Right
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.domain.GetAbsencesUseCase
import com.deelabs.untisplus.domain.GetExamsUseCase
import com.deelabs.untisplus.domain.GetHomeworkUseCase
import com.deelabs.untisplus.domain.GetMessagesOfDayUseCase
import com.deelabs.untisplus.domain.GetMessagesUseCase
import com.deelabs.untisplus.domain.GetOfficeHoursUseCase
import com.deelabs.untisplus.model.rest.Message
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.pages.infocenter.fragments.AbsencesUiState
import com.deelabs.untisplus.ui.pages.infocenter.fragments.EventsUiState
import com.deelabs.untisplus.ui.pages.infocenter.fragments.MessagesUiState
import com.deelabs.untisplus.ui.pages.infocenter.fragments.OfficeHoursUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InfoCenterViewModel @Inject constructor(
	private val userRepository: UserRepository,
	internal val masterDataRepository: MasterDataRepository,
	private val navigator: AppNavigator,
	internal val userSettingsRepository: UserSettingsRepository,
	private val getMessages: GetMessagesUseCase,
	getMessagesOfDay: GetMessagesOfDayUseCase,
	getExams: GetExamsUseCase,
	getHomework: GetHomeworkUseCase,
	getAbsences: GetAbsencesUseCase,
	getOfficeHours: GetOfficeHoursUseCase,
) : ViewModel() {
	private val excuseStatuses = masterDataRepository.userData?.excuseStatuses ?: emptyList()

	val shouldShowAbsences: Boolean
		get() = Right.R_MY_ABSENCES in userRepository.currentUser!!.userData.rights
	val shouldShowAbsencesAdd: Boolean
		get() = Right.W_OWN_ABSENCE in userRepository.currentUser!!.userData.rights
	val shouldShowAbsencesAddReason: Boolean
		get() = Right.W_OWN_ABSENCEREASON in userRepository.currentUser!!.userData.rights

	val shouldShowOfficeHours: Boolean
		get() = Right.R_OFFICEHOURS in userRepository.currentUser!!.userData.rights

	val messagesState: StateFlow<MessagesUiState> = combine(
		getMessagesOfDay(),
		getMessages(),
		MessagesUiState::Success
	).stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = MessagesUiState.Loading
	)

	private val _selectedMessage = MutableStateFlow<Message?>(null)
	val selectedMessage: StateFlow<Message?> = _selectedMessage
	private val _selectedMessageContent = MutableStateFlow<String?>(null)
	val selectedMessageContent: StateFlow<String?> = _selectedMessageContent

	fun onMessageClicked(message: Message) {
		_selectedMessage.value = message
		viewModelScope.launch {
			getMessages(message.id)
				.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
				.collect { result ->
					result?.fold(
						onSuccess = { _selectedMessageContent.value = it.content },
						onFailure = {
							_selectedMessage.value = null
						} // TODO: Instead of having `null` as loading, use a sealed class to handle error responses as well.
					)
				}
		}
	}

	fun onMessageDismiss() {
		_selectedMessage.value = null
	}

	fun onMessageReply() {
		_selectedMessage.value = null
	}

	fun onMessageDelete() {
		_selectedMessage.value = null
	}

	val eventsState: StateFlow<EventsUiState> = combine(
		getExams(),
		getHomework(),
		EventsUiState::Success
	).stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = EventsUiState.Loading
	)

	val absencesState: StateFlow<AbsencesUiState> =
		(if (!shouldShowAbsences) emptyFlow() else getAbsences())
			.map { AbsencesUiState.Success(it, excuseStatuses) }
			.stateIn(
				scope = viewModelScope,
				started = SharingStarted.WhileSubscribed(5_000),
				initialValue = AbsencesUiState.Loading
			)

	val officeHoursState: StateFlow<OfficeHoursUiState> =
		(if (!shouldShowAbsences) emptyFlow() else getOfficeHours())
			.map(OfficeHoursUiState::Success)
			.stateIn(
				scope = viewModelScope,
				started = SharingStarted.WhileSubscribed(5_000),
				initialValue = OfficeHoursUiState.Loading
			)

	fun goBack() {
		navigator.popBackStack()
	}
}
