package com.cybercat.ebooksender.feature.manga

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cybercat.ebooksender.data.manga.MangaNativeLoginConfig
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.ui.AnimatedAlertDialog
import com.cybercat.ebooksender.ui.AppOutlinedTextField
import com.cybercat.ebooksender.ui.LocalDismissDialog
import com.cybercat.ebooksender.ui.LocalDismissDialogAfter
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed
import org.json.JSONArray

internal const val HTML_EXTRACT_DELAY_MILLIS = 900L

@Composable
internal fun MangaBrowserCard(
    url: String,
    currentUrl: String?,
    sourceHomeUrl: String,
    userAgent: String?,
    loginUrl: String?,
    nativeLoginConfig: MangaNativeLoginConfig?,
    pendingLoginPost: MangaPendingLoginPost?,
    showNativeLoginOnStart: Boolean,
    enableHaptics: Boolean,
    onClose: () -> Unit,
    onWebPageLoaded: (String, String) -> Unit,
    onNativeLoginSubmit: (
        targetUrl: String,
        username: String,
        password: String,
        doNotRemember: Boolean
    ) -> Unit,
    onNativeLoginStartConsumed: () -> Unit,
    onLoginPostExecuted: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingLoginPost, webViewRef) {
        val loginPost = pendingLoginPost
        val webView = webViewRef
        if (loginPost != null && webView != null) {
            webView.postUrl(loginPost.url, loginPost.postBody)
            onLoginPostExecuted()
        }
    }

    LaunchedEffect(showNativeLoginOnStart, nativeLoginConfig) {
        if (showNativeLoginOnStart && nativeLoginConfig != null) {
            showLoginDialog = true
            onNativeLoginStartConsumed()
        }
    }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val webView = webViewRef ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.resumeBrowser()

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> webView.pauseBrowser()

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.pauseBrowser()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentUrl ?: url,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (nativeLoginConfig != null) {
                        TextButton(onClick = {
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                AppHapticFeedback.Press
                            )
                            showLoginDialog = true
                        }) {
                            Text(strings.mangaBtnLogin)
                        }
                    } else if (!loginUrl.isNullOrBlank()) {
                        TextButton(onClick = {
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                AppHapticFeedback.Press
                            )
                            webViewRef?.loadUrl(loginUrl)
                        }) {
                            Text(strings.mangaBtnLogin)
                        }
                    }
                    IconButton(onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            AppHapticFeedback.Press
                        )
                        onClose()
                    }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = strings.get("manga_action_close_browser")
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            userAgent?.takeIf { it.isNotBlank() }?.let { value ->
                                settings.userAgentString = value
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, loadedUrl: String) {
                                    super.onPageFinished(view, loadedUrl)
                                    CookieManager.getInstance().flush()

                                    view.postDelayed(
                                        {
                                            if (webViewRef === view) {
                                                view.extractHtml { html ->
                                                    onWebPageLoaded(loadedUrl, html)
                                                }
                                            }
                                        },
                                        HTML_EXTRACT_DELAY_MILLIS
                                    )
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        if (url.isNotBlank() && webView.url != url) {
                            webView.loadUrl(url)
                        }
                    },
                    onRelease = { webView ->
                        if (webViewRef === webView) {
                            webViewRef = null
                        }
                        webView.releaseBrowser()
                    }
                )
            }
        }
    }

    if (showLoginDialog && nativeLoginConfig != null) {
        MangaNativeLoginDialog(
            config = nativeLoginConfig,
            onDismiss = { showLoginDialog = false },
            onSubmit = { loginName, loginPassword, doNotRemember ->
                val targetUrl = webViewRef?.url
                    ?.takeIf { loadedUrl -> loadedUrl.startsWith(sourceHomeUrl) }
                    ?: nativeLoginConfig.loginUrl
                onNativeLoginSubmit(targetUrl, loginName, loginPassword, doNotRemember)
                showLoginDialog = false
            }
        )
    }
}

@Composable
internal fun MangaLoginMethodDialog(
    enableHaptics: Boolean,
    onDismiss: () -> Unit,
    onUseWebView: (rememberChoice: Boolean) -> Unit,
    onUseNativeForm: (rememberChoice: Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    var rememberChoice by remember { mutableStateOf(false) }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val dismiss = LocalDismissDialog.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.get("manga_login_method_title"),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        AppHapticFeedback.Press
                    )
                    dismiss()
                }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = strings.get("action_close")
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.get("manga_login_method_body"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { checked ->
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                AppHapticFeedback.Press
                            )
                            rememberChoice = checked
                        }
                    )
                    Text(strings.get("manga_login_method_remember"))
                }
            }
        },
        dismissButton = {
            val dismissAfter = LocalDismissDialogAfter.current
            TextButton(onClick = {
                view.performHapticIfAllowed(
                    context,
                    enableHaptics,
                    AppHapticFeedback.Press
                )
                dismissAfter { onUseWebView(rememberChoice) }
            }) {
                Text(strings.get("manga_login_method_webview"))
            }
        },
        confirmButton = {
            val dismissAfter = LocalDismissDialogAfter.current
            Button(onClick = {
                view.performHapticIfAllowed(
                    context,
                    enableHaptics,
                    AppHapticFeedback.Confirm
                )
                dismissAfter { onUseNativeForm(rememberChoice) }
            }) {
                Text(strings.get("manga_login_method_native"))
            }
        }
    )
}

@Composable
internal fun MangaNativeLoginDialog(
    config: MangaNativeLoginConfig,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit
) {
    var loginName by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var doNotRemember by remember { mutableStateOf(false) }
    val canSubmit = loginName.isNotBlank() && loginPassword.isNotBlank()

    val strings = LocalStrings.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.mangaLoginTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                AppOutlinedTextField(
                    value = loginName,
                    onValueChange = { loginName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.mangaUsername) }
                )
                AppOutlinedTextField(
                    value = loginPassword,
                    onValueChange = { loginPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.mangaPassword) },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (config.showDoNotRemember) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = doNotRemember,
                            onCheckedChange = { doNotRemember = it }
                        )
                        Text(strings.mangaDoNotRemember)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.opdsBtnCancel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(loginName.trim(), loginPassword, doNotRemember) },
                        enabled = canSubmit
                    ) {
                        Text(strings.mangaBtnLogin)
                    }
                }
            }
        }
    }
}

internal fun WebView.extractHtml(onHtml: (String) -> Unit) {
    evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { encoded ->
        val html = runCatching {
            JSONArray("[$encoded]").getString(0)
        }.getOrDefault("")
        if (html.isNotBlank()) {
            onHtml(html)
        }
    }
}

private fun WebView.pauseBrowser() {
    onPause()
    pauseTimers()
}

private fun WebView.resumeBrowser() {
    resumeTimers()
    onResume()
}

private fun WebView.releaseBrowser() {
    runCatching { stopLoading() }
    runCatching { loadUrl("about:blank") }
    webViewClient = WebViewClient()
    pauseBrowser()
    removeAllViews()
    destroy()
}
