package com.deelabs.untisplus.ui.pages.settings.fragments

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sapuseven.compose.protostore.ui.preferences.PreferenceGroup
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyTopic
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTarget
import com.deelabs.untisplus.api.model.proxy.ProxyWebhook
import com.deelabs.untisplus.ui.pages.settings.ChangeSubscriptionsViewModel
import kotlinx.coroutines.launch

private enum class SubscriptionKind { Ntfy, Webhook }

@Composable
fun SettingsCategoryChangeSubscriptions(viewModel: ChangeSubscriptionsViewModel = hiltViewModel()) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	val options by viewModel.options.collectAsStateWithLifecycle()
	val webhooks by viewModel.webhooks.collectAsStateWithLifecycle()
	val topics by viewModel.topics.collectAsStateWithLifecycle()
	val loading by viewModel.loading.collectAsStateWithLifecycle()
	val failed by viewModel.failed.collectAsStateWithLifecycle()

	var pickerKind by remember { mutableStateOf<SubscriptionKind?>(null) }
	var pickerCategory by remember { mutableStateOf<SubscriptionCategory<ProxySubscriptionTarget>?>(null) }
	var webhookTarget by remember { mutableStateOf<ProxySubscriptionTarget?>(null) }
	var ntfyTarget by remember { mutableStateOf<ProxySubscriptionTarget?>(null) }
	var webhookActions by remember { mutableStateOf<ProxyWebhook?>(null) }
	var topicActions by remember { mutableStateOf<ProxyNtfyTopic?>(null) }
	var webhookDelete by remember { mutableStateOf<ProxyWebhook?>(null) }
	var topicDelete by remember { mutableStateOf<ProxyNtfyTopic?>(null) }

	fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

	fun chooseTarget(kind: SubscriptionKind, target: ProxySubscriptionTarget) {
		when (kind) {
			SubscriptionKind.Webhook -> webhookTarget = target
			SubscriptionKind.Ntfy -> ntfyTarget = target
		}
	}

	fun openPicker(kind: SubscriptionKind) {
		pickerCategory = null
		pickerKind = kind
	}

	if (options != null) {
		if (pickerKind != null && pickerCategory == null) {
			SubscriptionCategoryPickerDialog(
				categories = notificationTargets(options!!),
				titleRes = R.string.preference_notifications_subscriptions_add,
				onDismiss = { pickerKind = null },
				onPick = { category ->
					val kind = pickerKind ?: return@SubscriptionCategoryPickerDialog
					if (category.targets.size == 1) {
						chooseTarget(kind, category.targets.first().payload)
						pickerKind = null
					} else {
						pickerCategory = category
					}
				}
			)
		}

		pickerCategory?.let { category ->
			SubscriptionTargetListDialog(
				category = category,
				onDismiss = { pickerCategory = null },
				onPick = { target ->
					pickerCategory = null
					pickerKind?.let { chooseTarget(it, target) }
					pickerKind = null
				}
			)
		}
	}

	PreferenceGroup(stringResource(R.string.preference_notifications_ntfy)) {
		when {
			loading && topics.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_notifications_subscriptions_none)) },
				supportingContent = { Text(stringResource(R.string.preference_proxy_status_loading)) }
			)

			failed && topics.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_notifications_subscriptions_load_error)) },
				modifier = Modifier.clickable { viewModel.refresh() }
			)

			topics.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_notifications_subscriptions_none)) }
			)

			else -> topics.forEach { topic ->
				ListItem(
					headlineContent = { Text(targetLabel(topic.name, topic.elementId)) },
					supportingContent = { Text(topic.topic + (topic.baseUrl?.let { " · $it" } ?: "")) },
					modifier = Modifier.clickable { topicActions = topic }
				)
			}
		}

		ListItem(
			headlineContent = { Text(stringResource(R.string.preference_notifications_ntfy_add)) },
			modifier = Modifier
				.clickable(enabled = options != null) { openPicker(SubscriptionKind.Ntfy) }
				.padding(start = 16.dp)
		)
	}

	PreferenceGroup(stringResource(R.string.preference_notifications_webhooks)) {
		when {
			loading && webhooks.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_notifications_subscriptions_none)) },
				supportingContent = { Text(stringResource(R.string.preference_proxy_status_loading)) }
			)

			failed && webhooks.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_notifications_subscriptions_load_error)) },
				modifier = Modifier.clickable { viewModel.refresh() }
			)

			webhooks.isEmpty() -> ListItem(
				headlineContent = { Text(stringResource(R.string.preference_notifications_subscriptions_none)) }
			)

			else -> webhooks.forEach { webhook ->
				ListItem(
					headlineContent = { Text(targetLabel(webhook.name, webhook.elementId)) },
					supportingContent = { Text(webhook.url) },
					modifier = Modifier.clickable { webhookActions = webhook }
				)
			}
		}

		ListItem(
			headlineContent = { Text(stringResource(R.string.preference_notifications_webhook_add)) },
			modifier = Modifier
				.clickable(enabled = options != null) { openPicker(SubscriptionKind.Webhook) }
				.padding(start = 16.dp)
		)
	}

	webhookTarget?.let { target ->
		WebhookConfigDialog(
			onDismiss = { webhookTarget = null },
			onSave = { url, secret ->
				webhookTarget = null
				scope.launch {
					viewModel.createWebhook(target, url, secret).onFailure {
						toast(context.getString(R.string.preference_notifications_subscriptions_error))
					}
				}
			}
		)
	}

	ntfyTarget?.let { target ->
		NtfyConfigDialog(
			onDismiss = { ntfyTarget = null },
			onSave = { topic, baseUrl ->
				ntfyTarget = null
				scope.launch {
					viewModel.createNtfy(target, topic, baseUrl).onFailure {
						toast(context.getString(R.string.preference_notifications_subscriptions_error))
					}
				}
			}
		)
	}

	webhookActions?.let { webhook ->
		SubscriptionActionsDialog(
			title = targetLabel(webhook.name, webhook.elementId),
			detail = webhook.url,
			onDismiss = { webhookActions = null },
			onTest = {
				scope.launch {
					viewModel.testWebhook(webhook.id)
						.onSuccess {
							toast(context.getString(if (it.ok) R.string.preference_notifications_subscriptions_test_ok else R.string.preference_notifications_subscriptions_test_failed))
						}
						.onFailure {
							toast(context.getString(R.string.preference_notifications_subscriptions_test_failed))
						}
				}
			},
			onDelete = {
				webhookActions = null
				webhookDelete = webhook
			}
		)
	}

	topicActions?.let { topic ->
		SubscriptionActionsDialog(
			title = targetLabel(topic.name, topic.elementId),
			detail = topic.topic + (topic.baseUrl?.let { " · $it" } ?: ""),
			onDismiss = { topicActions = null },
			onTest = {
				scope.launch {
					viewModel.testNtfy(topic.id)
						.onSuccess {
							toast(context.getString(if (it.ok) R.string.preference_notifications_subscriptions_test_ok else R.string.preference_notifications_subscriptions_test_failed))
						}
						.onFailure {
							toast(context.getString(R.string.preference_notifications_subscriptions_test_failed))
						}
				}
			},
			onDelete = {
				topicActions = null
				topicDelete = topic
			}
		)
	}

	webhookDelete?.let { webhook ->
		DeleteDialog(
			onDismiss = { webhookDelete = null },
			onConfirm = {
				webhookDelete = null
				scope.launch {
					viewModel.deleteWebhook(webhook.id).onFailure {
						toast(context.getString(R.string.preference_notifications_subscriptions_error))
					}
				}
			}
		)
	}

	topicDelete?.let { topic ->
		DeleteDialog(
			onDismiss = { topicDelete = null },
			onConfirm = {
				topicDelete = null
				scope.launch {
					viewModel.deleteNtfy(topic.id).onFailure {
						toast(context.getString(R.string.preference_notifications_subscriptions_error))
					}
				}
			}
		)
	}
}

