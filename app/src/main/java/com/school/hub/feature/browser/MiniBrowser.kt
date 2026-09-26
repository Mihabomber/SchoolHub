package com.school.hub.feature.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

private const val HOME = "https://ya.ru/"
private const val SEARCH = "https://duckduckgo.com/html/?q="

private fun toUrl(input: String): String {
    val text = input.trim()
    return when {
        text.startsWith("http://") || text.startsWith("https://") -> text
        text.contains('.') && !text.contains(' ') -> "https://$text"
        else -> SEARCH + URLEncoder.encode(text, "UTF-8")
    }
}

private fun isWebUrl(uri: Uri): Boolean = uri.scheme == "http" || uri.scheme == "https"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniBrowserScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var address by rememberSaveable { mutableStateOf(HOME) }
    var title by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var hasError by rememberSaveable { mutableStateOf(false) }
    val savedState = rememberSaveable { Bundle() }

    fun updateNavigation(view: WebView) {
        canBack = view.canGoBack()
        canForward = view.canGoForward()
    }

    fun openExternal(uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            hasError = true
        }
    }

    fun loadInput(input: String) {
        if (input.isBlank()) return
        val uri = Uri.parse(toUrl(input))
        if (isWebUrl(uri)) {
            hasError = false
            web?.loadUrl(uri.toString())
        } else {
            openExternal(uri)
        }
    }

    BackHandler {
        if (web?.canGoBack() == true) web?.goBack() else onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            web?.let { view ->
                view.saveState(savedState)
                view.stopLoading()
                view.removeAllViews()
                view.destroy()
            }
            web = null
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = if (title.isNotBlank()) ({ Text(title, maxLines = 1) }) else null,
                placeholder = { Text("Поиск или адрес") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { loadInput(address) })
            )
        }

        if (loading) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }

        if (hasError) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Нет соединения", style = MaterialTheme.typography.headlineSmall)
                Text("Страница не открылась", modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { hasError = false; web?.reload() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Повторить")
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                return if (isWebUrl(uri)) false else { openExternal(uri); true }
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                address = url
                                hasError = false
                                loading = true
                                updateNavigation(view)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                address = url
                                loading = false
                                progress = 100
                                updateNavigation(view)
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    loading = false
                                    hasError = true
                                }
                            }

                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                handler.cancel()
                                loading = false
                                hasError = true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                                loading = newProgress < 100
                                updateNavigation(view)
                            }

                            override fun onReceivedTitle(view: WebView, pageTitle: String?) {
                                title = pageTitle.orEmpty()
                            }
                        }

                        web = this
                        if (savedState.isEmpty) loadUrl(HOME) else restoreState(savedState)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        BottomAppBar {
            IconButton(onClick = { web?.goBack() }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            IconButton(onClick = { web?.goForward() }, enabled = canForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Вперёд")
            }
            Spacer(Modifier.weight(1f))
            if (loading) {
                IconButton(onClick = { web?.stopLoading(); loading = false }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Остановить")
                }
            } else {
                IconButton(onClick = { hasError = false; web?.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                }
            }
        }
    }
}
