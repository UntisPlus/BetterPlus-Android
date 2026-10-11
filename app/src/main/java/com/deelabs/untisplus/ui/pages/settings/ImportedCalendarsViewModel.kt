package com.deelabs.untisplus.ui.pages.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.data.database.entities.ImportedCalendarEntity
import com.deelabs.untisplus.data.repository.ImportedCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the imported-calendars screen: the user's `.ics` subscriptions and the
 * add/refresh/enable/delete actions backed by [ImportedCalendarRepository].
 */
@HiltViewModel
class ImportedCalendarsViewModel @Inject constructor(
	private val repository: ImportedCalendarRepository,
) : ViewModel() {
	val calendars: StateFlow<List<ImportedCalendarEntity>> = repository.calendarsFlow()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	private val _busy = MutableStateFlow(false)
	val busy: StateFlow<Boolean> = _busy

	fun addUrl(name: String, url: String, color: Int) = viewModelScope.launch {
		_busy.value = true
		runCatching { repository.addUrl(name, url, color) }
		_busy.value = false
	}

	fun importFile(name: String, uri: Uri, color: Int) = viewModelScope.launch {
		_busy.value = true
		runCatching { repository.importFile(name, uri, color) }
		_busy.value = false
	}

	fun setEnabled(calendar: ImportedCalendarEntity, enabled: Boolean) = viewModelScope.launch {
		repository.setEnabled(calendar, enabled)
	}

	fun delete(calendar: ImportedCalendarEntity) = viewModelScope.launch {
		repository.delete(calendar)
	}

	fun refresh(calendar: ImportedCalendarEntity) = viewModelScope.launch {
		repository.refresh(calendar.id)
	}

	fun refreshAll() = viewModelScope.launch {
		_busy.value = true
		repository.refreshAll()
		_busy.value = false
	}
}
