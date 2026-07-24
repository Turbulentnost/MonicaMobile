package com.example.monica.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
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
import androidx.compose.runtime.key
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

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // Видеозвонок: экран не гаснет по таймауту бездействия.
            .then(if (state.isVideo) Modifier.keepScreenOn() else Modifier),
        color = Color(0xFF121418),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    onSwitchCamera = onSwitchCamera,
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC0E1014))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
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
                        onClick = onToggleMute,
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
                        onClick = onToggleCamera,
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
                        onClick = onCycleAudioRoute,
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
    Box(modifier = Modifier.fillMaxSize()) {
        // Всегда вешаем remote sink — иначе видео звонящего не появится у абонента без камеры.
        key("remote-$videoEpoch") {
            WebRtcVideo(
                eglContext = eglContext,
                mirror = false,
                modifier = Modifier.fillMaxSize(),
                onBind = { callController.attachRemoteSink(it) },
                onUnbind = { callController.detachRemoteSink(it) },
            )
        }
        if (!hasRemoteVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE61A1E26)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "@${partnerNickname ?: "пользователь"}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "У пользователя отключена камера",
                        color = Color(0xFF9AA3B2),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 150.dp)
                .size(width = 110.dp, height = 160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF232833)),
        ) {
            key("local-$videoEpoch-$cameraEnabled-$usingFrontCamera") {
                if (cameraEnabled) {
                    WebRtcVideo(
                        eglContext = eglContext,
                        mirror = usingFrontCamera,
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
        }
    }
}

@Composable
private fun WebRtcVideo(
    eglContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier,
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
                onBind(this)
            }
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
