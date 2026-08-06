package com.example.monica.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.example.monica.data.MediaUrls

enum class VideoPlaybackQuality(val label: String, val maxHeight: Int) {
    Auto("Авто", Int.MAX_VALUE),
    P480("480p", 480),
    P720("720p", 720),
    Original("Оригинал", Int.MAX_VALUE),
}

@Composable
fun VideoPlayerDialog(
    mediaUri: Uri? = null,
    apiBaseUrl: String,
    fileName: String?,
    objectPath: String?,
    contentUrl: String?,
    mimeType: String?,
    onDismiss: () -> Unit,
    onDownload: (path: String?, url: String?, name: String, mime: String?) -> Unit,
) {
    val playUrl = mediaUri?.toString()
        ?: MediaUrls.resolve(apiBaseUrl, contentUrl)
        ?: MediaUrls.proxyUrl(apiBaseUrl, objectPath)
    if (playUrl.isNullOrBlank()) return

    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(VideoPlaybackQuality.Auto) }

    val trackSelector = remember { DefaultTrackSelector(context) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
    }

    DisposableEffect(playUrl) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(playUrl)))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(quality) {
        val maxH = quality.maxHeight
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, maxH)
            .build()
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = true
                        controllerShowTimeoutMs = 2500
                        this.player = player
                        setOnClickListener {
                            if (qualityMenuOpen) {
                                qualityMenuOpen = false
                            } else {
                                qualityMenuOpen = true
                            }
                        }
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName ?: "Видео",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = "Качество: ${quality.label}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Box {
                    IconButton(onClick = { qualityMenuOpen = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Качество", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = qualityMenuOpen,
                        onDismissRequest = { qualityMenuOpen = false },
                    ) {
                        VideoPlaybackQuality.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (option == quality) "✓ ${option.label}" else option.label,
                                    )
                                },
                                onClick = {
                                    quality = option
                                    qualityMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "Ещё", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Скачать") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onDownload(
                                    objectPath,
                                    contentUrl,
                                    fileName ?: "video.mp4",
                                    mimeType ?: "video/mp4",
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
