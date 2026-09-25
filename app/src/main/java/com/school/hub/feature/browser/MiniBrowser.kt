package com.school.hub.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

private const val HOME = "https://ya.ru/"

private fun toUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains('.') && !t.contains(' ') -> "https://$t"
        else -> "https://ya.ru/search/?text=" + URLEncoder.encode(t, "UTF-8")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniBrowserScreen(onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf(HOME) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }

    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onClose() }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть") }
            OutlinedTextField(
                address, { address = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Поиск или адрес") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { web?.loadUrl(toUrl(address)) }),
            )
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth())
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme ?: return true
                            return scheme != "http" && scheme != "https" // intent:// и прочее не открываем
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { address = url }
                        override fun onPageFinished(view: WebView, url: String) {
                            address = url; canBack = view.canGoBack(); canForward = view.canGoForward()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    loadUrl(HOME)
                    web = this
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        BottomAppBar {
            IconButton(onClick = { web?.goBack() }, enabled = canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            IconButton(onClick = { web?.goForward() }, enabled = canForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Вперёд") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { web?.loadUrl(HOME) }) { Icon(Icons.Filled.Home, "Яндекс") }
            IconButton(onClick = { web?.reload() }) { Icon(Icons.Filled.Refresh, "Обновить") }
        }
    }
}
