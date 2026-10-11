package com.deelabs.untisplus.ui.pages.settings.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sapuseven.compose.protostore.ui.preferences.PreferenceGroup
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOption
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
import com.deelabs.untisplus.ui.pages.settings.CalendarSubscriptionsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsCategoryCalendar(viewModel: CalendarSubscriptionsViewModel = hiltViewModel()) {
	val context = LocalContext.current
	val uriHandler = LocalUriHandler.current
	val scope = rememberCoroutineScope()

	val options by viewModel.options.collectAsStateWithLifecycle()
	val tokens by viewModel.tokens.collectAsStateWithLifecycle()
	val loading by viewModel.loading.collectAsStateWithLifecycle()
	val failed by viewModel.failed.collectAsStateWithLifecycle()

	var showPicker by remember { mutableStateOf(false) }
	var createdUrl by remember { mutableStateOf<String?>(null) }
	var activeToken by remember { mutableStateOf<ProxyCalendarToken?>(null) }
	var revokeToken by remember { mutableStateOf<ProxyCalendarToken?>(null) }
	var editToken by remember { mutableStateOf<ProxyCalendarToken?>(null) }

	fun copyToClipboard(link: String) {
		val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText("UntisPlus calendar", link))
	}

	fun share(link: String) {
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_TEXT, link)
		}
		context.startActivity(Intent.createChooser(intent, null))
	}

	PreferenceGroup(stringResource(R.string.preference_timetable_calendar_your)) {
		when {
			loading && tokens.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_timetable_calendar_subscription)) },
				supportingContent = {
					Text(stringResource(R.string.preference_proxy_status_loading))
				}
			)

			failed && tokens.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_timetable_calendar_load_error)) },
				modifier = Modifier.clickable { viewModel.refresh() }
			)

			tokens.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_timetable_calendar_none)) }
			)

			else -> tokens.forEach { token ->
				ListItem(
					headlineContent = { Text(token.name?.takeIf { it.isNotBlank() } ?: token.token) },
					supportingContent = {
						Text(
							buildString {
								append(typeLabel(token.type))
								token.days?.let {
									append(" · ")
									append(context.getString(R.string.preference_timetable_calendar_window_days, it))
								}
								token.horizon?.takeIf { it.isNotBlank() }?.let {
									append(" · ")
									append(context.getString(R.string.preference_timetable_calendar_horizon, it))
								}
							}
						)
					},
					leadingContent = {
						Icon(
							painter = painterResource(id = R.drawable.settings_timetable_range),
							contentDescription = null
						)
					},
					modifier = Modifier.clickable { activeToken = token }
				)
			}
		}
	}

	ListItem(
		headlineContent = { Text(stringResource(R.string.preference_timetable_calendar_add)) },
		supportingContent = { Text(stringResource(R.string.preference_timetable_calendar_add_desc)) },
		leadingContent = {
			Icon(
				painter = painterResource(id = R.drawable.settings_timetable_range),
				contentDescription = null
			)
		},
		modifier = Modifier.clickable(enabled = options != null) { showPicker = true }
	)

	if (showPicker && options != null) {
		CalendarTargetPickerDialog(
			options = options!!,
			onDismiss = { showPicker = false },
			onPick = { target ->
				showPicker = false
				scope.launch {
					viewModel.create(target.request)
						.onSuccess { createdUrl = it.url }
						.onFailure {
							Toast.makeText(
								context,
								context.getString(R.string.preference_timetable_calendar_error),
								Toast.LENGTH_SHORT
							).show()
						}
				}
			}
		)
	}

	createdUrl?.let { link ->
		CalendarLinkDialog(
			link = link,
			onDismiss = { createdUrl = null },
			onCopy = {
				copyToClipboard(link)
				createdUrl = null
			},
			onShare = { share(link) },
			onOpen = {
				uriHandler.openUri(link)
				createdUrl = null
			}
		)
	}

	activeToken?.let { token ->
		CalendarActionsDialog(
			token = token,
			onDismiss = { activeToken = null },
			onCopy = {
				copyToClipboard(token.url)
				activeToken = null
			},
			onShare = { share(token.url) },
			onOpen = {
				uriHandler.openUri(token.url)
				activeToken = null
			},
			onEditDays = {
				activeToken = null
				editToken = token
			},
			onRevoke = {
				activeToken = null
				revokeToken = token
			}
		)
	}

	editToken?.let { token ->
		CalendarDaysDialog(
			token = token,
			onDismiss = { editToken = null },
			onSave = { days ->
				editToken = null
				scope.launch {
					viewModel.updateDays(token.token, days)
						.onFailure {
							Toast.makeText(
								context,
								context.getString(R.string.preference_timetable_calendar_error),
								Toast.LENGTH_SHORT
							).show()
						}
				}
			}
		)
	}

	revokeToken?.let { token ->
		AlertDialog(
			onDismissRequest = { revokeToken = null },
			title = { Text(stringResource(R.string.preference_timetable_calendar_revoke_title)) },
			text = { Text(stringResource(R.string.preference_timetable_calendar_revoke_desc)) },
			confirmButton = {
				TextButton(onClick = {
					revokeToken = null
					scope.launch {
						viewModel.revoke(token.token).onFailure {
							Toast.makeText(
								context,
								context.getString(R.string.preference_timetable_calendar_error),
								Toast.LENGTH_SHORT
							).show()
						}
					}
				}) {
					Text(stringResource(R.string.preference_timetable_calendar_revoke))
				}
			},
			dismissButton = {
				TextButton(onClick = { revokeToken = null }) {
					Text(stringResource(R.string.all_cancel))
				}
			}
		)
	}
}

