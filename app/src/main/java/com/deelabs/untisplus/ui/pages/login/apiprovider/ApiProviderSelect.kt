package com.deelabs.untisplus.ui.pages.login.apiprovider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deelabs.untisplus.R
import com.deelabs.untisplus.ui.common.AppScaffold
import com.deelabs.untisplus.ui.common.SmallCircularProgressIndicator
import com.deelabs.untisplus.ui.functional.bottomInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiProviderSelect(viewModel: ApiProviderSelectViewModel = hiltViewModel()) {
	AppScaffold(
		floatingActionButtonPosition = FabPosition.End,
		floatingActionButton = {
			ExtendedFloatingActionButton(
				modifier = Modifier.bottomInsets(),
				containerColor = MaterialTheme.colorScheme.primary,
				icon = {
					if (viewModel.saving)
						SmallCircularProgressIndicator(color = LocalContentColor.current)
					else
						Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
				},
				text = { Text(stringResource(id = R.string.apiprovider_continue)) },
				onClick = { viewModel.onContinue() }
			)
		},
		topBar = {
			CenterAlignedTopAppBar(
				title = { Text(stringResource(id = R.string.apiprovider_title)) }
			)
		}
	) { innerPadding ->
		val isHttpCustom = viewModel.isHttpCustom

		Column(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize()
		) {
			ProviderRow(
				selected = viewModel.selection == ApiProviderSelectViewModel.Provider.OFFICIAL,
				title = stringResource(id = R.string.logindatainput_api_official),
				subtitle = stringResource(id = R.string.logindatainput_api_official_desc),
				onClick = { viewModel.selection = ApiProviderSelectViewModel.Provider.OFFICIAL }
			)
			ProviderRow(
				selected = viewModel.selection == ApiProviderSelectViewModel.Provider.UNTISPLUS,
				title = stringResource(id = R.string.logindatainput_api_untisplus),
				subtitle = stringResource(id = R.string.logindatainput_api_untisplus_desc),
				onClick = { viewModel.selection = ApiProviderSelectViewModel.Provider.UNTISPLUS }
			)
			ProviderRow(
				selected = viewModel.selection == ApiProviderSelectViewModel.Provider.CUSTOM,
				title = stringResource(id = R.string.apiprovider_custom),
				subtitle = stringResource(id = R.string.apiprovider_custom_desc),
				onClick = { viewModel.selection = ApiProviderSelectViewModel.Provider.CUSTOM }
			)

			if (viewModel.selection == ApiProviderSelectViewModel.Provider.CUSTOM) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 8.dp)
				) {
					OutlinedTextField(
						value = viewModel.customUrl,
						onValueChange = { viewModel.customUrl = it },
						singleLine = true,
						keyboardOptions = KeyboardOptions(
							keyboardType = KeyboardType.Uri
						),
						label = { Text(stringResource(id = R.string.apiprovider_custom_url)) },
						isError = !viewModel.customUrlValid,
						modifier = Modifier.fillMaxWidth()
					)
					if (!viewModel.customUrlValid) {
						Text(
							text = stringResource(id = R.string.logindatainput_error_invalid_url),
							color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.bodySmall,
							modifier = Modifier.padding(top = 4.dp)
						)
					}
					if (isHttpCustom) {
						Text(
							text = stringResource(id = R.string.apiprovider_http_warning),
							color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.bodyMedium,
							modifier = Modifier.padding(top = 12.dp)
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ProviderRow(
	selected: Boolean,
	title: String,
	subtitle: String,
	onClick: () -> Unit
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		RadioButton(selected = selected, onClick = null)
		Column(
			modifier = Modifier.padding(start = 8.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
