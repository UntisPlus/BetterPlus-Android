package com.deelabs.untisplus.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.deelabs.untisplus.helpers.AppTheme
import com.deelabs.untisplus.ui.pages.settings.automute.AutoMuteSettings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AutoMuteConfigurationActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			AppTheme {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					AutoMuteSettings() { finish() }
				} else {
					Text("Auto-Mute is not supported on this device.")
				}
			}
		}
	}
}
