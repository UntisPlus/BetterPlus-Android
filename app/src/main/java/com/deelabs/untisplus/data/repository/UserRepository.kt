package com.deelabs.untisplus.data.repository

import androidx.datastore.core.DataStore
import com.deelabs.untisplus.api.client.ProxyCookieStorage
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.settings.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
	private val userDao: UserDao,
	private val settingsDataStore: DataStore<Settings>,
	private val cookieStorage: ProxyCookieStorage,
) {
	private val _userState = MutableStateFlow<UserState>(UserState.Loading)
	val userState: StateFlow<UserState> = _userState

	val allUsersState: StateFlow<List<User>> = userDao.getAllFlow().stateIn(
		scope = CoroutineScope(Dispatchers.IO),
		started = SharingStarted.WhileSubscribed(),
		initialValue = emptyList()
	)

	val currentUser: User?
		get() = (_userState.value as? UserState.User)?.user

	init {
		loadActiveUser()
	}

	private fun loadActiveUser() {
		CoroutineScope(Dispatchers.IO).launch {
			switchUser(settingsDataStore.data.first().activeUser)
		}
	}

	fun switchUser(user: User, clearProxySession: Boolean = true) {
		val previousUser = (_userState.value as? UserState.User)?.user
		if (clearProxySession && previousUser != null && previousUser.id != user.id) {
			CoroutineScope(Dispatchers.IO).launch { cookieStorage.clear() }
		}
		_userState.value = UserState.User(user)
		CoroutineScope(Dispatchers.IO).launch {
			settingsDataStore.updateData { currentSettings ->
				currentSettings.toBuilder()
					.setActiveUser(user.id)
					.build()
			}
		}
	}

	suspend fun switchUser(userId: Long, clearProxySession: Boolean = true) {
		val user = userDao.getByIdAsync(userId)
			?: userDao.getAllFlow().first().firstOrNull()

		user?.let {
			switchUser(it, clearProxySession)
		} ?: run {
			_userState.value = UserState.NoUsers
		}
	}

	suspend fun deleteUser(user: User) {
		userDao.delete(user)
		// Drop the proxy session so a deleted account can't leave a live
		// JSESSIONID behind for the next user.
		cookieStorage.clear()

		val remainingUsers = userDao.getAllAsync()
		val currentState = _userState.value
		if (currentState is UserState.User && currentState.user == user) {
			if (remainingUsers.isEmpty()) {
				_userState.value = UserState.NoUsers
			} else {
				_userState.value = UserState.User(remainingUsers.first())
			}
		}
	}

	sealed class UserState {
		data object Loading : UserState()
		data object NoUsers : UserState()
		data class User(val user: com.deelabs.untisplus.data.database.entities.User) : UserState()
	}
}

