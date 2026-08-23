package com.deelabs.untisplus.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.pages.main.MainAppContent
import com.deelabs.untisplus.ui.pages.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
	companion object {
		const val MESSENGER_PACKAGE_NAME = "com.untis.chat"

		const val EXTRA_STRING_PERIOD_ELEMENT = "com.deelabs.untisplus.activities.main.element"
	}

	@Inject
	lateinit var appNavigator: AppNavigator

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)

		// Handle deep link (login data) BEFORE you setContent
		handleIntent(intent)

		setContent {
			// ViewModel (or other injector) that exposes current userState + settings
			val viewModel: MainViewModel = hiltViewModel()
			val userState by viewModel.userState.collectAsState()
			val currentIntentData by viewModel.pendingIntentData.collectAsState()

			// If there was a “loginData” in the Intent, navigate once:
			LaunchedEffect(currentIntentData) {
				currentIntentData?.let { dataString ->
					// send this into your NavController or AppNavigator
					viewModel.consumeIntentData()
					viewModel.appNavigator.navigate(AppRoutes.LoginDataInput(autoLoginData = dataString))
				}
			}

			MainAppContent(
				userState = userState,
				settingsFlow = viewModel.userSettingsFlow,
				navigator = viewModel.appNavigator,
				consumeApiProviderSelectionStart = viewModel::consumeApiProviderSelectionStart
			)
		}

		/*val action: String? = intent?.action
		val data: Uri? = intent?.data
		when {
			Intent.ACTION_VIEW == action && data?.host == "setschool" -> {
				appNavigator.navigate(AppRoutes.LoginDataInput(autoLoginData = data.toString()))
			}
		}

		setContent {
			// TODO: Sooo... this doesn't change the theme when the user is switched...
			val userSettingsRepository = userSettingsRepositoryFactory.create(MaterialTheme.colorScheme)

			val darkTheme by userSettingsRepository.getSettings().map {
				when (it.darkTheme) {
					"on" -> ThemeMode.AlwaysDark
					"off" -> ThemeMode.AlwaysLight
					else -> ThemeMode.FollowSystem
				}
			}.collectAsState(ThemeMode.FollowSystem)

			val darkThemeOled by userSettingsRepository.getSettings().map {
				it.darkThemeOled.takeIf { _ -> it.hasDarkThemeOled() }
			}.collectAsState(null)

			val themeColor by userSettingsRepository.getSettings().map {
				if (it.customThemeColor) Color(it.themeColor) else null
			}.collectAsState(null)

			AppTheme(darkTheme, darkThemeOled, themeColor) {
				Surface(
					modifier = Modifier.fillMaxSize()
				) {
					val userState by userRepository.userState.collectAsState()

					when (userState) {
						is UserState.Loading -> Box(
							modifier = Modifier.fillMaxSize(),
							contentAlignment = Alignment.Center
						) {
							CircularProgressIndicator()
						}

						is UserState.NoUsers -> {
							AppNavHost(
								navigator = appNavigator,
								startDestination = AppRoutes.Login
							)
						}

						is UserState.User -> {
							AppNavHost(
								navigator = appNavigator,
								startDestination = AppRoutes.Timetable()
							)

							// Re-create the view when the user changes
							LaunchedEffect(Unit) {
								snapshotFlow { userState }
									.drop(1)
									.collect {
										appNavigator.navigate(AppRoutes.Timetable()) {
											popUpTo(0) // Clear backstack
										}
									}
							}
						}
					}
				}
			}
		}*/
	}


	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent)
	}

	private fun handleIntent(intent: Intent) {
		when {
			Intent.ACTION_VIEW == intent.action && intent.data?.host == "setschool" -> {
				appNavigator.navigate(AppRoutes.LoginDataInput(autoLoginData = intent.data.toString()))
			}
		}
	}
}
