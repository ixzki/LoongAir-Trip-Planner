package com.loongair.tripplanner.ui.account

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import java.net.URI
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType

private val ALLOWED_YIDUN_HOST_SUFFIXES = listOf(
    "126.net",
    "163.com",
    "netease.com",
    "ydstatic.com"
)

internal fun isAllowedCaptchaUrl(rawUrl: String?): Boolean {
    if (rawUrl.isNullOrBlank()) return false
    val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme == "file") {
        val path = uri.path.orEmpty()
        return path.startsWith("/android_asset/") && !path.contains("..")
    }
    if (scheme == "https") {
        val host = uri.host?.lowercase().orEmpty()
        return ALLOWED_YIDUN_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }
    return false
}

@Keep
private class YidunJsBridge(
    private val webViewProvider: () -> WebView?,
    private val onSuccessCallback: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit
) {
    @JavascriptInterface
    fun onSuccess(validate: String) {
        webViewProvider()?.post { onSuccessCallback(validate) }
    }

    @JavascriptInterface
    fun onError(msg: String) {
        webViewProvider()?.post { onErrorCallback(msg) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun YidunCaptchaDialog(
    onDismiss: () -> Unit,
    onValidated: (String) -> Unit,
    onError: (String) -> Unit
) {
    val p = LocalLoongPalette.current
    val currentOnValidated by rememberUpdatedState(onValidated)
    val currentOnError by rememberUpdatedState(onError)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                removeJavascriptInterface("YidunBridge")
                webChromeClient = null
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = p.cardBg,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "完成官方安全验证",
                    style = LoongType.headline,
                    color = p.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.WHITE)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    // 仅允许本地 asset 入口与易盾官方 HTTPS 域名，拦截任何外部跳转
                                    return !isAllowedCaptchaUrl(request?.url?.toString())
                                }
                            }
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(
                                YidunJsBridge(
                                    webViewProvider = { webViewRef },
                                    onSuccessCallback = { currentOnValidated(it) },
                                    onErrorCallback = { currentOnError(it) }
                                ),
                                "YidunBridge"
                            )
                            loadUrl("file:///android_asset/yidun_captcha.html")
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", style = LoongType.filter)
                    }
                }
            }
        }
    }
}
