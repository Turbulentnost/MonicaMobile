package com.example.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.data.MessageAttachment
import com.example.monica.data.MessageItem
import com.example.monica.data.UserProfile
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.CachedMediaImage
import com.example.monica.ui.components.MonicaAppBar
import com.example.monica.ui.components.UserAvatar
import com.example.monica.ui.components.rememberChatFileDownloader
import com.example.monica.ui.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private data class SharedFileRow(
    val id: String,
    val name: String,
    val meta: String,
    val color: Color,
    val path: String?,
    val contentUrl: String?,
    val mimeType: String?,
)

private data class SharedPhotoRow(
    val path: String?,
    val contentUrl: String,
    val fileName: String,
    val messageId: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailsScreen(
    chatId: String,
    vm: MonicaViewModel,
    onBack: () -> Unit,
    onJumpToMessage: () -> Unit,
) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val onlineIds by vm.onlineIds.collectAsStateWithLifecycle()
    val partner = chats.find { it.id == chatId }?.partner
    val isOnline = onlineIds.contains(partner?.id)

    var tabIndex by remember { mutableIntStateOf(0) }
    var fileMessages by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }
    var filesError by remember { mutableStateOf<String?>(null) }
    var showAllFiles by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var lightboxPhoto by remember { mutableStateOf<SharedPhotoRow?>(null) }
    val downloadChatFile = rememberChatFileDownloader(vm.api)

    LaunchedEffect(chatId) {
        filesLoading = true
        filesError = null
        showAllFiles = false
        searchQuery = ""
        searchResults = emptyList()
        runCatching {
            withContext(Dispatchers.IO) { vm.api.listChatFiles(chatId) }
        }.onSuccess {
            fileMessages = it
        }.onFailure {
            filesError = "Не удалось загрузить файлы"
        }
        filesLoading = false
    }

    LaunchedEffect(chatId, searchQuery) {
        val query = searchQuery.trim()
        if (query.length < 2) {
            searchResults = emptyList()
            searchLoading = false
            searchError = null
            return@LaunchedEffect
        }
        searchLoading = true
        searchError = null
        delay(280)
        runCatching {
            withContext(Dispatchers.IO) {
                vm.api.listMessages(chatId = chatId, query = query, limit = 40)
            }
        }.onSuccess {
            searchResults = it
        }.onFailure {
            searchResults = emptyList()
            searchError = "Не удалось выполнить поиск"
        }
        searchLoading = false
    }

    val files = remember(fileMessages) { flattenFiles(fileMessages) }
    val photos = remember(fileMessages) { flattenPhotos(fileMessages) }
    val visibleFiles = if (showAllFiles) files else files.take(5)
    val searchActive = searchQuery.trim().length >= 2

    Scaffold(
        topBar = {
            MonicaAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        "Детали",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                PartnerHeader(partner = partner, isOnline = isOnline)
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Поиск по чату",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Найти сообщение…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (searchActive) {
                item { Spacer(Modifier.height(8.dp)) }
                when {
                    searchLoading -> item {
                        Text(
                            "Поиск…",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    searchError != null -> item {
                        Text(
                            searchError!!,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    searchResults.isEmpty() -> item {
                        Text(
                            "Ничего не найдено",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> items(searchResults, key = { it.id }) { message ->
                        SearchResultRow(
                            message = message,
                            onClick = {
                                vm.jumpToMessage(chatId, message.id)
                                onJumpToMessage()
                            },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                ScrollableTabRow(
                    selectedTabIndex = tabIndex,
                    edgePadding = 16.dp,
                    divider = {},
                ) {
                    listOf("Files", "Members", "Pinned").forEachIndexed { index, label ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            text = { Text(label) },
                        )
                    }
                }
                HorizontalDivider()
            }

            when (tabIndex) {
                0 -> {
                    when {
                        filesLoading -> item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                        filesError != null -> item {
                            Text(
                                filesError!!,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        files.isEmpty() -> item {
                            Text(
                                "Файлов пока нет",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            items(visibleFiles, key = { it.id }) { file ->
                                FileRow(file = file, onDownloadFile = downloadChatFile)
                            }
                            if (files.size > 5) {
                                item {
                                    TextButton(
                                        onClick = { showAllFiles = !showAllFiles },
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    ) {
                                        Text(if (showAllFiles) "Свернуть" else "Все файлы (${files.size})")
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        PhotosSection(
                            photos = photos,
                            loading = filesLoading,
                            error = filesError,
                            vm = vm,
                            onOpen = { lightboxPhoto = it },
                        )
                    }
                }
                else -> item {
                    Text(
                        "Закреплённых сообщений пока нет",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    lightboxPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { lightboxPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { lightboxPhoto = null },
                contentAlignment = Alignment.Center,
            ) {
                CachedMediaImage(
                    message = MessageItem(
                        id = photo.messageId,
                        sender = null,
                        messageType = "photo",
                        content = photo.path.orEmpty(),
                        contentUrl = photo.contentUrl,
                        fileName = photo.fileName,
                        sentAt = "",
                    ),
                    api = vm.api,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .height(420.dp),
                )
            }
        }
    }
}

@Composable
private fun PartnerHeader(partner: UserProfile?, isOnline: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UserAvatar(partner, size = 72.dp, showOnline = true, isOnline = isOnline)
        Spacer(Modifier.height(12.dp))
        Text(
            if (!partner?.nickname.isNullOrBlank()) "@${partner!!.nickname}" else "Пользователь",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        val fullName = listOfNotNull(
            partner?.firstName?.takeIf { it.isNotBlank() },
            partner?.lastName?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        if (fullName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                fullName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultRow(message: MessageItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            searchPreview(message),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "@${message.sender?.nickname ?: "user"} · ${TimeFormat.searchResultTime(message.sentAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileRow(
    file: SharedFileRow,
    onDownloadFile: (path: String?, url: String?, name: String, mime: String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onDownloadFile(file.path, file.contentUrl, file.name, file.mimeType)
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(file.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = file.color,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (file.meta.isNotBlank()) {
                Text(
                    file.meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Outlined.Download,
            contentDescription = "Скачать",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PhotosSection(
    photos: List<SharedPhotoRow>,
    loading: Boolean,
    error: String?,
    vm: MonicaViewModel,
    onOpen: (SharedPhotoRow) -> Unit,
) {
    when {
        loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        error != null -> Text(
            error,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
        )
        photos.isEmpty() -> Text(
            "Фотографий пока нет",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            photos.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { photo ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onOpen(photo) },
                        ) {
                            CachedMediaImage(
                                message = MessageItem(
                                    id = photo.messageId,
                                    sender = null,
                                    messageType = "photo",
                                    content = photo.path.orEmpty(),
                                    contentUrl = photo.contentUrl,
                                    fileName = photo.fileName,
                                    sentAt = "",
                                ),
                                api = vm.api,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun attachmentItems(message: MessageItem): List<MessageAttachment> {
    if (message.attachments.isNotEmpty()) return message.attachments
    return listOf(
        MessageAttachment(
            path = message.content.takeIf { it.isNotBlank() },
            contentUrl = message.contentUrl,
            fileName = message.fileName,
            mimeType = message.mimeType,
            fileSize = message.fileSize,
        ),
    )
}

private fun flattenFiles(messages: List<MessageItem>): List<SharedFileRow> {
    val result = mutableListOf<SharedFileRow>()
    val seen = mutableSetOf<String>()
    messages.forEach { message ->
        if (message.messageType == "photo") return@forEach
        attachmentItems(message).forEachIndexed { index, item ->
            val path = item.path?.takeIf { it.isNotBlank() }
                ?: message.content.takeIf { it.isNotBlank() }
            val url = item.contentUrl ?: message.contentUrl
            if (path.isNullOrBlank() && url.isNullOrBlank()) return@forEachIndexed
            val key = path ?: url ?: "${message.id}-$index"
            if (!seen.add(key)) return@forEachIndexed
            val name = item.fileName ?: message.fileName ?: "Файл"
            val mime = item.mimeType ?: message.mimeType.orEmpty()
            val size = item.fileSize ?: message.fileSize
            val type = fileTypeLabel(mime, name)
            val sizeLabel = formatFileSize(size)
            result += SharedFileRow(
                id = key,
                name = name,
                meta = listOfNotNull(type, sizeLabel.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = fileColor(mime, name),
                path = path,
                contentUrl = url,
                mimeType = mime.takeIf { it.isNotBlank() },
            )
        }
    }
    return result
}

private fun flattenPhotos(messages: List<MessageItem>): List<SharedPhotoRow> {
    val result = mutableListOf<SharedPhotoRow>()
    val seen = mutableSetOf<String>()
    messages.forEach { message ->
        if (message.messageType != "photo") return@forEach
        attachmentItems(message).forEachIndexed { index, item ->
            val key = item.path ?: item.contentUrl ?: "${message.id}-$index"
            val url = item.contentUrl ?: return@forEachIndexed
            if (!seen.add(key)) return@forEachIndexed
            result += SharedPhotoRow(
                path = item.path,
                contentUrl = url,
                fileName = item.fileName ?: message.fileName ?: "Фото",
                messageId = message.id,
            )
        }
    }
    return result
}

private fun searchPreview(message: MessageItem): String {
    return when (message.messageType) {
        "text" -> message.content.trim().ifBlank { "Сообщение" }
        "photo" -> message.caption?.trim()?.takeIf { it.isNotBlank() } ?: "Фото"
        "file" -> message.fileName ?: "Файл"
        "voice" -> "Голосовое сообщение"
        "call" -> message.content.trim().ifBlank { "Звонок" }
        "code" -> message.fileName ?: "Код"
        else -> message.content.trim().ifBlank { message.fileName ?: "Сообщение" }
    }
}

private fun formatFileSize(bytes: Long?): String {
    val size = bytes ?: return ""
    if (size <= 0L) return ""
    if (size < 1024) return "$size Б"
    if (size < 1024 * 1024) return "${size / 1024} КБ"
    val mb = size / (1024.0 * 1024.0)
    return if (size >= 10L * 1024 * 1024) {
        "${mb.toInt()} МБ"
    } else {
        String.format(Locale.US, "%.1f МБ", mb)
    }
}

private fun fileTypeLabel(mimeType: String, name: String): String {
    val mime = mimeType.lowercase()
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").uppercase()
    return when {
        mime.startsWith("image/") -> "Изображение"
        mime == "application/pdf" -> "PDF"
        ext.isNotBlank() && ext != name.uppercase() -> ext
        else -> "Файл"
    }
}

private fun fileColor(mimeType: String, name: String): Color {
    val mime = mimeType.lowercase()
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when {
        mime.startsWith("image/") -> Color(0xFF38BDF8)
        mime == "application/pdf" || ext == "pdf" -> Color(0xFFEF4444)
        ext in setOf("zip", "rar", "7z") -> Color(0xFFEAB308)
        ext in setOf("py", "js", "ts", "json", "yaml", "yml") -> Color(0xFFA78BFA)
        else -> Color(0xFF94A3B8)
    }
}
