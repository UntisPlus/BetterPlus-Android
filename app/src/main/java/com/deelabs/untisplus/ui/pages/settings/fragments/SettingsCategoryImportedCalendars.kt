package com.deelabs.untisplus.ui.pages.settings.fragments

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deelabs.untisplus.R
import com.deelabs.untisplus.data.database.entities.ImportedCalendarEntity
import com.deelabs.untisplus.ui.pages.settings.ImportedCalendarsViewModel
import com.sapuseven.compose.protostore.ui.preferences.PreferenceGroup

@Composable
fun SettingsCategoryImportedCalendars(viewModel: ImportedCalendarsViewModel = hiltViewModel()) {
	val calendars by viewModel.calendars.collectAsStateWithLifecycle()
	val busy by viewModel.busy.collectAsStateWithLifecycle()

	var showAddDialog by remember { mutableStateOf(false) }
	var pendingFile by remember { mutableStateOf<android.net.Uri?>(null) }
	var deleteTarget by remember { mutableStateOf<ImportedCalendarEntity?>(null) }

	val filePicker = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri ->
		uri?.let { pendingFile = it }
	}

	PreferenceGroup(stringResource(R.string.preference_timetable_imported_calendars_manage)) {
		if (calendars.isEmpty()) {
			ListItem(
				headlineContent = { Text(stringResource(R.string.preference_timetable_imported_calendars_empty)) }
			)
		} else {
			calendars.forEach { calendar ->
				ListItem(
					headlineContent = { Text(calendar.name) },
					supportingContent = {
						Text(
							buildString {
								append(calendar.url ?: calendar.localPath.orEmpty())
								append("\n")
								append(
									calendar.lastError?.let {
										stringResource(R.string.preference_timetable_imported_calendars_error, it)
									} ?: calendar.lastRefresh?.let {
										stringResource(
											R.string.preference_timetable_imported_calendars_last_refresh,
											DateUtils.getRelativeTimeSpanString(it)
										)
									} ?: stringResource(R.string.preference_timetable_imported_calendars_never)
								)
							}
						)
					},
					leadingContent = {
						Box(
							modifier = Modifier
								.size(24.dp)
								.background(Color(calendar.color), CircleShape)
						)
					},
					trailingContent = {
						Switch(
							checked = calendar.enabled,
							onCheckedChange = { viewModel.setEnabled(calendar, it) }
						)
					},
					modifier = Modifier.clickable { deleteTarget = calendar }
				)
			}
		}
	}

	ListItem(
		headlineContent = { Text(stringResource(R.string.preference_timetable_imported_calendars_add_url)) },
		leadingContent = {
			Icon(painter = painterResource(R.drawable.settings_timetable_range), contentDescription = null)
		},
		modifier = Modifier.clickable { showAddDialog = true }
	)

	ListItem(
		headlineContent = { Text(stringResource(R.string.preference_timetable_imported_calendars_import_file)) },
		leadingContent = {
			Icon(painter = painterResource(R.drawable.settings_timetable_range), contentDescription = null)
		},
		modifier = Modifier.clickable {
			filePicker.launch(arrayOf("text/calendar", "application/octet-stream", "*/*"))
		}
	)

	ListItem(
		headlineContent = { Text(stringResource(R.string.preference_timetable_imported_calendars_refresh_all)) },
		leadingContent = {
			Icon(painter = painterResource(R.drawable.settings_timetable_range), contentDescription = null)
		},
		modifier = Modifier.clickable(enabled = !busy) { viewModel.refreshAll() }
	)

	if (showAddDialog) {
		AddCalendarUrlDialog(
			onDismiss = { showAddDialog = false },
			onAdd = { name, url ->
				showAddDialog = false
				viewModel.addUrl(name, url, ImportedCalendarEntity.DEFAULT_COLOR)
			}
		)
	}

	pendingFile?.let { uri ->
		val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
		AddUrlDialog(
			title = stringResource(R.string.preference_timetable_imported_calendars_import_file),
			initialName = name,
			showUrl = false,
			onDismiss = { pendingFile = null },
			onAdd = { label, _ ->
				pendingFile = null
				viewModel.importFile(label, uri, ImportedCalendarEntity.DEFAULT_COLOR)
			}
		)
	}

	deleteTarget?.let { calendar ->
		AlertDialog(
			onDismissRequest = { deleteTarget = null },
			title = { Text(stringResource(R.string.preference_timetable_imported_calendars_delete)) },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text(stringResource(R.string.preference_timetable_imported_calendars_delete_confirm, calendar.name))
				}
			},
			confirmButton = {
				TextButton(
					colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
					onClick = {
						viewModel.delete(calendar)
						deleteTarget = null
					}
				) {
					Text(stringResource(R.string.preference_timetable_imported_calendars_delete))
				}
			},
			dismissButton = {
				TextButton(onClick = { viewModel.refresh(calendar) }) {
					Text(stringResource(R.string.preference_timetable_imported_calendars_refresh))
				}
			}
		)
	}
}

@Composable
private fun AddCalendarUrlDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
	AddUrlDialog(
		title = stringResource(R.string.preference_timetable_imported_calendars_add_url),
		initialName = "",
		showUrl = true,
		onDismiss = onDismiss,
		onAdd = onAdd
	)
}

@Composable
private fun AddUrlDialog(
	title: String,
	initialName: String,
	showUrl: Boolean,
	onDismiss: () -> Unit,
	onAdd: (String, String) -> Unit
) {
	var name by remember { mutableStateOf(initialName) }
	var url by remember { mutableStateOf("") }
	var urlTouched by remember { mutableStateOf(false) }
	val urlValid = !showUrl || url.isNotBlank()

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it },
					label = { Text(stringResource(R.string.preference_timetable_imported_calendars_name)) },
					singleLine = true
				)
				if (showUrl) {
					OutlinedTextField(
						value = url,
						onValueChange = {
							url = it
							urlTouched = true
						},
						label = { Text(stringResource(R.string.preference_timetable_imported_calendars_url)) },
						isError = urlTouched && !urlValid,
						singleLine = true
					)
					if (urlTouched && !urlValid) {
						Text(stringResource(R.string.preference_timetable_imported_calendars_invalid_url))
					}
				}
			}
		},
		confirmButton = {
			TextButton(enabled = urlValid, onClick = { onAdd(name, url) }) {
				Text(stringResource(R.string.all_save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_cancel))
			}
		}
	)
}
