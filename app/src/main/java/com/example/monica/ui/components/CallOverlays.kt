package com.example.monica.ui.components

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.monica.data.CallAudioRoute
import com.example.monica.data.CallUiState
import com.example.monica.data.CallUiStatus
import com.example.monica.data.call.CallController
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallHost(
    state: CallUiState,
    callController: CallController,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onCycleAudioRoute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onUpgradeToVideo: () -> Unit,
) {
    when (state.status) {
        CallUiStatus.Incoming -> IncomingCallOverlay(
            nickname = state.partner?.nickname ?: "пользователь",
            isVideo = state.isVideo,
            onAccept = onAccept,
            onReject = onReject,
        )
        CallUiStatus.Outgoing,
        CallUiStatus.Connecting,
        CallUiStatus.Active,
        -> ActiveCallScreen(
            state = state,
            callController = callController,
            onEnd = {
                when (state.status) {
                    CallUiStatus.Outgoing -> onCancel()
                    else -> onHangup()
                }
            },
            onToggleMute = onToggleMute,
            onCycleAudioRoute = onCycleAudioRoute,
            onToggleCamera = onToggleCamera,
            onSwitchCamera = onSwitchCamera,
            onUpgradeToVideo = onUpgradeToVideo,
        )
        else -> Unit
    }
}

