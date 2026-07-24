package com.example.monica.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG_URL = "URL"

object LinkCopyFeedback {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun show(text: String = "Ссылка скопирована") {
        _message.value = text
    }

    fun clear() {
        _message.value = null
    }
}

@Composable
fun BoxScope.LinkCopiedBanner() {
    val message by LinkCopyFeedback.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(1700)
        LinkCopyFeedback.clear()
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 10.dp)
            .zIndex(20f),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Text(
            text = message.orEmpty(),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color(0xE61B1F27), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun LinkAwareText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 16.sp, color = color),
    linkColor: Color = Color(0xFF7EB6FF),
    maxLines: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val annotated = remember(text, color, linkColor) {
        buildLinkAnnotatedString(text, color, linkColor)
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotated,
        style = style.merge(TextStyle(color = color)),
        maxLines = maxLines,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotated) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val layout = layoutResult ?: return@awaitEachGesture
                val offset = layout.getOffsetForPosition(down.position).coerceIn(0, annotated.length)
                val link = annotated.getStringAnnotations(TAG_URL, offset, offset).firstOrNull()
                    ?: return@awaitEachGesture

                down.consume()
                val up = withTimeoutOrNull(longPressTimeout) {
                    waitForUpOrCancellation()
                }
                if (up == null) {
                    copyLinkToClipboard(context, link.item)
                    waitForUpOrCancellation()
                } else {
                    openLink(uriHandler, link.item)
                }
            }
        },
    )
}

private fun buildLinkAnnotatedString(
    text: String,
    color: Color,
    linkColor: Color,
): AnnotatedString {
    if (text.isBlank()) return AnnotatedString(text)
    val matcher = Patterns.WEB_URL.matcher(text)
    return buildAnnotatedString {
        var cursor = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start < cursor) continue
            if (start > cursor) {
                withStyle(SpanStyle(color = color)) {
                    append(text.substring(cursor, start))
                }
            }
            val raw = text.substring(start, end)
            val url = normalizeUrl(raw)
            pushStringAnnotation(TAG_URL, url)
            withStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(raw)
            }
            pop()
            cursor = end
        }
        if (cursor < text.length) {
            withStyle(SpanStyle(color = color)) {
                append(text.substring(cursor))
            }
        }
    }
}

private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

private fun openLink(uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
}

fun copyLinkToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
    LinkCopyFeedback.show("Ссылка скопирована")
}
