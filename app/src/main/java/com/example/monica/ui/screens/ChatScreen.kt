package com.example.monica.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.InsertEmoticon
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.R
import com.example.monica.data.CallUiStatus
import com.example.monica.data.DeliveryStatus
import com.example.monica.data.MessageItem
import com.example.monica.data.MonicaApi
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.AppIcon
import com.example.monica.ui.components.CachedMediaImage
import com.example.monica.ui.components.CodeViewerView
import com.example.monica.ui.components.EmojiPicker
import com.example.monica.ui.components.MonicaAppBar
import com.example.monica.ui.components.MonacoEditorView
import com.example.monica.ui.components.UserAvatar
import com.example.monica.ui.components.VoiceMessagePlayer
import com.example.monica.ui.components.VoiceRecorderController
import com.example.monica.ui.components.formatVoiceDuration
import com.example.monica.ui.components.rememberChatFileDownloader
import com.example.monica.ui.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

private data class CodeChrome(
    val bg: Color,
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val error: Color,
)

private fun codeChrome(darkTheme: Boolean) = if (darkTheme) {
    CodeChrome(
        bg = Color(0xFF1E1E1E),
        fg = Color(0xFFD4D4D4),
        muted = Color(0xFF858585),
        accent = Color(0xFF4EC9B0),
        error = Color(0xFFF48771),
    )
} else {
    CodeChrome(
        bg = Color(0xFFFFFFFF),
        fg = Color(0xFF1E1E1E),
        muted = Color(0xFF6B6B6B),
        accent = Color(0xFF267F99),
        error = Color(0xFFA1260D),
    )
}

private fun compressWaveform(samples: List<Float>, targetSize: Int): List<Float> {
    if (samples.isEmpty()) return emptyList()
    if (samples.size <= targetSize) return samples.map { it.coerceIn(0.05f, 1f) }
    return List(targetSize) { index ->
        val start = index * samples.size / targetSize
        val end = ((index + 1) * samples.size / targetSize).coerceAtMost(samples.size)
        samples.subList(start, end).maxOrNull()?.coerceIn(0.05f, 1f) ?: 0.05f
    }
}

