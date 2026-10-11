package com.deelabs.untisplus.ui.pages.settings.fragments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOption
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarPerson

/** One subscribable target, carrying the caller's request payload [T]. */
data class SubscriptionTarget<T>(val label: String, val payload: T)

/** A group of targets the user picks from, e.g. all classes. */
data class SubscriptionCategory<T>(
	val titleRes: Int,
	val targets: List<SubscriptionTarget<T>>,
	val searchable: Boolean
)

/**
 * Builds the pickable categories from a proxy options catalog
 * (`/api/calendar/options` or `/api/notifications/options`), mapping each
 * element onto the caller's request payload [T]. Empty groups are omitted and
 * single-entry groups (personal, Technik) are not searchable.
 */
fun <T> subscriptionCategories(
	options: ProxyCalendarOptions,
	personal: (ProxyCalendarPerson) -> T,
	classTarget: (ProxyCalendarOption) -> T,
	teacher: (ProxyCalendarOption) -> T,
	room: (ProxyCalendarOption) -> T,
	subject: (ProxyCalendarOption) -> T,
	technik: (ProxyCalendarOption) -> T
): List<SubscriptionCategory<T>> = buildList {
	options.personal?.let {
		add(
			SubscriptionCategory(
				R.string.preference_timetable_calendar_personal,
				listOf(SubscriptionTarget(it.name, personal(it))),
				searchable = false
			)
		)
	}
	options.classes.toCategory(R.string.preference_timetable_calendar_classes) { SubscriptionTarget(it.name, classTarget(it)) }
		?.let(::add)
	options.teacher.toCategory(R.string.preference_timetable_calendar_teachers) { SubscriptionTarget(it.name, teacher(it)) }
		?.let(::add)
	options.room.toCategory(R.string.preference_timetable_calendar_rooms) { SubscriptionTarget(it.name, room(it)) }
		?.let(::add)
	options.subject.toCategory(R.string.preference_timetable_calendar_subjects) { SubscriptionTarget(it.name, subject(it)) }
		?.let(::add)
	options.technik?.let {
		add(
			SubscriptionCategory(
				R.string.preference_timetable_calendar_technik,
				listOf(SubscriptionTarget(it.name, technik(it))),
				searchable = false
			)
		)
	}
}

private fun <T> List<ProxyCalendarOption>.toCategory(
	titleRes: Int,
	target: (ProxyCalendarOption) -> SubscriptionTarget<T>
): SubscriptionCategory<T>? =
	if (isEmpty()) null
	else SubscriptionCategory(titleRes, map(target), searchable = true)

@Composable
fun <T> SubscriptionCategoryPickerDialog(
	categories: List<SubscriptionCategory<T>>,
	titleRes: Int,
	onDismiss: () -> Unit,
	onPick: (SubscriptionCategory<T>) -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(titleRes)) },
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				categories.forEach { category ->
					ListItem(
						headlineContent = { Text(stringResource(category.titleRes)) },
						modifier = Modifier.clickable { onPick(category) }
					)
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
fun <T> SubscriptionTargetListDialog(
	category: SubscriptionCategory<T>,
	onDismiss: () -> Unit,
	onPick: (T) -> Unit
) {
	var query by remember { mutableStateOf("") }
	val filtered = remember(category, query) {
		if (query.isBlank()) category.targets
		else category.targets.filter { it.label.contains(query.trim(), ignoreCase = true) }
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(category.titleRes)) },
		text = {
			Column {
				if (category.searchable) {
					OutlinedTextField(
						value = query,
						onValueChange = { query = it },
						singleLine = true,
						placeholder = { Text(stringResource(R.string.preference_timetable_calendar_search)) },
						modifier = Modifier
							.fillMaxWidth()
							.padding(bottom = 8.dp)
					)
				}
				if (filtered.isEmpty()) {
					Text(
						text = stringResource(R.string.preference_timetable_calendar_no_results),
						modifier = Modifier.padding(16.dp)
					)
				} else {
					LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
						items(filtered) { target ->
							ListItem(
								headlineContent = { Text(target.label) },
								modifier = Modifier.clickable { onPick(target.payload) }
							)
						}
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
