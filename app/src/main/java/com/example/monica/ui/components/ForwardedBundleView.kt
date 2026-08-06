package com.example.monica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.monica.data.ForwardBundleItem
import com.example.monica.data.MessageAttachment
import com.example.monica.data.MonicaApi
import com.example.monica.ui.util.TimeFormat

@Composable
fun ForwardedBundleView(
    bundle: List<ForwardBundleItem>,
    comment: String,
    api: MonicaApi,
    foreground: Color,
    onOpenOriginal: (chatId: String, messageId: String) -> Unit,
    onDownloadFile: ((path: String?, url: String?, name: String, mime: String?) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            forwardedCountLabel(bundle.size),
            color = foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        bundle.forEach { item ->
            ForwardedItem(
                item = item,
                api = api,
                foreground = foreground,
                onDownloadFile = onDownloadFile,
                onOpen = {
                    if (item.originalChatId.isNotBlank() && item.originalId.isNotBlank()) {
                        onOpenOriginal(item.originalChatId, item.originalId)
                    }
                },
            )
        }
        if (comment.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            LinkAwareText(
                text = comment,
                color = foreground,
                linkColor = Color(0xFF7EB6FF),
            )
        }
    }
}

@Composable
private fun ForwardedItem(
    item: ForwardBundleItem,
    api: MonicaApi,
    foreground: Color,
    onOpen: () -> Unit,
    onDownloadFile: ((path: String?, url: String?, name: String, mime: String?) -> Unit)?,
) {
    val allPhotos = remember(item) {
        item.attachments.ifEmpty {
            listOf(
                MessageAttachment(
                    path = item.content,
                    contentUrl = item.contentUrl,
                    fileName = item.fileName,
                    mimeType = item.mimeType,
                ),
            )
        }.mapNotNull { it.toPhotoViewerItem() }
    }
    var lightboxIndex by remember(item.originalId) { mutableStateOf<Int?>(null) }
    lightboxIndex?.let { index ->
        if (allPhotos.isNotEmpty()) {
            PhotoLightbox(
                items = allPhotos,
                initialIndex = index,
                api = api,
                onDismiss = { lightboxIndex = null },
                onDownload = { path, url, name, mime ->
                    onDownloadFile?.invoke(path, url, name, mime)
                },
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(item.sender, size = 30.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.sender?.displayName ?: "user",
                    color = foreground,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    TimeFormat.messageTime(item.sentAt),
                    color = foreground.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Icon(
                    Icons.Outlined.NorthEast,
                    contentDescription = "Открыть оригинал",
                    tint = foreground.copy(alpha = 0.72f),
                    modifier = Modifier.size(16.dp),
                )
            }
            if (item.messageType == "photo" && allPhotos.isNotEmpty()) {
                val preview = allPhotos.take(4)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    preview.forEachIndexed { index, photo ->
                        CachedMediaImage(
                            objectPath = photo.path,
                            contentUrl = photo.contentUrl,
                            fileName = photo.fileName,
                            api = api,
                            modifier = Modifier
                                .weight(1f)
                                .height(112.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { lightboxIndex = index },
                        )
                    }
                }
            }
            val text = forwardedItemText(item)
            if (text.isNotBlank()) {
                LinkAwareText(
                    text = text,
                    color = foreground,
                    linkColor = Color(0xFF7EB6FF),
                    maxLines = 8,
                )
            }
        }
    }
}

private fun forwardedItemText(item: ForwardBundleItem): String = when (item.messageType) {
    "voice" -> "Голосовое сообщение"
    "file" -> item.fileName ?: "Файл"
    "photo" -> item.caption.orEmpty().ifBlank {
        item.content.takeIf { it.isNotBlank() && !it.contains('/') }.orEmpty()
    }
    else -> item.content
}

private fun forwardedCountLabel(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "$count пересланное сообщение"
        mod10 in 2..4 && mod100 !in 12..14 -> "$count пересланных сообщения"
        else -> "$count пересланных сообщений"
    }
}
