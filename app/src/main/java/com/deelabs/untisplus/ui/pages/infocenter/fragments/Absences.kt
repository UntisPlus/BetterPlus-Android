package com.deelabs.untisplus.ui.pages.infocenter.fragments

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.data.database.entities.ExcuseStatusEntity
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InfoCenterAbsences(
	uiState: AbsencesUiState,
	proxyAvailable: Boolean = false,
	onSetNote: (StudentAbsence, String) -> Unit = { _, _ -> }
) {
	var editing by remember { mutableStateOf<StudentAbsence?>(null) }

	Crossfade(targetState = uiState, label = "InfoCenter Absences Content") { state ->
		when (state) {
			AbsencesUiState.Loading -> InfoCenterLoading()
			is AbsencesUiState.Success -> {
				state.absences.fold(
					onSuccess = {
						if (state.isEmpty) {
							Text(
								text = stringResource(R.string.infocenter_absences_empty),
								textAlign = TextAlign.Center,
								modifier = Modifier.fillMaxWidth()
							)
						} else {
							LazyColumn(
								horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()
							) {
								items(it) { absence ->
									AbsenceItem(
										item = absence,
										excuseStatuses = state.excuseStatuses,
										proxyAvailable = proxyAvailable,
										onEditNote = { editing = absence }
									)
								}
							}
						}
					},
					onFailure = { InfoCenterError(it) }
				)
			}
		}
	}

	editing?.let { absence ->
		AbsenceNoteDialog(
			absence = absence,
			onDismiss = { editing = null },
			onSave = { note ->
				onSetNote(absence, note)
				editing = null
			}
		)
	}
}

@Composable
private fun AbsenceItem(
	item: StudentAbsence,
	excuseStatuses: List<ExcuseStatusEntity>,
	proxyAvailable: Boolean,
	onEditNote: () -> Unit
) {
	ListItem(
		modifier = if (proxyAvailable) Modifier.clickable(onClick = onEditNote) else Modifier,
		headlineContent = {
			Text(item.startDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)))
		},
		supportingContent = {
			Column {
				// Time range
				Text(
					stringResource(
						R.string.infocenter_absences_timerange,
						item.startDateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
						item.endDateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
					)
				)

				// Reason
				Text(
					stringResource(
						R.string.infocenter_absence_reason,
						if (item.absenceReason.isNotEmpty())
							item.absenceReason.replaceFirstChar { it.uppercase() }
						else
							stringResource(R.string.infocenter_absence_reason_unknown)
					)
				)

				// Additional Text
				if (item.text.isNotBlank()) {
					Text(item.text)
				}

				// Private note (injected by the untis-proxy)
				if (!item.note.isNullOrBlank()) {
					Text(stringResource(R.string.infocenter_absence_note) + ": " + item.note)
				}

				// Excuse status
				item.excuse?.let { excuse -> excuseStatuses.find { it.id == excuse.excuseStatusId } }?.let {
					Text(
						stringResource(
							if (it.excused) R.string.infocenter_absence_excused_status
							else R.string.infocenter_absence_unexcused_status,
							it.longName
						)
					)
				} ?: Text(
					if (item.excused)
						stringResource(R.string.infocenter_absence_excused)
					else
						stringResource(R.string.infocenter_absence_unexcused)
				)
			}
		}
	)
}

@Composable
private fun AbsenceNoteDialog(
	absence: StudentAbsence,
	onDismiss: () -> Unit,
	onSave: (String) -> Unit
) {
	var text by remember(absence.id) { mutableStateOf(absence.note.orEmpty()) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.infocenter_absence_note)) },
		text = {
			OutlinedTextField(
				value = text,
				onValueChange = { text = it },
				label = { Text(stringResource(R.string.infocenter_absence_note_hint)) },
				minLines = 3,
				modifier = Modifier.fillMaxWidth()
			)
		},
		confirmButton = {
			TextButton(onClick = { onSave(text) }) {
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

sealed interface AbsencesUiState {
	data object Loading : AbsencesUiState

	data class Success(
		val absences: Result<List<StudentAbsence>>,
		val excuseStatuses: List<ExcuseStatusEntity>
	) : AbsencesUiState {

		val isEmpty: Boolean get() = absences.getOrDefault(emptyList()).isEmpty()
	}
}
