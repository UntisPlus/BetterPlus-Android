package com.deelabs.untisplus.ui.pages.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyRequest
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyTopic
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTarget
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTest
import com.deelabs.untisplus.api.model.proxy.ProxyWebhook
import com.deelabs.untisplus.api.model.proxy.ProxyWebhookRequest
import com.deelabs.untisplus.data.repository.ProxyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the self-service webhook/ntfy subscription screen
 * (`docs/PROXY-API.md` §5). Targets come from `/api/notifications/options`;
 * the two lists are server-backed and show only the caller's own rows.
 */
@HiltViewModel
class ChangeSubscriptionsViewModel @Inject constructor(
	private val proxyRepository: ProxyRepository
) : ViewModel() {
	private val _options = MutableStateFlow<ProxyCalendarOptions?>(null)
	val options: StateFlow<ProxyCalendarOptions?> = _options

	private val _webhooks = MutableStateFlow<List<ProxyWebhook>>(emptyList())
	val webhooks: StateFlow<List<ProxyWebhook>> = _webhooks

	private val _topics = MutableStateFlow<List<ProxyNtfyTopic>>(emptyList())
	val topics: StateFlow<List<ProxyNtfyTopic>> = _topics

	private val _loading = MutableStateFlow(true)
	val loading: StateFlow<Boolean> = _loading

	private val _failed = MutableStateFlow(false)
	val failed: StateFlow<Boolean> = _failed

	init {
		refresh()
	}

	fun refresh() = viewModelScope.launch {
		_loading.value = true
		_failed.value = false
		runCatching {
			_options.value = proxyRepository.notificationOptions()
			_webhooks.value = proxyRepository.webhooks().webhooks
			_topics.value = proxyRepository.ntfyTopics().topics
		}.onFailure {
			_failed.value = true
		}
		_loading.value = false
	}

	suspend fun createWebhook(target: ProxySubscriptionTarget, url: String, secret: String?): Result<Unit> =
		runCatching { proxyRepository.createWebhook(ProxyWebhookRequest(target, url, secret)) }
			.map { }
			.onSuccess { reloadWebhooks() }

	suspend fun createNtfy(target: ProxySubscriptionTarget, topic: String, baseUrl: String?): Result<Unit> =
		runCatching { proxyRepository.createNtfy(ProxyNtfyRequest(target, topic, baseUrl)) }
			.map { }
			.onSuccess { reloadTopics() }

	suspend fun testWebhook(id: Long): Result<ProxySubscriptionTest> =
		runCatching { proxyRepository.testWebhook(id) }

	suspend fun testNtfy(id: Long): Result<ProxySubscriptionTest> =
		runCatching { proxyRepository.testNtfy(id) }

	suspend fun deleteWebhook(id: Long): Result<Unit> =
		runCatching { proxyRepository.deleteWebhook(id) }
			.map { }
			.onSuccess { reloadWebhooks() }

	suspend fun deleteNtfy(id: Long): Result<Unit> =
		runCatching { proxyRepository.deleteNtfy(id) }
			.map { }
			.onSuccess { reloadTopics() }

	private suspend fun reloadWebhooks() {
		runCatching { proxyRepository.webhooks().webhooks }
			.onSuccess { _webhooks.value = it }
	}

	private suspend fun reloadTopics() {
		runCatching { proxyRepository.ntfyTopics().topics }
			.onSuccess { _topics.value = it }
	}
}
