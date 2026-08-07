package com.example.monica.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.data.CallUiStatus
import com.example.monica.media.ActiveMediaSessionRepository
import com.example.monica.media.NowPlayingUiState
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.util.ArtworkPalette
import com.example.monica.ui.util.extractArtworkPalette
import com.example.monica.ui.util.shiftHue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val StripGlassFill = Color(0x22FFFFFF)
private val StripProgressTrack = Color(0x44E8D5B0)
private val StripOnGlass = Color.White
private val StripOnGlassMuted = Color.White.copy(alpha = 0.72f)

@Composable
fun NowPlayingStripHost(
    vm: MonicaViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = remember(context) {
        ActiveMediaSessionRepository.get(context)
    }
    val nowPlaying by repo.nowPlaying.collectAsStateWithLifecycle()
    val promptVisible by repo.permissionPromptVisible.collectAsStateWithLifecycle()
    val musicDisplay by repo.musicDisplayEnabled.collectAsStateWithLifecycle()
    val callState by vm.callState.collectAsStateWithLifecycle()
    val inCall = callState.status in listOf(
        CallUiStatus.Outgoing,
        CallUiStatus.Incoming,
        CallUiStatus.Connecting,
        CallUiStatus.Active,
    )

    DisposableEffect(lifecycleOwner, repo) {
        repo.start()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repo.refreshListenerAccess()
                repo.start()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(inCall) {
        repo.setCallActive(inCall)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = promptVisible && !inCall,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            NotificationAccessPrompt(
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onOpenListenerSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onDismiss = repo::dismissPermissionPrompt,
            )
        }
        AnimatedVisibility(
            visible = musicDisplay && nowPlaying != null && !inCall,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val state = nowPlaying
            if (state != null) {
                NowPlayingStrip(
                    state = state,
                    onPrev = repo::skipPrevious,
                    onPlayPause = repo::playPause,
                    onNext = repo::skipNext,
                )
            }
        }
    }
}

@Composable
private fun NotificationAccessPrompt(
    onOpenAppSettings: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StripGlassFill,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Панель музыки",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = StripOnGlass,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Чтобы показывать текущий трек, нужен доступ к уведомлениям. " +
                    "Если Android пишет про ограниченные настройки: карточка Monica → ⋮ → " +
                    "«Разрешить ограниченные настройки», затем включите Monica в доступе к уведомлениям. " +
                    "«Позже» — включить можно в меню → Настройки → «Отображать музыку».",
                style = MaterialTheme.typography.bodySmall,
                color = StripOnGlassMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Позже")
                }
                TextButton(onClick = onOpenAppSettings) {
                    Text("О приложении")
                }
                TextButton(onClick = onOpenListenerSettings) {
                    Text("Доступ")
                }
            }
        }
    }
}

@Composable
fun NowPlayingStrip(
    state: NowPlayingUiState,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var palette by remember(state.title, state.artist) {
        mutableStateOf(ArtworkPalette.Fallback)
    }
    LaunchedEffect(state.artwork, state.title, state.artist) {
        palette = withContext(Dispatchers.Default) {
            extractArtworkPalette(state.artwork)
        }
    }

    val infinite = rememberInfiniteTransition(label = "np-shimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state.isPlaying) 2200 else 5200,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "np-shimmer-shift",
    )
    val hueSwing by infinite.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state.isPlaying) 3200 else 7000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "np-hue",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = if (state.isPlaying) 1f else 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state.isPlaying) 1400 else 2800,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "np-pulse",
    )

    val base = palette.base
    val deep = palette.deep
    val bright = palette.bright.shiftHue(if (state.isPlaying) hueSwing else hueSwing * 0.35f)
    val accent = lerp(palette.average, bright, 0.55f).shiftHue(hueSwing * 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val w = size.width
                val h = size.height
                // База из медианного/среднего тона обложки.
                drawRect(color = deep.copy(alpha = 0.92f))
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            base.copy(alpha = 0.78f * pulse),
                            deep.copy(alpha = 0.88f),
                        ),
                    ),
                )
                // Яркий переливающийся блик.
                val travel = shimmer * (w * 1.55f) - w * 0.35f
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.35f to bright.copy(alpha = if (state.isPlaying) 0.55f else 0.28f),
                            0.5f to Color.White.copy(alpha = if (state.isPlaying) 0.42f else 0.18f),
                            0.65f to accent.copy(alpha = if (state.isPlaying) 0.5f else 0.24f),
                            1.0f to Color.Transparent,
                        ),
                        start = Offset(travel, 0f),
                        end = Offset(travel + w * 0.55f, h),
                    ),
                )
                // Лёгкий диагональный цветной слой для «живости».
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            bright.copy(alpha = 0.18f * pulse),
                            Color.Transparent,
                            accent.copy(alpha = 0.22f * pulse),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h),
                    ),
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(state)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = StripOnGlass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.artist.isNotBlank()) {
                        Text(
                            text = state.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = StripOnGlassMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TransportButton(
                    enabled = state.canSkipPrev,
                    onClick = onPrev,
                    contentDescription = "Предыдущий трек",
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = null)
                }
                TransportButton(
                    enabled = state.canPlayPause,
                    onClick = onPlayPause,
                    contentDescription = if (state.isPlaying) "Пауза" else "Играть",
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                    )
                }
                TransportButton(
                    enabled = state.canSkipNext,
                    onClick = onNext,
                    contentDescription = "Следующий трек",
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = bright.copy(alpha = 0.95f),
                trackColor = StripProgressTrack,
            )
        }
    }
}

@Composable
private fun Artwork(state: NowPlayingUiState) {
    val shape = RoundedCornerShape(8.dp)
    if (state.artwork != null) {
        Image(
            bitmap = state.artwork.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(shape)
                .background(StripGlassFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = StripOnGlassMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TransportButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .then(
                Modifier.semantics {
                    this.contentDescription = contentDescription
                },
            ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) {
                StripOnGlass
            } else {
                StripOnGlass.copy(alpha = 0.38f)
            },
        ) {
            content()
        }
    }
}
