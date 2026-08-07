package com.example.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.monica.data.AppUpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val BANNER_AUTO_HIDE_MS = 4_000L
private const val SWIPE_DISMISS_PX = 72f

@Composable
fun BoxScope.UpdateAvailableBanner(
    update: AppUpdateInfo?,
    visible: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val show = visible && update != null
    val offsetY = remember(update?.versionCode) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(show, update?.versionCode) {
        if (!show) {
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }
        offsetY.snapTo(0f)
        delay(BANNER_AUTO_HIDE_MS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = show,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(start = 12.dp, top = 10.dp, end = 12.dp)
            .zIndex(18f)
            .offset { IntOffset(0, offsetY.value.roundToInt()) },
        enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { -it },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(260)) { -it },
    ) {
        if (update != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(update.versionCode) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY.value <= -SWIPE_DISMISS_PX) {
                                    scope.launch {
                                        offsetY.animateTo(-600f, tween(220))
                                        onDismiss()
                                    }
                                } else {
                                    scope.launch {
                                        offsetY.animateTo(0f, tween(180))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offsetY.animateTo(0f, tween(180))
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                // Смахивание только вверх.
                                val next = (offsetY.value + dragAmount).coerceAtMost(0f)
                                scope.launch { offsetY.snapTo(next) }
                            },
                        )
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Доступна версия ${update.versionName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Можно обновить Monica прямо сейчас.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onUpdate) {
                            Text("Обновить")
                        }
                    }
                }
            }
        }
    }
}

/** Тонкий прогресс скачивания APK над системным футером. */
@Composable
fun BoxScope.UpdateDownloadProgressBar(
    progress: Float?,
    onCancel: () -> Unit,
) {
    AnimatedVisibility(
        visible = progress != null,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .zIndex(17f),
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
    ) {
        val value = progress ?: 0f
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Скачивание обновления… ${(value * 100).toInt().coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Отменить обновление",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { value.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .height(4.dp),
                )
            }
        }
    }
}
