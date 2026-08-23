package com.deelabs.untisplus.ui.pages.settings.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sapuseven.compose.protostore.ui.preferences.Preference
import com.sapuseven.compose.protostore.ui.preferences.PreferenceGroup
import com.sapuseven.compose.protostore.ui.preferences.SwitchPreference
import com.sapuseven.compose.protostore.ui.preferences.WeekRangePreference
import com.deelabs.untisplus.BuildConfig
import com.deelabs.untisplus.R
import com.deelabs.untisplus.activities.AutoMuteConfigurationActivity
import com.deelabs.untisplus.services.AutoMuteServiceZenRuleImpl
import com.deelabs.untisplus.ui.pages.settings.SettingsScreenViewModel
import com.deelabs.untisplus.ui.pages.settings.automute.AutoMuteSettingsViewModel.Companion.EXTRA_USER_ID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SuppressLint("InlinedApi")
@Composable
fun SettingsCategoryGeneral(viewModel: SettingsScreenViewModel) {
	/* PreferenceGroup(stringResource(id = R.string.preference_category_general_behaviour)) {
	// Not supported, not planned. May be reconsidered if there is demand for this feature.
	SwitchPreference(
		title = { Text(stringResource(R.string.preference_double_tap_to_exit)) },
		settingsRepository = viewModel.repository,
		value = { it.exitConfirmation },
		onValueChange = { exitConfirmation = it }
	)

	// Not supported. May be reconsidered if there is demand for this feature.
	// Could be implemented by setting flingBehavior on the WeekView HorizontalPager
	SwitchPreference(
		title = { Text(stringResource(R.string.preference_flinging_enable)) },
		settingsRepository = viewModel.repository,
		value = { it.flingEnable },
		onValueChange = { flingEnable = it }
	)*/

	PreferenceGroup(stringResource(R.string.preference_category_general_week_display)) {
		WeekRangePreference(
			title = { Text(stringResource(R.string.preference_week_custom_range)) },
			summary = { Text(stringResource(R.string.preference_week_custom_range_summary)) },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.weekCustomRangeList.toSet() },
			onValueChange = {
				clearWeekCustomRange()
				addAllWeekCustomRange(it)
			}
		)

		/* Not supported yet
		SwitchPreference(
			title = { Text(stringResource(R.string.preference_week_snap_to_days)) },
			summary = { Text(stringResource(R.string.preference_week_snap_to_days_summary)) },
			settingsRepository = viewModel.repository,
			value = { it.weekSnapToDays },
			onValueChange = { weekSnapToDays = it }
		)

		SliderPreference(
			title = { Text(stringResource(R.string.preference_week_display_length)) },
			summary = { Text(stringResource(R.string.preference_week_display_length_summary)) },
			valueRange = 0f..7f,
			steps = 6,
			enabledCondition = { it.weekSnapToDays },
			showSeekBarValue = true,
			settingsRepository = viewModel.repository,
			value = { it.weekCustomLength },
			onValueChange = { weekCustomLength = it }
		)
		*/
	}

	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		val visible by viewModel.userSettingsRepository.getSettings().map { it.automuteEnable }.collectAsState(initial = false)
		ScheduleExactAlarmInfoMessage(
			visible = visible,
			primaryText = R.string.preference_automute_exact_alarms_unavailable,
			secondaryText = stringResource(
				R.string.preference_automute_exact_alarms_unavailable_desc,
				stringResource(R.string.app_name)
			)
		)
	}

	when (viewModel.autoMuteService) {
		is AutoMuteServiceZenRuleImpl -> {
			PreferenceGroup(stringResource(id = R.string.preference_category_general_automute)) {
				val context = LocalContext.current
				val scope = rememberCoroutineScope()
				val permissionLauncher =
					rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
						if (viewModel.autoMuteService.isPermissionGranted()) {
							scope.launch {
								viewModel.userSettingsRepository.updateSettings {
									automuteEnable = true
								}
								viewModel.autoMuteService.autoMuteEnable()
							}
						}
					}

				LaunchedEffect(Unit) {
					viewModel.userSettingsRepository.updateSettings {
						automuteEnable = viewModel.autoMuteService.isAutoMuteEnabled()
					}
				}

				SwitchPreference(
					title = { Text(stringResource(R.string.preference_automute_enable)) },
					summary = { Text(stringResource(R.string.preference_automute_enable_summary)) },
					settingsRepository = viewModel.userSettingsRepository,
					value = { it.automuteEnable },
					onValueChange = {
						if (it) {
							if (viewModel.autoMuteService.isPermissionGranted()) {
								viewModel.autoMuteService.autoMuteEnable()
								automuteEnable = true
							} else {
								permissionLauncher.launch(Intent(ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
								automuteEnable = false
							}
						} else {
							viewModel.autoMuteService.autoMuteDisable()
							automuteEnable = false
						}
					}
				)

				Preference(
					title = { Text(stringResource(R.string.preference_automute_preferences)) },
					trailingContent = {
						Icon(
							painter = painterResource(R.drawable.settings_external),
							contentDescription = null
						)
					},
					onClick = {
						context.startActivity(Intent(context, AutoMuteConfigurationActivity::class.java).apply {
							putExtra(EXTRA_USER_ID, viewModel.currentUserId())
						})
					}
				)
			}
		}
		else -> {
			// Auto-Mute is not supported on this device - hide the category
		}
	}

	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		val packageName = LocalContext.current.packageName
		val context = LocalContext.current
		Preference(
			title = { Text(text = stringResource(id = R.string.preference_app_language)) },
			onClick = {
				context.startActivity(
					Intent(
						android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
						Uri.parse("package:$packageName")
					)
				)
			}
		)
	}
}
