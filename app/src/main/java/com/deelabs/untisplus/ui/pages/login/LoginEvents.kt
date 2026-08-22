package com.deelabs.untisplus.ui.pages.login

sealed class LoginEvents {
	data object ClearFocus : LoginEvents()
}