@Suppress("DEPRECATION")
private fun vibrateVoiceCancellation(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    } else {
        vibrator.vibrate(90L)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    vm: MonicaViewModel,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit = {},
) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val partnerTyping by vm.partnerTyping.collectAsStateWithLifecycle()
    val onlineIds by vm.onlineIds.collectAsStateWithLifecycle()
    val lastSeenMap by vm.lastSeenMap.collectAsStateWithLifecycle()
    val privateSessionId by vm.privateSessionId.collectAsStateWithLifecycle()
    val privateChatId by vm.privateChatId.collectAsStateWithLifecycle()
    val outgoingPending by vm.outgoingPending.collectAsStateWithLifecycle()
    val incomingInvites by vm.incomingInvitesByChat.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()
    val callState by vm.callState.collectAsStateWithLifecycle()
    val myId = vm.session.userId
    val context = LocalContext.current

    val chat = chats.find { it.id == chatId }
    val partner = chat?.partner
    val isOnline = onlineIds.contains(partner?.id)
    val lastSeen = lastSeenMap[partner?.id] ?: partner?.lastSeenAt
    val statusText = if (isOnline) "в сети" else TimeFormat.lastSeen(lastSeen)
    val incomingInvite = incomingInvites[chatId]
    val isOutgoingPending = outgoingPending.containsKey(chatId)
    val isPrivateActiveHere = privateSessionId != null && privateChatId == chatId
    val callBusy = callState.status !in listOf(CallUiStatus.Idle, CallUiStatus.Ended)
    val canStartCall = isOnline && !callBusy && partner != null
    val canStartVideo = canStartCall && vm.hasCameraDevice()
    val downloadChatFile = rememberChatFileDownloader(vm.api)

    var pendingStartMode by remember { mutableStateOf<String?>(null) }
    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val mode = pendingStartMode
        pendingStartMode = null
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        val camOk = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        if (mode == null || !micOk) return@rememberLauncherForActivityResult
        if (mode == "video" && !camOk) return@rememberLauncherForActivityResult
        vm.startCall(chatId, mode)
    }

    fun requestStartCall(mode: String) {
        val micOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        val camOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        when {
            mode == "audio" && micOk -> vm.startCall(chatId, "audio")
            mode == "video" && micOk && camOk -> vm.startCall(chatId, "video")
            mode == "video" -> {
                pendingStartMode = "video"
                callPermLauncher.launch(
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
                )
            }
            else -> {
                pendingStartMode = "audio"
                callPermLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }
    }

    var input by remember { mutableStateOf("") }
    var codeMode by remember { mutableStateOf(false) }
    var codeLanguage by remember { mutableStateOf("python") }
    var codeFileName by remember { mutableStateOf("script.py") }
    var codeText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollToBottomTick by vm.scrollChatToBottom.collectAsStateWithLifecycle()
    val pendingScrollToMessageId by vm.pendingScrollToMessageId.collectAsStateWithLifecycle()
    val highlightedMessageId by vm.highlightedMessageId.collectAsStateWithLifecycle()

    DisposableEffect(chatId) {
        vm.openChat(chatId)
        // Не вызываем leaveChat в onDispose: переход на экран деталей снимает ChatScreen
        // с композиции, но чат должен остаться открытым (поиск → jump to message).
        onDispose { }
    }

    val dayGroups = remember(messages) {
        messages.groupBy { TimeFormat.dayKey(it.sentAt) }
            .entries
            .map { (key, msgs) ->
                key to (TimeFormat.dayLabel(msgs.firstOrNull()?.sentAt) to msgs)
            }
    }

    LaunchedEffect(chatId, messages.lastOrNull()?.id, scrollToBottomTick) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (pendingScrollToMessageId != null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.scrollToItem(lastIndex)
        }
    }

    LaunchedEffect(pendingScrollToMessageId, messages) {
        val targetId = pendingScrollToMessageId ?: return@LaunchedEffect
        if (messages.none { it.id == targetId }) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        val index = messageListIndex(messages, targetId)
        if (index >= 0) {
            listState.animateScrollToItem(index)
            vm.clearPendingScrollToMessage()
        }
    }

    LaunchedEffect(codeLanguage) {
        val ext = if (codeLanguage == "javascript") ".js" else ".py"
        val base = codeFileName.substringBeforeLast('.', codeFileName)
        val stripped = if (base.isBlank() || base == codeFileName) {
            if (codeLanguage == "javascript") "script" else "script"
        } else base
        codeFileName = stripped + ext
    }

    fun sendCode() {
        if (codeText.isBlank() || loading) return
        vm.sendCodeFile(chatId, codeLanguage, codeFileName, codeText) {
            codeText = ""
            codeMode = false
        }
    }

    Scaffold(
        topBar = {
            MonicaAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenDetails)
                            .padding(end = 4.dp),
                    ) {
                        UserAvatar(partner, size = 30.dp, showOnline = true, isOnline = isOnline)
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                "@${partner?.nickname ?: "—"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { requestStartCall("audio") },
                        enabled = canStartCall,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = "Аудиозвонок",
                            tint = if (canStartCall) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                    IconButton(
                        onClick = { requestStartCall("video") },
                        enabled = canStartVideo,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = "Видеозвонок",
                            tint = if (canStartVideo) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                    PrivateChatActionButton(
                        hasIncomingInvite = incomingInvite != null,
                        isOutgoingPending = isOutgoingPending,
                        isActive = isPrivateActiveHere,
                        onInvite = { vm.invitePrivate(chatId) },
                        onAccept = { vm.acceptInviteForChat(chatId) },
                        onCancelOutgoing = { vm.cancelOutgoingInvite(chatId) },
                        onReopen = { vm.reopenPrivate() },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dayGroups.forEach { (_, pair) ->
                    val (label, dayMessages) = pair
                    item(key = "day-$label-${dayMessages.firstOrNull()?.id}") {
                        DaySeparator(label)
                    }
                    items(dayMessages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            isOwn = msg.sender?.id == myId,
                            chatId = chatId,
                            vm = vm,
                            darkTheme = darkTheme,
                            highlighted = highlightedMessageId == msg.id,
                            onDownloadFile = downloadChatFile,
                        )
                    }
                }
            }

            if (partnerTyping) {
                Text(
                    "@${partner?.nickname} печатает…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (codeMode) {
                CodeComposer(
                    language = codeLanguage,
                    onLanguageChange = { codeLanguage = it },
                    fileName = codeFileName,
                    onFileNameChange = { codeFileName = it },
                    code = codeText,
                    onCodeChange = { codeText = it },
                    loading = loading,
                    darkTheme = darkTheme,
                    onCancel = { codeMode = false },
                    onSend = { sendCode() },
                )
            } else {
                MessageComposer(
                    text = input,
                    onTextChange = {
                        input = it
                        vm.onComposerChange(it)
                    },
                    loading = loading,
                    onSendText = {
                        vm.sendMessage(input)
                        input = ""
                    },
                    onOpenCode = {
                        codeMode = true
                        if (codeText.isBlank()) {
                            codeLanguage = "python"
                            codeFileName = "script.py"
                        }
                    },
                    onSendFile = { name, bytes, mime ->
                        vm.sendUploadedFile(chatId, name, bytes, mime)
                    },
                    onSendVoice = { file, waveform, durationMs ->
                        val bytes = runCatching { file.readBytes() }.getOrNull()
                        if (bytes != null) {
                            vm.sendUploadedFile(
                                chatId = chatId,
                                fileName = file.name,
                                bytes = bytes,
                                mimeType = "audio/mp4",
                                waveform = waveform,
                                voiceDurationMs = durationMs,
                            ) {
                                file.delete()
                            }
                        } else {
                            file.delete()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    loading: Boolean,
    onSendText: () -> Unit,
    onOpenCode: () -> Unit,
    onSendFile: (name: String, bytes: ByteArray, mime: String) -> Unit,
    onSendVoice: (file: File, waveform: List<Float>, durationMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorderController(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var emojiPickerVisible by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }
    val fullRecordingLevels = remember { mutableListOf<Float>() }
    val waveformSamples = 28 // 28 × 70 мс ≈ последние 2 секунды
    var micPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micPermission = granted
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        } ?: "attachment-${System.currentTimeMillis()}"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null) onSendFile(name, bytes, mime)
    }

    DisposableEffect(Unit) {
        onDispose { recorder.release() }
    }

    LaunchedEffect(recording) {
        while (recording) {
            val snapshot = recorder.snapshot(cancelled)
            elapsedMs = snapshot.elapsedMs
            if (levels.size >= waveformSamples) levels.removeAt(0)
            val level = snapshot.amplitude.coerceAtLeast(0.08f)
            levels.add(level)
            fullRecordingLevels.add(level)
            delay(70)
        }
    }

    fun startRecording(): Boolean {
        if (!micPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        cancelled = false
        elapsedMs = 0L
        levels.clear()
        fullRecordingLevels.clear()
        repeat(waveformSamples) { levels.add(0.08f) }
        recording = recorder.start()
        return recording
    }

    fun finishRecording(send: Boolean) {
        val shouldSend = send && !cancelled && elapsedMs >= 350L
        val duration = elapsedMs
        val waveform = compressWaveform(fullRecordingLevels, 64)
        val file = recorder.stop(shouldSend)
        recording = false
        if (file != null) onSendVoice(file, waveform, duration)
        cancelled = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(visible = emojiPickerVisible && !recording) {
            EmojiPicker(
                onSelect = { emoji -> onTextChange(text + emoji) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        AnimatedVisibility(visible = recording) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (cancelled) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = 4.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .padding(bottom = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val waveColor = if (cancelled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (cancelled) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color(0xFFE53935)
                                },
                            ),
                    )
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp),
                    ) {
                        val count = levels.size.coerceAtLeast(1)
                        val spacing = size.width / count
                        levels.forEachIndexed { index, level ->
                            val bar = size.height * level.coerceIn(0.12f, 1f)
                            drawLine(
                                color = waveColor,
                                start = androidx.compose.ui.geometry.Offset(
                                    index * spacing,
                                    (size.height - bar) / 2f,
                                ),
                                end = androidx.compose.ui.geometry.Offset(
                                    index * spacing,
                                    (size.height + bar) / 2f,
                                ),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                    Text(
                        if (cancelled) "Отпустите — отмена" else formatVoiceDuration(elapsedMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cancelled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { attachmentLauncher.launch("*/*") },
                    enabled = !loading && !recording,
                    modifier = Modifier.size(44.dp),
                ) {
                    AppIcon(
                        resId = R.drawable.ic_attachment,
                        contentDescription = "Прикрепить файл",
                        size = 28.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 92.dp),
                    enabled = !recording,
                    placeholder = { Text("Сообщение") },
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.isNotBlank() && !loading) onSendText()
                        },
                    ),
                )

                IconButton(
                    onClick = { emojiPickerVisible = !emojiPickerVisible },
                    enabled = !loading && !recording,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.InsertEmoticon,
                        contentDescription = "Эмодзи",
                        modifier = Modifier.size(26.dp),
                        tint = if (emojiPickerVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                IconButton(
                    onClick = {
                        emojiPickerVisible = false
                        onOpenCode()
                    },
                    enabled = !loading && !recording,
                    modifier = Modifier.size(44.dp),
                ) {
                    AppIcon(
                        resId = R.drawable.ic_code,
                        contentDescription = "Написать код",
                        size = 28.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (text.isNotBlank()) {
                    IconButton(
                        onClick = onSendText,
                        enabled = !loading,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(micPermission, loading) {
                                val cancelThreshold = 84.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (loading || !startRecording()) {
                                        var pressed = true
                                        while (pressed) {
                                            val event = awaitPointerEvent()
                                            pressed = event.changes.any { it.pressed }
                                        }
                                        return@awaitEachGesture
                                    }
                                    var vibrated = false
                                    var pressed = true
                                    while (pressed) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull()
                                        if (change != null) {
                                            val movedLeft =
                                                down.position.x - change.position.x > cancelThreshold
                                            if (movedLeft && !cancelled) {
                                                cancelled = true
                                                if (!vibrated) {
                                                    vibrateVoiceCancellation(context)
                                                    vibrated = true
                                                }
                                            } else if (!movedLeft && cancelled) {
                                                cancelled = false
                                            }
                                            change.consume()
                                        }
                                        pressed = event.changes.any { it.pressed }
                                    }
                                    finishRecording(send = !cancelled)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            resId = R.drawable.ic_mic,
                            contentDescription = "Удерживайте для записи",
                            size = 28.dp,
                            tint = if (recording) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeComposer(
    language: String,
    onLanguageChange: (String) -> Unit,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    loading: Boolean,
    darkTheme: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    var langExpanded by remember { mutableStateOf(false) }
    val langLabel = if (language == "javascript") "JavaScript" else "Python"
    val chrome = remember(darkTheme) { codeChrome(darkTheme) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(chrome.bg)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it },
                modifier = Modifier.width(140.dp),
            ) {
                OutlinedTextField(
                    value = langLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Язык") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Python") },
                        onClick = {
                            onLanguageChange("python")
                            langExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("JavaScript") },
                        onClick = {
                            onLanguageChange("javascript")
                            langExpanded = false
                        },
                    )
                }
            }
            OutlinedTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                modifier = Modifier.weight(1f),
                label = { Text("Имя файла") },
                singleLine = true,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Двойной пробел — отступ · отправить кнопкой ниже",
            color = chrome.muted,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(6.dp))
        MonacoEditorView(
            value = code,
            language = language,
            darkTheme = darkTheme,
            onValueChange = onCodeChange,
            onSubmit = onSend,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 240.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Отмена", color = chrome.fg)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = code.isNotBlank() && fileName.isNotBlank() && !loading,
            ) {
                Text(if (loading) "Отправка…" else "Отправить код")
            }
        }
    }
}

@Composable
private fun PrivateChatActionButton(
    hasIncomingInvite: Boolean,
    isOutgoingPending: Boolean,
    isActive: Boolean,
    onInvite: () -> Unit,
    onAccept: () -> Unit,
    onCancelOutgoing: () -> Unit,
    onReopen: () -> Unit,
) {
    when {
        hasIncomingInvite -> {
            IconButton(
                onClick = onAccept,
                modifier = Modifier.size(40.dp),
            ) {
                AppIcon(
                    resId = R.drawable.ic_check_green,
                    contentDescription = "Принять приватный чат",
                    size = 32.dp,
                    tint = null,
                )
            }
        }
        isOutgoingPending -> {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = onCancelOutgoing,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Отменить приглашение",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        isActive -> {
            IconButton(
                onClick = onReopen,
                modifier = Modifier.size(40.dp),
            ) {
                AppIcon(
                    resId = R.drawable.ic_check_green,
                    contentDescription = "Открыть приватный чат",
                    size = 32.dp,
                    tint = null,
                )
            }
        }
        else -> {
            IconButton(
                onClick = onInvite,
                modifier = Modifier.size(40.dp),
            ) {
                AppIcon(
                    resId = R.drawable.ic_private_message,
                    contentDescription = "Пригласить в приватный чат",
                    size = 32.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun codeLanguageOf(message: MessageItem): String? {
    if (message.messageType != "file") return null
    val name = message.fileName.orEmpty().lowercase()
    val mime = message.mimeType.orEmpty().lowercase()
    return when {
        name.endsWith(".py") || mime.contains("python") -> "python"
        name.endsWith(".js") ||
            mime.contains("javascript") ||
            mime == "text/js" ||
            mime == "application/x-javascript" -> "javascript"
        else -> null
    }
}

/** Индекс сообщения в LazyColumn с учётом day-разделителей. */
private fun messageListIndex(messages: List<MessageItem>, messageId: String): Int {
    var index = 0
    messages.groupBy { TimeFormat.dayKey(it.sentAt) }.values.forEach { dayMessages ->
        index += 1 // DaySeparator
        dayMessages.forEach { msg ->
            if (msg.id == messageId) return index
            index += 1
        }
    }
    return -1
}

@Composable
private fun MessageBubble(
    message: MessageItem,
    isOwn: Boolean,
    chatId: String,
    vm: MonicaViewModel,
    darkTheme: Boolean,
    highlighted: Boolean = false,
    onDownloadFile: (path: String?, url: String?, name: String, mime: String?) -> Unit,
) {
    if (message.messageType == "call") {
        val status = message.mimeType.orEmpty()
        val accent = when (status) {
            "missed", "rejected" -> Color(0xFFFF9A9A)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.content.ifBlank { "Звонок" },
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = TimeFormat.messageTime(message.sentAt),
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.65f),
            )
        }
        return
    }

    val codeLang = codeLanguageOf(message)
    val chrome = remember(darkTheme) { codeChrome(darkTheme) }
    val bg = when {
        codeLang != null -> chrome.bg
        isOwn -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        codeLang != null -> chrome.fg
        isOwn -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val delivery = message.deliveryStatus(isOwn)
    val bubbleAlpha = if (delivery == DeliveryStatus.Sending) 0.72f else 1f
    val highlightColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
        } else {
            Color.Transparent
        },
        label = "messageHighlight",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlightColor, RoundedCornerShape(18.dp))
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .then(if (codeLang != null) Modifier.fillMaxWidth(0.95f) else Modifier)
                .widthIn(max = if (codeLang != null) 420.dp else 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg.copy(alpha = if (codeLang != null) 1f else bubbleAlpha))
                .padding(10.dp),
        ) {
            Text(
                "@${message.sender?.nickname ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            when {
                message.messageType == "photo" -> {
                    CachedMediaImage(
                        message = message,
                        api = vm.api,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
                codeLang != null -> {
                    CodeMessageBody(
                        message = message,
                        language = codeLang,
                        chatId = chatId,
                        vm = vm,
                        darkTheme = darkTheme,
                        onDownloadFile = onDownloadFile,
                    )
                }
                message.messageType == "voice" -> {
                    val voiceUrl = message.contentUrl
                    if (!voiceUrl.isNullOrBlank()) {
                        VoiceMessagePlayer(
                            url = voiceUrl,
                            waveform = message.waveform,
                            recordedDurationMs = message.voiceDurationMs,
                            foreground = fg,
                            modifier = Modifier.widthIn(min = 220.dp, max = 290.dp),
                        )
                    } else {
                        Text("Голосовое сообщение", color = fg)
                    }
                }
                message.messageType == "file" -> {
                    FileMessageBody(
                        message = message,
                        fg = fg,
                        onDownloadFile = onDownloadFile,
                    )
                }
                else -> Text(message.content, color = fg)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    TimeFormat.messageTime(message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.7f),
                )
                if (delivery != null) {
                    MessageDeliveryStatus(
                        status = delivery,
                        color = when (delivery) {
                            DeliveryStatus.Read -> if (codeLang != null) chrome.accent else fg
                            else -> fg.copy(alpha = 0.75f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageDeliveryStatus(
    status: DeliveryStatus,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (status) {
            DeliveryStatus.Sending -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = color,
                )
            }
            DeliveryStatus.Sent -> {
                Icon(
                    Icons.Outlined.Done,
                    contentDescription = status.label,
                    modifier = Modifier.size(14.dp),
                    tint = color,
                )
            }
            DeliveryStatus.Read -> {
                Icon(
                    Icons.Outlined.DoneAll,
                    contentDescription = status.label,
                    modifier = Modifier.size(14.dp),
                    tint = color,
                )
            }
        }
        Text(
            status.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun FileMessageBody(
    message: MessageItem,
    fg: Color,
    onDownloadFile: (path: String?, url: String?, name: String, mime: String?) -> Unit,
) {
    val name = message.fileName ?: "Файл"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onDownloadFile(
                    message.content.takeIf { it.isNotBlank() },
                    message.contentUrl,
                    name,
                    message.mimeType,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
        Text(
            name,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Icon(
            Icons.Outlined.Download,
            contentDescription = "Скачать",
            tint = fg.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CodeMessageBody(
    message: MessageItem,
    language: String,
    chatId: String,
    vm: MonicaViewModel,
    darkTheme: Boolean,
    onDownloadFile: (path: String?, url: String?, name: String, mime: String?) -> Unit,
) {
    var codeText by remember(message.id) { mutableStateOf<String?>(null) }
    var loadError by remember(message.id) { mutableStateOf<String?>(null) }
    var running by remember(message.id) { mutableStateOf(false) }
    var runResult by remember(message.id) { mutableStateOf<MonicaApi.CodeRunResult?>(null) }
    var runError by remember(message.id) { mutableStateOf<String?>(null) }
    val chrome = remember(darkTheme) { codeChrome(darkTheme) }

    LaunchedEffect(message.id, message.contentUrl, message.content) {
        if (message.contentUrl.isNullOrBlank() && message.content.isBlank()) {
            loadError = "Нет ссылки на файл"
            return@LaunchedEffect
        }
        loadError = null
        try {
            codeText = withContext(Dispatchers.IO) {
                vm.api.fetchMediaText(message.content, message.contentUrl)
            }
        } catch (e: Exception) {
            loadError = "Не удалось показать код"
        }
    }

    val label = message.fileName ?: if (language == "javascript") "script.js" else "script.py"
    val langLabel = if (language == "javascript") "JavaScript" else "Python"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                color = chrome.fg,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(langLabel, color = chrome.accent, style = MaterialTheme.typography.labelSmall)
            IconButton(
                onClick = {
                    onDownloadFile(
                        message.content.takeIf { it.isNotBlank() },
                        message.contentUrl,
                        label,
                        message.mimeType,
                    )
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = "Скачать",
                    tint = chrome.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            OutlinedButton(
                onClick = {
                    if (running) return@OutlinedButton
                    running = true
                    runError = null
                    runResult = null
                    vm.runCodeMessage(
                        chatId = chatId,
                        messageId = message.id,
                        onResult = {
                            runResult = it
                            running = false
                        },
                        onError = {
                            runError = it
                            running = false
                        },
                    )
                },
                enabled = !running,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        running -> "…"
                        runResult != null || runError != null -> "Снова"
                        else -> "Run"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loadError != null -> Text(loadError!!, color = chrome.error)
            codeText == null -> Text("Загрузка…", color = chrome.muted)
            else -> CodeViewerView(
                code = codeText!!,
                language = language,
                darkTheme = darkTheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        if (runError != null) {
            Spacer(Modifier.height(8.dp))
            Text(runError!!, color = chrome.error, style = MaterialTheme.typography.bodySmall)
        }
        runResult?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("exit ${result.exitCode}")
                    if (result.timedOut) append(" · timeout")
                    if (result.memoryExceeded) append(" · memory")
                },
                color = chrome.muted,
                style = MaterialTheme.typography.labelSmall,
            )
            if (result.stdout.isNotBlank()) {
                Text(
                    result.stdout,
                    color = chrome.fg,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (result.stderr.isNotBlank()) {
                Text(
                    result.stderr,
                    color = chrome.error,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

