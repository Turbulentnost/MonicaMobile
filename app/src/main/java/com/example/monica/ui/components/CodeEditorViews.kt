package com.example.monica.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val VS_DARK_BG = "#1E1E1E"
private const val VS_LIGHT_BG = "#FFFFFF"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoEditorView(
    value: String,
    language: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = true,
    readOnly: Boolean = false,
    onValueChange: (String) -> Unit = {},
    onSubmit: (() -> Unit)? = null,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    val themeKey = if (darkTheme) "dark" else "light"
    val bgColor = if (darkTheme) VS_DARK_BG else VS_LIGHT_BG

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(value, ready) {
        if (!ready) return@LaunchedEffect
        webView?.evaluateJavascript("setValue(${JSONObject.quote(value)});", null)
    }

    LaunchedEffect(language, ready) {
        if (!ready) return@LaunchedEffect
        val lang = if (language == "javascript") "javascript" else "python"
        webView?.evaluateJavascript("setLanguage(${JSONObject.quote(lang)});", null)
    }

    LaunchedEffect(themeKey, ready) {
        if (!ready) return@LaunchedEffect
        webView?.setBackgroundColor(Color.parseColor(bgColor))
        webView?.evaluateJavascript("setTheme(${JSONObject.quote(themeKey)});", null)
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.parseColor(bgColor))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.allowFileAccess = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onReady() {
                            post { ready = true }
                        }

                        @JavascriptInterface
                        fun onChange(text: String) {
                            post { onValueChange(text) }
                        }

                        @JavascriptInterface
                        fun onSubmit(text: String) {
                            post {
                                onValueChange(text)
                                onSubmit?.invoke()
                            }
                        }
                    },
                    "MonicaBridge",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        ready = true
                    }
                }
                val lang = if (language == "javascript") "javascript" else "python"
                val ro = if (readOnly) "1" else "0"
                loadUrl(
                    "file:///android_asset/monaco/editor.html?lang=$lang&readonly=$ro&theme=$themeKey",
                )
                webView = this
            }
        },
        update = { view ->
            view.setBackgroundColor(Color.parseColor(bgColor))
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeViewerView(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = true,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var heightDp by remember { mutableIntStateOf(160) }
    val themeKey = if (darkTheme) "dark" else "light"
    val bgColor = if (darkTheme) VS_DARK_BG else VS_LIGHT_BG

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(code, language, ready) {
        if (!ready) return@LaunchedEffect
        val lang = if (language == "javascript") "javascript" else "python"
        webView?.evaluateJavascript(
            "setCode(${JSONObject.quote(code)}, ${JSONObject.quote(lang)});",
            null,
        )
    }

    LaunchedEffect(themeKey, ready) {
        if (!ready) return@LaunchedEffect
        webView?.setBackgroundColor(Color.parseColor(bgColor))
        webView?.evaluateJavascript("setTheme(${JSONObject.quote(themeKey)});", null)
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(Color.parseColor(bgColor))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                isVerticalScrollBarEnabled = true
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onReady() {
                            post { ready = true }
                        }

                        @JavascriptInterface
                        fun onHeight(h: Int) {
                            post { heightDp = h.coerceIn(80, 300) }
                        }
                    },
                    "MonicaBridge",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        ready = true
                    }
                }
                loadUrl("file:///android_asset/monaco/viewer.html?theme=$themeKey")
                webView = this
            }
        },
        update = { view ->
            view.setBackgroundColor(Color.parseColor(bgColor))
        },
    )
}
