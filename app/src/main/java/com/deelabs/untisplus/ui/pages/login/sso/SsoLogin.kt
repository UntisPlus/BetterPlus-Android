package com.deelabs.untisplus.ui.pages.login.sso

import android.graphics.Bitmap
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.deelabs.untisplus.R
import com.deelabs.untisplus.ui.common.AppScaffold
import kotlinx.coroutines.delay
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SsoLogin(
	viewModel: SsoLoginViewModel = hiltViewModel()
) {
	val context = LocalContext.current

	val webView = remember {
		WebView(context).apply {
			// WebView only renders the visible region by default; on very large surfaces the
			// GPU compositor can skip tiles ("some content may not draw"). Force whole-document
			// drawing so the full page always renders.
			WebView.enableSlowWholeDocumentDraw()
			clearCache(true)
			settings.javaScriptEnabled = true
			settings.domStorageEnabled = true
			settings.useWideViewPort = true
			settings.setSupportZoom(false)
			settings.setBuiltInZoomControls(false)
			CookieManager.getInstance().setAcceptCookie(true)
			CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
			webChromeClient = object : WebChromeClient() {
				override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
					Log.d("SsoWebView", "${consoleMessage?.messageLevel()}: ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
					return true
				}
			}
			webViewClient = object : WebViewClient() {
				override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
					super.onPageStarted(view, url, favicon)
					viewModel.onPageStarted()
				}

				override fun shouldInterceptRequest(
					view: WebView?,
					request: WebResourceRequest?
				): WebResourceResponse? {
					val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
					// Univention (UCS) SSO pages serve some CSS with a broken MIME type
					// (application/json), which WebView's strict MIME checking then blocks,
					// leaving the login page unstyled. Re-serve only those stylesheets with a
					// correct type. Scoped to UCS hosts so we never touch the normal (already
					// correct) assets of IServ, Keycloak, Microsoft, etc.
					if (url.endsWith(".css", ignoreCase = true)) {
						val host = request.url?.host.orEmpty()
						if (host.contains("ucs-sso") || host.contains("schule-mv") || host.contains("univention")) {
							try {
								val conn = (URL(url).openConnection() as HttpURLConnection).apply {
									connectTimeout = 10000
									readTimeout = 15000
									for ((k, v) in request.requestHeaders) setRequestProperty(k, v)
								}
								return WebResourceResponse("text/css", "utf-8", conn.inputStream)
							} catch (e: Exception) {
								Log.w("SsoWebView", "CSS MIME fix failed: $url", e)
							}
						}
					}
					return super.shouldInterceptRequest(view, request)
				}

				override fun onPageFinished(view: WebView?, url: String?) {
					super.onPageFinished(view, url)
					CookieManager.getInstance().flush()
					viewModel.onPageFinished()
				}

				override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
					super.doUpdateVisitedHistory(view, url, isReload)
					viewModel.onWebViewEvent()
				}
			}
		}
	}

	AppScaffold(
		topBar = {
			CenterAlignedTopAppBar(
				title = { Text(stringResource(id = R.string.ssologin_title)) },
				navigationIcon = {
					IconButton(onClick = { viewModel.goBack() }) {
						Icon(
							imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
							contentDescription = stringResource(id = R.string.all_back)
						)
					}
				}
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize()
		) {
			AnimatedVisibility(visible = viewModel.pageLoading) {
				LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
			}

			AndroidView(
				factory = { webView },
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f, fill = true)
			)
			OutlinedButton(
				onClick = {
					context.startActivity(
						Intent(Intent.ACTION_VIEW, viewModel.browserUrl().toUri())
					)
					viewModel.showCapture()
				},
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 4.dp)
			) {
				Text(stringResource(id = R.string.ssologin_open_in_browser))
			}

			SsoStatusBar(viewModel)
		}
	}

	if (viewModel.showCaptureDialog) {
		CaptureLoginDialog(viewModel)
	}

	LaunchedEffect(viewModel.webTargetUrl) {
		viewModel.webTargetUrl?.let { webView.loadUrl(it) }
	}

	LaunchedEffect(Unit) {
		viewModel.detectAndPrepare()
		while (true) {
			viewModel.onWebViewEvent()
			delay(2_000)
		}
	}

	DisposableEffect(Unit) {
		onDispose {
			webView.destroy()
		}
	}
}

@Composable
private fun SsoStatusBar(viewModel: SsoLoginViewModel) {
	if (viewModel.ssoNotAvailable) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp)
		) {
			Text(
				text = stringResource(id = R.string.ssologin_unavailable),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			TextButton(onClick = { viewModel.openWebLogin() }) {
				Text(stringResource(id = R.string.ssologin_open_web_login))
			}
		}
	} else if (viewModel.harvestError != null) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp)
		) {
			Text(
				text = stringResource(id = viewModel.harvestError!!),
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodyMedium
			)
			TextButton(onClick = { viewModel.retryHarvest() }) {
				Text(stringResource(id = R.string.ssologin_retry))
			}
		}
	} else if (viewModel.waitingForLogin && viewModel.webTargetUrl != null) {
		Text(
			text = stringResource(id = R.string.ssologin_waiting),
			style = MaterialTheme.typography.bodySmall,
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 8.dp)
		)
	}
}

@Composable
private fun CaptureLoginDialog(viewModel: SsoLoginViewModel) {
	var username by remember { mutableStateOf(viewModel.captureUsername) }
	var key by remember { mutableStateOf("") }
	var showHow by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = { viewModel.dismissCapture() },
		title = { Text(stringResource(id = R.string.ssologin_capture_title)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(stringResource(id = R.string.ssologin_capture_message))
				OutlinedTextField(
					value = username,
					onValueChange = { username = it },
					label = { Text(stringResource(id = R.string.ssologin_capture_username_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = key,
					onValueChange = { key = it },
					label = { Text(stringResource(id = R.string.ssologin_capture_key_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				TextButton(onClick = { showHow = true }) {
					Text(stringResource(id = R.string.ssologin_capture_how))
				}
			}
		},
		confirmButton = {
			Button(
				onClick = { viewModel.submitCapture(username, key) },
				enabled = username.isNotBlank() && key.isNotBlank()
			) {
				Text(stringResource(id = R.string.ssologin_capture_complete))
			}
		},
		dismissButton = {
			TextButton(onClick = { viewModel.dismissCapture() }) {
				Text(stringResource(id = R.string.all_cancel))
			}
		}
	)

	if (showHow) {
		AlertDialog(
			onDismissRequest = { showHow = false },
			title = { Text(stringResource(id = R.string.ssologin_capture_how)) },
			text = { Text(stringResource(id = R.string.ssologin_capture_how_body)) },
			confirmButton = {
				TextButton(onClick = { showHow = false }) {
					Text(stringResource(id = R.string.all_close))
				}
			}
		)
	}
}
