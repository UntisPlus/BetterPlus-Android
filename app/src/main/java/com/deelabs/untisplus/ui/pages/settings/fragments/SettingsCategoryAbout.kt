package com.deelabs.untisplus.ui.pages.settings.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sapuseven.compose.protostore.ui.preferences.Preference
import com.sapuseven.compose.protostore.ui.preferences.PreferenceGroup
import com.deelabs.untisplus.BuildConfig
import com.deelabs.untisplus.R
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_PRIVACY_POLICY
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_REPOSITORY
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.pages.settings.SettingsScreenViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCategoryAbout(navController: NavController, viewModel: SettingsScreenViewModel) {
	val uriHandler = LocalUriHandler.current

	Preference(
		title = { Text(stringResource(R.string.app_name)) },
		summary = {
			Text(
				stringResource(
					R.string.preference_info_app_version_desc,
					BuildConfig.VERSION_NAME,
					BuildConfig.VERSION_CODE
				)
			)
		},
		onClick = {
			uriHandler.openUri("$URL_GITHUB_REPOSITORY/releases")
		},
		leadingContent = {
			Icon(
				painter = painterResource(R.drawable.settings_about_app_icon),
				contentDescription = null
			)
		}
	)

	if (viewModel.proxyAvailable) {
		var showProxyDiagnostics by remember { mutableStateOf(false) }

		Preference(
			title = { Text(stringResource(R.string.preference_proxy_status)) },
			summary = { Text(stringResource(R.string.preference_proxy_status_desc)) },
			onClick = { showProxyDiagnostics = true },
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_info),
					contentDescription = null
				)
			}
		)

		if (showProxyDiagnostics) {
			ProxyDiagnosticsDialog(
				viewModel = viewModel,
				onDismiss = { showProxyDiagnostics = false }
			)
		}
	}

	PreferenceGroup(stringResource(id = R.string.preference_info_general)) {
		val externalSourceDialog = remember { mutableStateOf(false) }

		Preference(
			title = { Text(stringResource(R.string.preference_info_github)) },
			summary = {
				//Text(URL_GITHUB_REPOSITORY)
			},
			onClick = {
				uriHandler.openUri(URL_GITHUB_REPOSITORY)
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_info_github),
					contentDescription = null
				)
			}
		)

		Preference(
			title = { Text(stringResource(R.string.preference_info_license)) },
			summary = { Text(stringResource(R.string.preference_info_license_desc)) },
			onClick = {
				uriHandler.openUri("$URL_GITHUB_REPOSITORY/blob/master/LICENSE")
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_info_github),
					contentDescription = null
				)
			}
		)

		Preference(
			title = { Text(stringResource(R.string.preference_info_contributors)) },
			summary = { Text(stringResource(R.string.preference_info_contributors_desc)) },
			onClick = {
				externalSourceDialog.value = true
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_about_contributor),
					contentDescription = null
				)
			}
		)

		if (externalSourceDialog.value) {
			AlertDialog(
				onDismissRequest = {
					externalSourceDialog.value = false
				},
				confirmButton = {
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
						verticalArrangement = Arrangement.spacedBy(8.dp),
						modifier = Modifier
							.fillMaxWidth()
					) {
						TextButton(
							onClick = {
								uriHandler.openUri(URL_GITHUB_PRIVACY_POLICY)
							}
						) {
							Text(text = stringResource(id = R.string.preference_info_privacy_policy))
						}

						Row(
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							TextButton(
								onClick = {
									externalSourceDialog.value = false
								}
							) {
								Text(text = stringResource(id = R.string.all_cancel))
							}

							TextButton(
								onClick = {
									externalSourceDialog.value = false
									navController.navigate(AppRoutes.Settings.About.Contributors)
								}
							) {
								Text(text = stringResource(id = R.string.all_ok))
							}
						}
					}
				},
				title = {
					Text(text = stringResource(id = R.string.preference_info_privacy))
				},
				text = {
					Text(stringResource(id = R.string.preference_info_privacy_desc))
				}
			)
		}

		Preference(
			title = { Text(stringResource(R.string.preference_info_libraries)) },
			summary = { Text(stringResource(R.string.preference_info_libraries_desc)) },
			onClick = { navController.navigate(AppRoutes.Settings.About.Libraries) },
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_about_library),
					contentDescription = null
				)
			}
		)
	}
}

@Composable
private fun ProxyDiagnosticsDialog(viewModel: SettingsScreenViewModel, onDismiss: () -> Unit) {
	var diagnostics by remember { mutableStateOf<SettingsScreenViewModel.ProxyDiagnostics?>(null) }
	var failed by remember { mutableStateOf(false) }
	var loading by remember { mutableStateOf(true) }

	LaunchedEffect(Unit) {
		viewModel.proxyDiagnostics()
			.onSuccess { diagnostics = it }
			.onFailure { failed = true }
		loading = false
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(text = stringResource(id = R.string.all_ok))
			}
		},
		title = { Text(text = stringResource(id = R.string.preference_proxy_status)) },
		text = {
			when {
				loading -> Row(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					CircularProgressIndicator()
					Text(text = stringResource(id = R.string.preference_proxy_status_loading))
				}

				failed -> Text(text = stringResource(id = R.string.preference_proxy_status_error))
				else -> diagnostics?.let { d ->
					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						DiagnosticRow("status", d.status.status)
						d.status.version?.let { DiagnosticRow("version", it) }
						d.status.mode?.let { DiagnosticRow("mode", it) }
						d.status.uptimeSec?.let { DiagnosticRow("uptime_sec", it.toString()) }

						HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

						DiagnosticRow("username", d.me.username)
						DiagnosticRow("level", d.me.level)
						DiagnosticRow("recon", d.me.permissions.recon.toString())
						DiagnosticRow("boosted", d.me.permissions.boosted.toString())
						DiagnosticRow("editor", d.me.permissions.editor.toString())
					}
				}
			}
		}
	)
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
	Row(modifier = Modifier.fillMaxWidth()) {
		Text(text = label, modifier = Modifier.padding(end = 8.dp))
		Text(text = value)
	}
}