@Composable
private fun IncomingCallOverlay(
    nickname: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6121418)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                if (isVideo) "Входящий видеозвонок" else "Входящий аудиозвонок",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB8C0CC),
            )
            Text(
                "@$nickname",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallRoundButton(
                    color = Color(0xFFE53935),
                    onClick = onReject,
                    contentDescription = "Отклонить",
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                }
                CallRoundButton(
                    color = Color(0xFF43A047),
                    onClick = onAccept,
                    contentDescription = "Принять",
                ) {
                    Icon(
                        if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCallScreen(
    state: CallUiState,
    callController: CallController,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onCycleAudioRoute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onUpgradeToVideo: () -> Unit,
) {
    val statusText = when (state.status) {
        CallUiStatus.Outgoing -> "Вызов…"
        CallUiStatus.Connecting -> "Соединение…"
        CallUiStatus.Active -> formatCallDuration(state.elapsedSeconds)
        else -> ""
    }
    val showVideoStage = state.isVideo
    val egl = callController.eglBaseContext()
    // В активном видеозвонке нижняя панель прячется через 3с; тап по экрану — снова показать.
    val autoHideControls = showVideoStage && state.status == CallUiStatus.Active
    var controlsVisible by remember(autoHideControls) { mutableStateOf(true) }
    var controlsEpoch by remember { mutableIntStateOf(0) }

    fun bumpControls() {
        controlsVisible = true
        controlsEpoch += 1
    }

    LaunchedEffect(autoHideControls, controlsVisible, controlsEpoch) {
        if (!autoHideControls) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (!controlsVisible) return@LaunchedEffect
        delay(3_000)
        controlsVisible = false
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // Видеозвонок: экран не гаснет по таймауту бездействия.
            .then(if (state.isVideo) Modifier.keepScreenOn() else Modifier),
        color = Color(0xFF121418),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (autoHideControls) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { bumpControls() },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (showVideoStage && egl != null) {
                VideoCallStage(
                    eglContext = egl,
                    callController = callController,
                    videoEpoch = state.videoEpoch,
                    cameraEnabled = state.cameraEnabled,
                    usingFrontCamera = state.usingFrontCamera,
                    canSwitchCamera = state.canSwitchCamera,
                    hasRemoteVideo = state.hasRemoteVideo,
                    partnerNickname = state.partner?.nickname,
                    onSwitchCamera = {
                        bumpControls()
                        onSwitchCamera()
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (state.isVideo) "Видеозвонок" else "Аудиозвонок",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFB8C0CC),
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    UserAvatar(state.partner, size = 96.dp, showOnline = false)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "@${state.partner?.nickname ?: "пользователь"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF9AA3B2),
                    )
                }
            }

            AnimatedVisibility(
                visible = !autoHideControls || controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    animationSpec = tween(220),
                    initialOffsetY = { it },
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    animationSpec = tween(220),
                    targetOffsetY = { it },
                ) + fadeOut(animationSpec = tween(160)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC0E1014))
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { bumpControls() },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showVideoStage) {
                        Text(
                            "@${state.partner?.nickname ?: "пользователь"} · $statusText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD0D5DD),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CallRoundButton(
                            color = Color(0xFF2A2F38),
                            onClick = {
                                bumpControls()
                                onToggleMute()
                            },
                            contentDescription = if (state.muted) "Включить микрофон" else "Выключить микрофон",
                            size = 56.dp,
                        ) {
                            Icon(
                                if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                        CallRoundButton(
                            color = if (state.cameraEnabled) Color(0xFF2A2F38) else Color(0xFF3A3030),
                            onClick = {
                                bumpControls()
                                onToggleCamera()
                            },
                            contentDescription = if (state.cameraEnabled) "Выключить камеру" else "Включить камеру",
                            size = 56.dp,
                        ) {
                            Icon(
                                if (state.cameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                        CallRoundButton(
                            color = Color(0xFFE53935),
                            onClick = onEnd,
                            contentDescription = "Завершить",
                            size = 68.dp,
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                        }
                        CallRoundButton(
                            color = Color(0xFF2A2F38),
                            onClick = {
                                bumpControls()
                                onCycleAudioRoute()
                            },
                            contentDescription = audioRouteLabel(state.audioRoute),
                            size = 56.dp,
                        ) {
                            Icon(
                                audioRouteIcon(state.audioRoute),
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                    }
                    if (!state.isVideo) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onUpgradeToVideo) {
                            Icon(
                                Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Color(0xFFB8C0CC),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Видео", color = Color(0xFFB8C0CC))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            audioRouteLabel(state.audioRoute),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8A93A3),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCallStage(
    eglContext: EglBase.Context,
    callController: CallController,
    videoEpoch: Int,
    cameraEnabled: Boolean,
    usingFrontCamera: Boolean,
    canSwitchCamera: Boolean,
    hasRemoteVideo: Boolean,
    partnerNickname: String?,
    onSwitchCamera: () -> Unit,
) {
    // true = собеседник на весь экран, мы в PiP; false = наоборот.
    var remoteIsPrimary by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Основной (полноэкранный) поток.
        if (remoteIsPrimary) {
            key("remote-primary-$videoEpoch") {
                WebRtcVideo(
                    eglContext = eglContext,
                    mirror = false,
                    zOrderMediaOverlay = false,
                    modifier = Modifier.fillMaxSize(),
                    onBind = { callController.attachRemoteSink(it) },
                    onUnbind = { callController.detachRemoteSink(it) },
                )
            }
            if (!hasRemoteVideo) {
                CameraOffPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    title = "@${partnerNickname ?: "пользователь"}",
                    subtitle = "У пользователя отключена камера",
                )
            }
        } else {
            key("local-primary-$videoEpoch-$cameraEnabled-$usingFrontCamera") {
                if (cameraEnabled) {
                    WebRtcVideo(
                        eglContext = eglContext,
                        mirror = usingFrontCamera,
                        zOrderMediaOverlay = false,
                        modifier = Modifier.fillMaxSize(),
                        onBind = { callController.attachLocalSink(it) },
                        onUnbind = { callController.detachLocalSink(it) },
                    )
                } else {
                    CameraOffPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        title = "Вы",
                        subtitle = "Камера выключена",
                    )
                }
            }
            if (cameraEnabled && canSwitchCamera) {
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(40.dp)
                        .semantics { contentDescription = "Переключить камеру" },
                ) {
                    Icon(
                        Icons.Filled.Cameraswitch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // Маленькое окошко — тап меняет местами с основным.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 150.dp)
                .size(width = 110.dp, height = 160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF232833))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { remoteIsPrimary = !remoteIsPrimary },
                ),
        ) {
            if (remoteIsPrimary) {
                key("local-pip-$videoEpoch-$cameraEnabled-$usingFrontCamera") {
                    if (cameraEnabled) {
                        WebRtcVideo(
                            eglContext = eglContext,
                            mirror = usingFrontCamera,
                            zOrderMediaOverlay = true,
                            modifier = Modifier.fillMaxSize(),
                            onBind = { callController.attachLocalSink(it) },
                            onUnbind = { callController.detachLocalSink(it) },
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Камера выкл.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB8C0CC),
                            )
                        }
                    }
                }
                if (cameraEnabled && canSwitchCamera) {
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .semantics { contentDescription = "Переключить камеру" },
                    ) {
                        Icon(
                            Icons.Filled.Cameraswitch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                key("remote-pip-$videoEpoch") {
                    WebRtcVideo(
                        eglContext = eglContext,
                        mirror = false,
                        zOrderMediaOverlay = true,
                        modifier = Modifier.fillMaxSize(),
                        onBind = { callController.attachRemoteSink(it) },
                        onUnbind = { callController.detachRemoteSink(it) },
                    )
                }
                if (!hasRemoteVideo) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Нет видео",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB8C0CC),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraOffPlaceholder(
    modifier: Modifier,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = modifier.background(Color(0xE61A1E26)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, color = Color(0xFF9AA3B2))
        }
    }
}

@Composable
private fun WebRtcVideo(
    eglContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier,
    zOrderMediaOverlay: Boolean = false,
    onBind: (SurfaceViewRenderer) -> Unit,
    onUnbind: (SurfaceViewRenderer) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                init(eglContext, null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(zOrderMediaOverlay)
                onBind(this)
            }
        },
        update = { renderer ->
            renderer.setMirror(mirror)
            renderer.setZOrderMediaOverlay(zOrderMediaOverlay)
        },
        onRelease = { renderer ->
            onUnbind(renderer)
            runCatching {
                renderer.clearImage()
                renderer.release()
            }
        },
    )
}

@Composable
private fun CallRoundButton(
    color: Color,
    onClick: () -> Unit,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .semantics { this.contentDescription = contentDescription },
    ) {
        content()
    }
}

private fun audioRouteIcon(route: CallAudioRoute) = when (route) {
    CallAudioRoute.Earpiece -> Icons.Filled.Hearing
    CallAudioRoute.Speaker -> Icons.Filled.VolumeUp
    CallAudioRoute.Bluetooth -> Icons.Filled.Bluetooth
}

private fun audioRouteLabel(route: CallAudioRoute) = when (route) {
    CallAudioRoute.Earpiece -> "Динамик у уха"
    CallAudioRoute.Speaker -> "Громкая связь"
    CallAudioRoute.Bluetooth -> "Bluetooth"
}

fun formatCallDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
