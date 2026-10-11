package com.deelabs.untisplus.ui.pages.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
import com.deelabs.untisplus.data.repository.ProxyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the calendar-subscription screen: the subscribable targets from
 * `GET /api/calendar/options`, the account's own subscriptions from
 * `GET /api/calendar/tokens`, and the mint/re-scope/revoke actions. The token
 * is a bearer credential and is never stored locally — the list always comes
 * from the proxy.
 */
@HiltViewModel
class CalendarSubscriptionsViewModel @Inject constructor(
	private val proxyRepository: ProxyRepository,
) : ViewModel() {
	private val _options = MutableStateFlow<ProxyCalendarOptions?>(null)
	val options: StateFlow<ProxyCalendarOptions?> = _options

	private val _tokens = MutableStateFlow<List<ProxyCalendarToken>>(emptyList())
	val tokens: StateFlow<List<ProxyCalendarToken>> = _tokens

	private val _loading = MutableStateFlow(true)
	val loading: StateFlow<Boolean> = _loading

	private val _failed = MutableStateFlow(false)
	val failed: StateFlow<Boolean> = _failed

	init {
		refresh()
	}

	/** Reloads the options and the account's own subscriptions. */
	fun refresh() = viewModelScope.launch {
		_loading.value = true
		_failed.value = false
		runCatching {
			_options.value = proxyRepository.calendarOptions()
			_tokens.value = proxyRepository.calendarTokens()
		}.onFailure {
			_failed.value = true
		}
		_loading.value = false
	}

	/** Mints (or returns the existing) subscription for [request]. */
	suspend fun create(request: ProxyCalendarTokenRequest): Result<ProxyCalendarToken> =
		runCatching { proxyRepository.calendarToken(request) }
			.onSuccess { reloadTokens() }

	/** Re-scopes an existing subscription to a new lookahead window. */
	suspend fun updateDays(token: String, days: Int): Result<ProxyCalendarToken> =
		runCatching { proxyRepository.calendarUpdateDays(token, days) }
			.onSuccess { reloadTokens() }

	/** Revokes one of the account's own subscriptions. */
	suspend fun revoke(token: String): Result<Unit> =
		runCatching { proxyRepository.calendarRevoke(token) }
			.onSuccess { reloadTokens() }

	private suspend fun reloadTokens() {
		runCatching { proxyRepository.calendarTokens() }
			.onSuccess { _tokens.value = it }
	}
}
