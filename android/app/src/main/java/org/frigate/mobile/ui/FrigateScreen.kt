package org.frigate.mobile.ui

import android.graphics.Bitmap
import android.content.Context
import android.net.http.SslError
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.frigate.mobile.R
import org.frigate.mobile.data.normalizeServerUrl

private const val MAX_LOAD_RETRIES = 3
private const val RETRY_DELAY_MS = 4000L
private const val PULL_TO_REFRESH_THRESHOLD_DP = 160f
private const val PULL_TO_REFRESH_MAX_OFFSET_DP = 80f

private class PullToRefreshWebView(
    context: Context,
    private val canRefresh: () -> Boolean,
    private val onPullOffsetChanged: (Float) -> Unit,
    private val onRefresh: () -> Unit,
) : WebView(context) {
    private val refreshThreshold =
        PULL_TO_REFRESH_THRESHOLD_DP * context.resources.displayMetrics.density
    private val maxPullOffset =
        PULL_TO_REFRESH_MAX_OFFSET_DP * context.resources.displayMetrics.density
    private var lastTouchY = 0f
    private var lastObservedTouchY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var pullDistance = 0f
    private var pullGestureEligible = false
    private var pointerDown = false
    private var multiTouchSeen = false
    private var gestureId = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            lastTouchY = event.y
            lastObservedTouchY = event.y
            touchStartX = event.x
            touchStartY = event.y
            pullDistance = 0f
            pointerDown = true
            multiTouchSeen = false
            pullGestureEligible = false
            val currentGestureId = ++gestureId
            if (canRefresh()) {
                val cssScale = resources.displayMetrics.density
                val cssX = event.x / cssScale
                val cssY = event.y / cssScale
                evaluateJavascript(
                    """
                    (function(x, y) {
                        var element = document.elementFromPoint(x, y);
                        while (element && element !== document.documentElement) {
                            var style = window.getComputedStyle(element);
                            var isScrollable =
                                /(auto|scroll|overlay)/.test(style.overflowY) &&
                                element.scrollHeight > element.clientHeight;
                            if (isScrollable && element.scrollTop > 1) return false;
                            element = element.parentElement;
                        }
                        var root = document.scrollingElement;
                        return !root || root.scrollTop <= 1;
                    })($cssX, $cssY);
                    """.trimIndent(),
                ) { result ->
                    if (
                        currentGestureId == gestureId &&
                        pointerDown &&
                        !multiTouchSeen &&
                        canRefresh()
                    ) {
                        pullGestureEligible = result == "true"
                        lastTouchY = lastObservedTouchY
                    }
                }
            }
        }

        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            // Pinch zoom and other multi-finger gestures must never refresh
            multiTouchSeen = true
            finishPullToRefresh()
        }

        val handled = super.onTouchEvent(event)

        if (multiTouchSeen) {
            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                pointerDown = false
            }
            return handled
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val touchDeltaY = event.y - lastTouchY
                val verticalPull = event.y - touchStartY
                val horizontalPull = event.x - touchStartX
                if (
                    pullGestureEligible &&
                    canRefresh() &&
                    touchDeltaY > 0f &&
                    verticalPull > kotlin.math.abs(horizontalPull)
                ) {
                    pullDistance += touchDeltaY
                } else if (touchDeltaY < 0f && pullDistance > 0f) {
                    pullDistance = (pullDistance + touchDeltaY).coerceAtLeast(0f)
                } else if (!pullGestureEligible || !canRefresh()) {
                    pullDistance = 0f
                    pullGestureEligible = false
                }

                updatePullOffset()
                lastTouchY = event.y
                lastObservedTouchY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val verticalPull = event.y - touchStartY
                val horizontalPull = event.x - touchStartX
                if (
                    pullGestureEligible &&
                    canRefresh() &&
                    pullDistance >= refreshThreshold &&
                    verticalPull > kotlin.math.abs(horizontalPull)
                ) {
                    translationY = maxPullOffset
                    onPullOffsetChanged(maxPullOffset)
                    onRefresh()
                    reload()
                    pullGestureEligible = false
                } else {
                    finishPullToRefresh()
                }
                pointerDown = false
            }

            MotionEvent.ACTION_CANCEL -> {
                pointerDown = false
                gestureId++
                finishPullToRefresh()
            }
        }

        return handled
    }

    fun finishPullToRefresh() {
        pullDistance = 0f
        pullGestureEligible = false
        translationY = 0f
        onPullOffsetChanged(0f)
    }

    private fun updatePullOffset() {
        val offset = (pullDistance * 0.5f).coerceAtMost(maxPullOffset)
        translationY = offset
        onPullOffsetChanged(offset)
    }
}

