package com.example.monica.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.monica.data.UserProfile
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.MonicaAppBar
import com.example.monica.ui.components.UserAvatar
import kotlinx.coroutines.delay

private const val TITLE_MAX = 64
private const val GROUP_PHOTO_MAX_BYTES = 10 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    vm: MonicaViewModel,
    onBack: () -> Unit,
    onCreated: (chatId: String) -> Unit,
) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val myId = vm.session.userId.orEmpty()
    val context = LocalContext.current

    var step by remember { mutableStateOf(0) } // 0 = name/photo, 1 = members
    var title by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedUsers by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoName by remember { mutableStateOf("group.jpg") }
    var photoMime by remember { mutableStateOf("image/jpeg") }
    var localError by remember { mutableStateOf<String?>(null) }

    val partners = remember(chats, myId) {
        chats
            .asSequence()
            .filter { !it.isGroup }
            .mapNotNull { it.partner }
            .filter { it.id.isNotBlank() && it.id != myId }
            .distinctBy { it.id }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            } ?: "group.jpg"
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        when {
            bytes == null || bytes.isEmpty() -> {
                localError = "Не удалось прочитать фото"
            }
            bytes.size > GROUP_PHOTO_MAX_BYTES -> {
                localError = "Фото слишком большое (макс. 10 МБ)"
            }
            !mime.startsWith("image/") &&
                !name.lowercase().matches(Regex(".*\\.(jpe?g|png|webp|gif)$")) -> {
                localError = "Выберите изображение (JPG, PNG, WEBP, GIF)"
            }
            else -> {
                photoUri = uri
                photoMime = mime
                photoName = name
                photoBytes = bytes
                localError = null
            }
        }
    }

    LaunchedEffect(query, step) {
        if (step != 1) return@LaunchedEffect
        delay(250)
        vm.searchUsers(query)
    }

    val visibleUsers = remember(partners, searchResults, selectedUsers, query, myId) {
        val byId = linkedMapOf<String, UserProfile>()
        partners.forEach { byId[it.id] = it }
        if (query.trim().length >= 2) {
            searchResults.forEach { user ->
                if (user.id != myId) byId[user.id] = user
            }
        }
        selectedUsers.forEach { (id, user) -> byId.putIfAbsent(id, user) }
        val q = query.trim().lowercase()
        byId.values
            .filter { user ->
                if (q.isBlank()) return@filter true
                val nick = user.nickname.lowercase()
                val full = user.displayName.lowercase()
                nick.contains(q) || full.contains(q)
            }
            .sortedWith(
                compareBy(
                    { if (it.id in selectedIds) 0 else 1 },
                    { it.displayName.lowercase() },
                ),
            )
    }

    fun toggleUser(user: UserProfile) {
        val id = user.id
        if (id in selectedIds) {
            selectedIds = selectedIds - id
            selectedUsers = selectedUsers - id
        } else {
            selectedIds = selectedIds + id
            selectedUsers = selectedUsers + (id to user)
        }
    }

    fun submit() {
        localError = null
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            localError = "Укажите название группы"
            step = 0
            return
        }
        if (selectedIds.isEmpty()) {
            localError = "Выберите хотя бы одного участника"
            return
        }
        if (photoUri != null && (photoBytes == null || photoBytes!!.isEmpty())) {
            localError = "Не удалось прочитать фото группы"
            step = 0
            return
        }
        vm.createGroup(
            title = cleanTitle,
            memberIds = selectedIds.toList(),
            photoBytes = photoBytes,
            photoFileName = photoName,
            photoMime = photoMime,
            onCreated = onCreated,
        )
    }

    Scaffold(
        topBar = {
            MonicaAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == 1) step = 0 else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        if (step == 0) "Новая группа" else "Участники",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (step == 0) {
                        TextButton(
                            onClick = {
                                if (title.trim().isEmpty()) {
                                    localError = "Укажите название группы"
                                } else {
                                    localError = null
                                    step = 1
                                }
                            },
                        ) {
                            Text("Далее")
                        }
                    } else {
                        TextButton(
                            onClick = ::submit,
                            enabled = !loading && selectedIds.isNotEmpty(),
                        ) {
                            Text("Создать (${selectedIds.size})")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (step == 0) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { photoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Фото группы",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.AddAPhoto,
                            contentDescription = "Загрузить фото",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (photoUri == null) "Добавить фото группы" else "Изменить фото",
                        modifier = Modifier.clickable { photoPicker.launch("image/*") },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (photoUri != null) {
                        Text(
                            "Убрать",
                            modifier = Modifier.clickable {
                                photoUri = null
                                photoBytes = null
                                photoName = "group.jpg"
                                photoMime = "image/jpeg"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        if (it.length <= TITLE_MAX) {
                            title = it
                            localError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Название группы") },
                    supportingText = {
                        Text("${title.trim().length}/$TITLE_MAX")
                    },
                    shape = RoundedCornerShape(14.dp),
                )
                localError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (title.trim().isEmpty()) {
                            localError = "Укажите название группы"
                        } else {
                            localError = null
                            step = 1
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    enabled = title.trim().isNotEmpty(),
                ) {
                    Text("Далее")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    title.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Выбрано: ${selectedIds.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск участников…") },
                    shape = RoundedCornerShape(14.dp),
                )
                localError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(visibleUsers, key = { it.id }) { user ->
                        val selected = user.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggleUser(user) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(user, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (user.nickname.isNotBlank()) {
                                    Text(
                                        "@${user.nickname}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (selected) {
                                    Icons.Outlined.CheckCircle
                                } else {
                                    Icons.Outlined.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp),
                    )
                } else {
                    Button(
                        onClick = ::submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Text("Создать группу (${selectedIds.size})")
                    }
                }
            }
        }
    }
}