/** The notification targets the active account may watch (no Technik). */
private fun notificationTargets(options: ProxyCalendarOptions): List<SubscriptionCategory<ProxySubscriptionTarget>> =
	subscriptionCategories(
		options,
		personal = { ProxySubscriptionTarget(personal = true) },
		classTarget = { ProxySubscriptionTarget(classId = it.id) },
		teacher = { ProxySubscriptionTarget(teacherId = it.id) },
		room = { ProxySubscriptionTarget(roomId = it.id) },
		subject = { ProxySubscriptionTarget(subjectId = it.id) },
		technik = { ProxySubscriptionTarget(classId = it.id) }
	)

private fun targetLabel(name: String?, elementId: Long?): String =
	name?.takeIf { it.isNotBlank() } ?: elementId?.toString().orEmpty()

@Composable
private fun WebhookConfigDialog(
	onDismiss: () -> Unit,
	onSave: (url: String, secret: String?) -> Unit
) {
	var url by remember { mutableStateOf("") }
	var secret by remember { mutableStateOf("") }
	val valid = url.startsWith("http://") || url.startsWith("https://")

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preference_notifications_webhook_add)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = url,
					onValueChange = { url = it.trim() },
					singleLine = true,
					label = { Text(stringResource(R.string.preference_notifications_subscriptions_url)) }
				)
				OutlinedTextField(
					value = secret,
					onValueChange = { secret = it },
					singleLine = true,
					label = { Text(stringResource(R.string.preference_notifications_subscriptions_secret)) }
				)
			}
		},
		confirmButton = {
			TextButton(enabled = valid, onClick = { onSave(url, secret.ifBlank { null }) }) {
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
private fun NtfyConfigDialog(
	onDismiss: () -> Unit,
	onSave: (topic: String, baseUrl: String?) -> Unit
) {
	var topic by remember { mutableStateOf("") }
	var baseUrl by remember { mutableStateOf("") }
	val valid = topic.isNotBlank()

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preference_notifications_ntfy_add)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = topic,
					onValueChange = { topic = it.trim() },
					singleLine = true,
					label = { Text(stringResource(R.string.preference_notifications_subscriptions_topic)) }
				)
				OutlinedTextField(
					value = baseUrl,
					onValueChange = { baseUrl = it.trim() },
					singleLine = true,
					label = { Text(stringResource(R.string.preference_notifications_subscriptions_base_url)) }
				)
			}
		},
		confirmButton = {
			TextButton(enabled = valid, onClick = { onSave(topic, baseUrl.ifBlank { null }) }) {
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
private fun SubscriptionActionsDialog(
	title: String,
	detail: String,
	onDismiss: () -> Unit,
	onTest: () -> Unit,
	onDelete: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(detail)
				Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					TextButton(onClick = onTest) {
						Text(stringResource(R.string.preference_notifications_subscriptions_test))
					}
					TextButton(
						onClick = onDelete,
						colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
					) {
						Text(stringResource(R.string.all_delete))
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_close))
			}
		}
	)
}

@Composable
private fun DeleteDialog(
	onDismiss: () -> Unit,
	onConfirm: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preference_notifications_subscription_delete_title)) },
		text = { Text(stringResource(R.string.preference_notifications_subscription_delete_desc)) },
		confirmButton = {
			TextButton(
				onClick = onConfirm,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
			) {
				Text(stringResource(R.string.all_delete))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_cancel))
			}
		}
	)
}