@Composable
fun FrigateScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("frigate", 0) }
    var serverUrl by rememberSaveable {
        mutableStateOf(preferences.getString("server_url", "").orEmpty())
    }
    var activeUrl by rememberSaveable {
        mutableStateOf(serverUrl.takeIf(String::isNotBlank))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var isPullToRefreshing by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var retryAttempts by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    if (activeUrl == null) {
        ConnectScreen(
            serverUrl = serverUrl,
            error = error,
            onServerUrlChange = {
                serverUrl = it
                error = null
            },
            onConnect = {
                try {
                    val normalizedUrl = normalizeServerUrl(serverUrl)
                    preferences.edit().putString("server_url", normalizedUrl).apply()
                    serverUrl = normalizedUrl
                    activeUrl = normalizedUrl
                    error = null
                } catch (exception: IllegalArgumentException) {
                    error = exception.message ?: context.getString(R.string.invalid_server_url)
                }
            },
        )
    } else {
        Scaffold(
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PullToRefreshWebView(
                            context = viewContext,
                            canRefresh = { !isLoading },
                            onPullOffsetChanged = { pullOffsetPx = it },
                            onRefresh = {
                                pageError = null
                                isPullToRefreshing = true
                            },
                        ).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            isLoading = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    isLoading = true
                                    loadFailed = false
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    isLoading = false
                                    isPullToRefreshing = false
                                    (view as? PullToRefreshWebView)?.finishPullToRefresh()
                                    if (!loadFailed) retryAttempts = 0
                                    canGoBack = view.canGoBack()
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    loadError: WebResourceError,
                                ) {
                                    if (!request.isForMainFrame) return
                                    loadFailed = true
                                    if (retryAttempts < MAX_LOAD_RETRIES) {
                                        retryAttempts++
                                        isLoading = true
                                        val failedUrl = request.url.toString()
                                        view.postDelayed({
                                            if (view.isAttachedToWindow) view.loadUrl(failedUrl)
                                        }, RETRY_DELAY_MS)
                                    } else {
                                        retryAttempts = 0
                                        isLoading = false
                                        isPullToRefreshing = false
                                        (view as? PullToRefreshWebView)?.finishPullToRefresh()
                                        error = context.getString(R.string.connection_failed)
                                        activeUrl = null
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    response: WebResourceResponse,
                                ) {
                                    if (request.isForMainFrame && response.statusCode >= 400) {
                                        isLoading = false
                                        pageError = context.getString(
                                            R.string.server_http_error,
                                            response.statusCode,
                                            request.url,
                                        )
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView,
                                    handler: SslErrorHandler,
                                    error: SslError,
                                ) {
                                    handler.cancel()
                                    isLoading = false
                                    isPullToRefreshing = false
                                    (view as? PullToRefreshWebView)?.finishPullToRefresh()
                                    pageError = context.getString(
                                        R.string.ssl_error,
                                        error.primaryError,
                                    )
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView,
                                    url: String?,
                                    isReload: Boolean,
                                ) {
                                    canGoBack = view.canGoBack()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    isLoading = newProgress < 100
                                }

                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                                        message.message().contains("Uncaught") &&
                                        pageError == null
                                    ) {
                                        pageError = context.getString(
                                            R.string.web_page_error,
                                            message.message(),
                                        )
                                    }
                                    return false
                                }
                            }
                            webView = this
                            loadUrl(activeUrl.orEmpty())
                        }
                    },
                    update = {},
                    onRelease = { releasedWebView ->
                        releasedWebView.stopLoading()
                        releasedWebView.destroy()
                        if (webView === releasedWebView) {
                            webView = null
                            canGoBack = false
                        }
                    },
                )
                if (retryAttempts > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(
                                R.string.retrying,
                                retryAttempts,
                                MAX_LOAD_RETRIES,
                            ),
                        )
                    }
                } else if (isLoading && !isPullToRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                    )
                }
                if (isPullToRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                    )
                } else if (pullOffsetPx > 0f) {
                    val indicatorOffset = with(LocalDensity.current) {
                        (pullOffsetPx - 20.dp.toPx()).coerceAtLeast(0f).toDp()
                    }
                    val pullProgress = (
                        pullOffsetPx /
                            with(LocalDensity.current) { PULL_TO_REFRESH_MAX_OFFSET_DP.dp.toPx() }
                        ).coerceIn(0f, 1f)
                    CircularProgressIndicator(
                        progress = { pullProgress },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = indicatorOffset)
                            .size(40.dp),
                    )
                }
                if (pageError != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.page_load_failed_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(pageError.orEmpty())
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                pageError = null
                                isLoading = true
                                webView?.reload()
                            },
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                pageError = null
                                isLoading = false
                                activeUrl = null
                            },
                        ) {
                            Text(stringResource(R.string.change_server))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen(
    serverUrl: String,
    error: String?,
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.connect_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onConnect() }),
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_frigate))
        }
    }
}