/** A subscribable target and the mint request it maps to. */
private data class CalendarTarget(val label: String, val request: ProxyCalendarTokenRequest)

private fun calendarTargets(options: ProxyCalendarOptions): List<Pair<Int, List<CalendarTarget>>> = buildList {
	add(
		R.string.preference_timetable_calendar_personal to buildList {
			options.personal?.let {
				add(CalendarTarget(it.name, ProxyCalendarTokenRequest(personal = true)))
			}
		}
	)
	add(R.string.preference_timetable_calendar_classes to options.classes.toTargets { ProxyCalendarTokenRequest(classId = it.id) })
	add(R.string.preference_timetable_calendar_teachers to options.teacher.toTargets { ProxyCalendarTokenRequest(teacherId = it.id) })
	add(R.string.preference_timetable_calendar_rooms to options.room.toTargets { ProxyCalendarTokenRequest(roomId = it.id) })
	add(R.string.preference_timetable_calendar_subjects to options.subject.toTargets { ProxyCalendarTokenRequest(subjectId = it.id) })
	add(
		R.string.preference_timetable_calendar_technik to buildList {
			options.technik?.let {
				add(CalendarTarget(it.name, ProxyCalendarTokenRequest(classId = it.id)))
			}
		}
	)
}

private fun List<ProxyCalendarOption>.toTargets(
	request: (ProxyCalendarOption) -> ProxyCalendarTokenRequest
): List<CalendarTarget> = map { CalendarTarget(it.name, request(it)) }

@Composable
private fun CalendarTargetPickerDialog(
	options: ProxyCalendarOptions,
	onDismiss: () -> Unit,
	onPick: (CalendarTarget) -> Unit
) {
	val groups = calendarTargets(options)

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preference_timetable_calendar_add)) },
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				groups.forEach { (titleRes, targets) ->
					if (targets.isEmpty()) return@forEach
					Text(
						text = stringResource(titleRes),
						modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
					)
					targets.forEach { target ->
						ListItem(
							headlineContent = { Text(target.label) },
							modifier = Modifier.clickable { onPick(target) }
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_cancel))
			}
		}
	)
}

@Composable
private fun CalendarActionsDialog(
	token: ProxyCalendarToken,
	onDismiss: () -> Unit,
	onCopy: () -> Unit,
	onShare: () -> Unit,
	onOpen: () -> Unit,
	onEditDays: () -> Unit,
	onRevoke: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(token.name?.takeIf { it.isNotBlank() } ?: token.token) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(token.url)
			}
		},
		confirmButton = {
			Column(
				horizontalAlignment = Alignment.End,
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					TextButton(onClick = onCopy) {
						Text(stringResource(R.string.preference_timetable_calendar_copy))
					}
					TextButton(onClick = onShare) {
						Text(stringResource(R.string.preference_timetable_calendar_share))
					}
					TextButton(onClick = onOpen) {
						Text(stringResource(R.string.preference_timetable_calendar_open))
					}
				}
				Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					TextButton(onClick = onEditDays) {
						Text(stringResource(R.string.all_edit))
					}
					TextButton(onClick = onRevoke) {
						Text(stringResource(R.string.preference_timetable_calendar_revoke))
					}
				}
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_cancel))
			}
		}
	)
}

@Composable
private fun CalendarDaysDialog(
	token: ProxyCalendarToken,
	onDismiss: () -> Unit,
	onSave: (Int) -> Unit
) {
	var days by remember { mutableStateOf((token.days ?: 30).toString()) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preference_timetable_calendar_window)) },
		text = {
			OutlinedTextField(
				value = days,
				onValueChange = { days = it.filter { c -> c.isDigit() }.take(3) },
				singleLine = true
			)
		},
		confirmButton = {
			TextButton(
				enabled = (days.toIntOrNull() ?: 0) in 1..365,
				onClick = { onSave(days.toInt()) }
			) {
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

@Composable
private fun CalendarLinkDialog(
	link: String,
	onDismiss: () -> Unit,
	onCopy: () -> Unit,
	onShare: () -> Unit,
	onOpen: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preference_timetable_calendar_subscription)) },
		text = { Text(stringResource(R.string.preference_timetable_calendar_note) + "\n\n" + link) },
		confirmButton = {
			Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				TextButton(onClick = onCopy) {
					Text(stringResource(R.string.preference_timetable_calendar_copy))
				}
				TextButton(onClick = onShare) {
					Text(stringResource(R.string.preference_timetable_calendar_share))
				}
				TextButton(onClick = onOpen) {
					Text(stringResource(R.string.preference_timetable_calendar_open))
				}
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_close))
			}
		}
	)
}

@Composable
private fun typeLabel(type: String?): String = when (type) {
	"class" -> stringResource(R.string.preference_timetable_calendar_classes)
	"student" -> stringResource(R.string.preference_timetable_calendar_personal)
	"teacher" -> stringResource(R.string.preference_timetable_calendar_teachers)
	"room" -> stringResource(R.string.preference_timetable_calendar_rooms)
	"subject" -> stringResource(R.string.preference_timetable_calendar_subjects)
	"technik" -> stringResource(R.string.preference_timetable_calendar_technik)
	else -> type.orEmpty()
}
