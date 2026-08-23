package com.deelabs.untisplus.ui.pages.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.deelabs.untisplus.R
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.settings.model.DarkTheme
import com.deelabs.untisplus.data.settings.model.UserSettings
import com.deelabs.untisplus.helpers.AppTheme
import com.deelabs.untisplus.helpers.ThemeMode
import com.deelabs.untisplus.ui.common.AppScaffold
import com.deelabs.untisplus.ui.navigation.AppNavHost
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
	userState: UserRepository.UserState,
	settingsFlow: Flow<UserSettings>,
	navigator: AppNavigator
) {
	val settings by settingsFlow.collectAsState(initial = UserSettings.getDefaultInstance())

	val darkTheme = when (settings.darkTheme) {
		DarkTheme.DARK -> ThemeMode.AlwaysDark
		DarkTheme.LIGHT -> ThemeMode.AlwaysLight
		else -> ThemeMode.FollowSystem
	}
	val darkThemeOled by remember { derivedStateOf { settings.darkThemeOled } }
	val themeColor by remember { derivedStateOf { if (settings.hasThemeColor()) Color(settings.themeColor) else null } }

	AppTheme(darkTheme, darkThemeOled, themeColor) {
		Surface(modifier = Modifier.fillMaxSize()) {
			when (userState) {
				is UserRepository.UserState.Loading -> {
					AppScaffold(
						topBar = {
							CenterAlignedTopAppBar(
								title = { Text(stringResource(id = R.string.app_name)) },
								navigationIcon = {
									IconButton(onClick = {}) {
										Icon(
											imageVector = Icons.Outlined.Menu,
											contentDescription = null
										)
									}
								},
								colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
									containerColor = Color.Transparent,
									scrolledContainerColor = Color.Transparent
								)
							)
						},
						modifier = Modifier.safeDrawingPadding()
					) {}
				}

				is UserRepository.UserState.NoUsers -> {
					AppNavHost(
						navigator = navigator,
						startDestination = AppRoutes.Login
					)
				}

				is UserRepository.UserState.User -> {
					key(userState.user.id) {
						AppNavHost(
							navigator = navigator,
							startDestination = AppRoutes.Timetable()
						)
					}

				}
			}
		}
	}
}
