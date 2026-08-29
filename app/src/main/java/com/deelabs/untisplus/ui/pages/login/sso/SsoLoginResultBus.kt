package com.deelabs.untisplus.ui.pages.login.sso

import kotlinx.coroutines.flow.MutableStateFlow

data class SsoLoginResult(
	val username: String,
	val key: String
)

/**
 * One-shot channel from [SsoLoginViewModel] back to the LoginDataInput screen,
 * since the SSO screen pops itself off the back stack on success.
 */
object SsoLoginResultBus {
	val result = MutableStateFlow<SsoLoginResult?>(null)

	fun publish(result: SsoLoginResult) {
		this.result.value = result
	}

	fun consume(): SsoLoginResult? {
		return result.value.also { result.value = null }
	}
}
