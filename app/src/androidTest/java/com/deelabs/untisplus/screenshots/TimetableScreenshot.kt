package com.deelabs.untisplus.screenshots

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.hilt.navigation.compose.hiltViewModel
import com.sapuseven.compose.protostore.ui.preferences.materialColors
import com.deelabs.untisplus.activities.MainActivity
import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.settings.model.Settings
import com.deelabs.untisplus.modules.FakeUserModule.MOCK_USER_ID
import com.deelabs.untisplus.ui.pages.login.datainput.DEMO_API_URL
import com.deelabs.untisplus.ui.pages.timetable.Timetable
import com.deelabs.untisplus.ui.pages.timetable.TimetableViewModel
import com.deelabs.untisplus.ui.theme.generateColorScheme
import com.deelabs.untisplus.utils.WithScreenshot
import com.deelabs.untisplus.utils.takeScreenshot
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class TimetableScreenshot {
	@get:Rule
	var rule = createAndroidComposeRule<MainActivity>()

	@get:Rule
	var hiltRule = HiltAndroidRule(this)

	@Inject
	lateinit var userDao: UserDao

	@Inject
	lateinit var userDataApi: UserDataApi

	@Inject
	lateinit var dataStore: DataStore<Settings>

	@Before
	fun init() {
		hiltRule.inject()

		runBlocking {
			userDataApi.getUserData(DEMO_API_URL, null, null).let {
				userDao.insertMasterData(MOCK_USER_ID, it.masterData)
			}
		}
	}

	@Test
	fun timetableScreenshotLightRed() {
		timetableScreenshotWithTheme(materialColors[0], false, "light-1-red")
	}

	@Test
	fun timetableScreenshotLightGreen() {
		timetableScreenshotWithTheme(materialColors[11], false, "light-4-green")
	}

	@Test
	fun timetableScreenshotDarkIndigo() {
		timetableScreenshotWithTheme(materialColors[5], true, "dark-6-indigo")
	}

	@OptIn(ExperimentalTestApi::class)
	fun timetableScreenshotWithTheme(
		themeColor: Color,
		darkTheme: Boolean = false,
		themeColorName: String
	) {
		var viewModel: TimetableViewModel
		rule.activity.setContent {
			val colorScheme = generateColorScheme(rule.activity, false, themeColor, darkTheme, false)
			val typography = MaterialTheme.typography
			viewModel = hiltViewModel<TimetableViewModel, TimetableViewModel.Factory>(
				creationCallback = { factory -> factory.create(colorScheme, typography) }
			)

			MaterialTheme(
				colorScheme = colorScheme
			) {
				WithScreenshot {
					Timetable(viewModel = viewModel)
				}
			}
		}
		rule.waitForIdle()
		rule.onNodeWithText("Save").performClick()
		rule.waitUntilDoesNotExist(hasTestTag("loading"), timeoutMillis = 10_000)
		rule.waitForIdle()
		rule.takeScreenshot("timetable-${themeColorName}.png")
	}
}
