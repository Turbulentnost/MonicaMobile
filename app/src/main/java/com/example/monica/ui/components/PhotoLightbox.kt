package com.example.monica.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.monica.data.MessageAttachment
import com.example.monica.data.MessageItem
import com.example.monica.data.MonicaApi

data class PhotoViewerItem(
    val path: String? = null,
    val contentUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val localPreviewPath: String? = null,
    val uploadProgress: Float? = null,
) {
    val hasSource: Boolean
        get() = !path.isNullOrBlank() ||
            !contentUrl.isNullOrBlank() ||
            !localPreviewPath.isNullOrBlank()
}

fun MessageItem.toPhotoViewerItems(): List<PhotoViewerItem> {
    val fromAttachments = attachments.mapNotNull { it.toPhotoViewerItem() }
    if (fromAttachments.isNotEmpty()) return fromAttachments
    val fallback = PhotoViewerItem(
        path = content.takeIf { it.isNotBlank() },
        contentUrl = contentUrl,
        fileName = fileName ?: "photo.jpg",
        mimeType = mimeType ?: "image/jpeg",
        localPreviewPath = localPreviewPath,
        uploadProgress = uploadProgress,
    )
    return if (fallback.hasSource) listOf(fallback) else emptyList()
}

fun MessageAttachment.toPhotoViewerItem(): PhotoViewerItem? {
    val item = PhotoViewerItem(
        path = path?.takeIf { it.isNotBlank() },
        contentUrl = contentUrl?.takeIf { it.isNotBlank() },
        fileName = fileName ?: "photo.jpg",
        mimeType = mimeType ?: "image/jpeg",
    )
    return item.takeIf { it.hasSource }
}

/** Telegram-подобные ряды для 1–10 фото в пузыре. */
fun photoGalleryRowSizes(count: Int): List<Int> {
    val n = count.coerceIn(0, 10)
    return when (n) {
        0 -> emptyList()
        1 -> listOf(1)
        2 -> listOf(2)
        3 -> listOf(3)
        4 -> listOf(2, 2)
        5 -> listOf(2, 3)
        6 -> listOf(3, 3)
        7 -> listOf(3, 4)
        8 -> listOf(4, 4)
        9 -> listOf(4, 5)
        else -> listOf(5, 5)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagePhotoGallery(
    message: MessageItem,
    api: MonicaApi,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    onOpen: (index: Int) -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val photos = remember(message.id, message.attachments, message.content, message.contentUrl, message.localPreviewPath) {
        message.toPhotoViewerItems().take(10)
    }
    if (photos.isEmpty()) return

    val rows = remember(photos.size) { photoGalleryRowSizes(photos.size) }
    var offset = 0
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rows.forEach { cols ->
            val slice = photos.subList(offset, (offset + cols).coerceAtMost(photos.size))
            val startIndex = offset
            offset += slice.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                slice.forEachIndexed { colIdx, photo ->
                    val absoluteIndex = startIndex + colIdx
                    CachedMediaImage(
                        objectPath = photo.path,
                        contentUrl = photo.contentUrl,
                        fileName = photo.fileName,
                        api = api,
                        localPreviewPath = photo.localPreviewPath,
                        uploadProgress = if (absoluteIndex == 0) photo.uploadProgress else null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (photos.size == 1) {
                                    Modifier.height(180.dp)
                                } else {
                                    Modifier.aspectRatio(1f)
                                },
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onOpen(absoluteIndex) },
                                onLongClick = onLongPress,
                            ),
                    )
                }
            }
        }
        message.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Spacer(Modifier.height(4.dp))
            Text(
                caption,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun PhotoLightbox(
    items: List<PhotoViewerItem>,
    initialIndex: Int,
    api: MonicaApi,
    onDismiss: () -> Unit,
    onDownload: (path: String?, url: String?, name: String, mime: String?) -> Unit,
) {
    if (items.isEmpty()) return
    val startIndex = initialIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { items.size },
    )
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(startIndex, items.size) {
        if (pagerState.currentPage != startIndex) {
            pagerState.scrollToPage(startIndex)
        }
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
                .background(Color.Black.copy(alpha = 0.94f)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page ->
                    val item = items[page]
                    item.path ?: item.contentUrl ?: item.localPreviewPath ?: "photo-$page"
                },
            ) { page ->
                val item = items[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CachedMediaImage(
                        objectPath = item.path,
                        contentUrl = item.contentUrl,
                        fileName = item.fileName,
                        api = api,
                        localPreviewPath = item.localPreviewPath,
                        uploadProgress = item.uploadProgress,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 56.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { /* absorb — не закрывать по тапу на фото */ },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                    )
                }
                Text(
                    text = if (items.size > 1) {
                        "${pagerState.currentPage + 1} / ${items.size}"
                    } else {
                        items[pagerState.currentPage].fileName ?: "Фото"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    maxLines = 1,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = "Ещё",
                            tint = Color.White,
                        )
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
                                val current = items[pagerState.currentPage]
                                onDownload(
                                    current.path,
                                    current.contentUrl,
                                    current.fileName ?: "photo.jpg",
                                    current.mimeType ?: "image/jpeg",
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
