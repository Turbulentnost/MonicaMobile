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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.InsertEmoticon
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.monica.BuildConfig
import com.example.monica.R
import com.example.monica.data.CallUiStatus
import com.example.monica.data.DeliveryStatus
import com.example.monica.data.MediaUrls
import com.example.monica.data.MessageItem
import com.example.monica.data.MonicaApi
import com.example.monica.data.isVideoMime
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.AppIcon
import com.example.monica.ui.components.AttachmentPickerSheet
import com.example.monica.ui.components.CodeViewerView
import com.example.monica.ui.components.EmojiPicker
import com.example.monica.ui.components.ForwardedBundleView
import com.example.monica.ui.components.ForwardPickerSheet
import com.example.monica.ui.components.LinkAwareText
import com.example.monica.ui.components.MessagePhotoGallery
import com.example.monica.ui.components.MessageSelectionToolbar
import com.example.monica.ui.components.MonicaAppBar
import com.example.monica.ui.components.PhotoLightbox
import com.example.monica.ui.components.PhotoViewerItem
import com.example.monica.ui.components.UploadProgressOverlay
import com.example.monica.ui.components.UserAvatar
import com.example.monica.ui.components.VideoPlayerDialog
import com.example.monica.ui.components.VoiceMessagePlayer
import com.example.monica.ui.components.VoiceRecorderController
import com.example.monica.ui.components.formatVoiceDuration
import com.example.monica.ui.components.rememberChatFileDownloader
import com.example.monica.ui.components.toPhotoViewerItems
import com.example.monica.ui.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.io.File
import android.net.Uri

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
    val selectedMessageIds by vm.selectedMessageIds.collectAsStateWithLifecycle()
    val pendingForward by vm.pendingForward.collectAsStateWithLifecycle()
    val replyTo by vm.replyTo.collectAsStateWithLifecycle()
    val forwardBusy by vm.forwardBusy.collectAsStateWithLifecycle()
    val forwardSearchResults by vm.searchResults.collectAsStateWithLifecycle()
    val aiStyleEnabled by vm.aiStyleEnabled.collectAsStateWithLifecycle()
    val aiReasonActive by vm.aiReasonActive.collectAsStateWithLifecycle()
    val aiSuggestion by vm.aiSuggestion.collectAsStateWithLifecycle()
    val aiLoading by vm.aiLoading.collectAsStateWithLifecycle()
    val myId = vm.session.userId
    val context = LocalContext.current
    val activePendingForward = pendingForward?.takeIf { it.targetChatId == chatId }

    val chat = chats.find { it.id == chatId }
    val isGroup = chat?.isGroup == true
    val partner = chat?.partner
    val isOnline = onlineIds.contains(partner?.id)
    val lastSeen = lastSeenMap[partner?.id] ?: partner?.lastSeenAt
    val statusText = when {
        isGroup -> {
            val count = chat?.membersCount ?: 0
            if (count > 0) "$count участников" else "Группа"
        }
        isOnline -> "в сети"
        else -> TimeFormat.lastSeen(lastSeen)
    }
    val headerTitle = chat?.displayTitle ?: "—"
    val headerAvatar = chat?.avatarUser()
    val incomingInvite = incomingInvites[chatId]
    val isOutgoingPending = outgoingPending.containsKey(chatId)
    val isPrivateActiveHere = privateSessionId != null && privateChatId == chatId
    val callBusy = callState.status !in listOf(CallUiStatus.Idle, CallUiStatus.Ended)
    // Presence выключается, когда приложение собеседника свёрнуто, но
    // входящий звонок всё равно доставляется через FCM и фоновый демон.
    val canStartCall = !isGroup && !callBusy && partner != null
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
    var forwardPickerVisible by remember { mutableStateOf(false) }
    var forwardQuery by remember { mutableStateOf("") }
    var codeMode by remember { mutableStateOf(false) }
    var codeLanguage by remember { mutableStateOf("python") }
    var codeFileName by remember { mutableStateOf("script.py") }
    var codeText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollToBottomTick by vm.scrollChatToBottom.collectAsStateWithLifecycle()
    val pendingScrollToMessageId by vm.pendingScrollToMessageId.collectAsStateWithLifecycle()
    val highlightedMessageId by vm.highlightedMessageId.collectAsStateWithLifecycle()

    LaunchedEffect(activePendingForward?.targetChatId) {
        if (activePendingForward != null) input = ""
    }

    DisposableEffect(chatId) {
        vm.openChat(chatId)
        // Не вызываем leaveChat в onDispose: переход на экран деталей снимает ChatScreen
        // с композиции, но чат должен остаться открытым (поиск → jump to message).
        onDispose { }
    }

    LaunchedEffect(chatId) {
        vm.loadAiStyle()
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
                        UserAvatar(
                            headerAvatar,
                            size = 30.dp,
                            showOnline = !isGroup,
                            isOnline = isOnline,
                        )
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                headerTitle,
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
                    if (!isGroup) {
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
                    }
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
                            selectionMode = selectedMessageIds.isNotEmpty(),
                            selected = msg.id in selectedMessageIds,
                            onToggleSelection = { vm.toggleMessageSelection(msg) },
                            onLongPress = { vm.enterMessageSelection(msg) },
                            onSwipeReply = { vm.beginReply(msg) },
                            onOpenOriginal = { originalChatId, originalMessageId ->
                                vm.openOriginalMessage(originalChatId, originalMessageId)
                            },
                            onDownloadFile = downloadChatFile,
                        )
                    }
                }
            }

            if (partnerTyping) {
                Text(
                    "${partner?.displayName ?: "Собеседник"} печатает…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (selectedMessageIds.isNotEmpty()) {
                MessageSelectionToolbar(
                    count = selectedMessageIds.size,
                    onClose = vm::clearMessageSelection,
                    onReply = vm::replyToSelectedMessage,
                    onForward = { forwardPickerVisible = true },
                )
            } else if (codeMode) {
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
                        vm.onAiDraftChange(it, chatId)
                    },
                    loading = loading || forwardBusy,
                    forwardingMode = activePendingForward != null,
                    forwardPreview = activePendingForward?.preview,
                    replyPreview = replyTo,
                    onCancelForward = {
                        vm.cancelPendingForward()
                        input = ""
                        vm.onAiDraftChange("", chatId)
                    },
                    onCancelReply = vm::cancelReply,
                    onSendText = {
                        if (activePendingForward != null) {
                            vm.completePendingForward(input) { input = "" }
                        } else {
                            vm.sendMessage(input)
                            input = ""
                        }
                        vm.onAiDraftChange("", chatId)
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
                    onSendLocationText = { locationText ->
                        vm.sendMessage(locationText)
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
                    aiStyleEnabled = aiStyleEnabled,
                    aiReasonActive = aiReasonActive,
                    aiSuggestion = aiSuggestion,
                    aiLoading = aiLoading,
                    onToggleAiReason = { vm.toggleAiReason(input, chatId) },
                    onAcceptAiSuggestion = {
                        val next = vm.acceptAiSuggestion(input)
                        if (next != null) {
                            input = next
                            vm.onComposerChange(next)
                            vm.onAiDraftChange(next, chatId)
                        }
                    },
                )
            }
        }
    }

    if (forwardPickerVisible) {
        ForwardPickerSheet(
            chats = chats,
            searchResults = forwardSearchResults,
            query = forwardQuery,
            busy = forwardBusy,
            onQueryChange = {
                forwardQuery = it
                vm.searchUsers(it)
            },
            onSelectChat = { targetChatId ->
                forwardPickerVisible = false
                forwardQuery = ""
                vm.prepareForwardToChat(targetChatId)
            },
            onSelectUser = { userId ->
                forwardPickerVisible = false
                forwardQuery = ""
                vm.prepareForwardToUser(userId)
            },
            onDismiss = {
                forwardPickerVisible = false
                forwardQuery = ""
            },
        )
    }
}

@Composable
private fun MessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    loading: Boolean,
    forwardingMode: Boolean = false,
    forwardPreview: MessageItem? = null,
    replyPreview: MessageItem? = null,
    onCancelForward: () -> Unit = {},
    onCancelReply: () -> Unit = {},
    onSendText: () -> Unit,
    onOpenCode: () -> Unit,
    onSendFile: (name: String, bytes: ByteArray, mime: String) -> Unit,
    onSendLocationText: (String) -> Unit = {},
    onSendVoice: (file: File, waveform: List<Float>, durationMs: Long) -> Unit,
    aiStyleEnabled: Boolean = false,
    aiReasonActive: Boolean = false,
    aiSuggestion: String = "",
    aiLoading: Boolean = false,
    onToggleAiReason: () -> Unit = {},
    onAcceptAiSuggestion: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorderController(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var emojiPickerVisible by remember { mutableStateOf(false) }
    var attachmentSheetVisible by remember { mutableStateOf(false) }
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

    fun sendUri(uri: Uri, mimeHint: String?) {
        scope.launch {
            val resolver = context.contentResolver
            val name = withContext(Dispatchers.IO) {
                resolver.query(
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
            }
            val mime = mimeHint?.takeIf { it.isNotBlank() }
                ?: resolver.getType(uri)
                ?: "application/octet-stream"
            val bytes = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes != null) onSendFile(name, bytes, mime)
        }
    }

    if (attachmentSheetVisible) {
        AttachmentPickerSheet(
            onDismiss = { attachmentSheetVisible = false },
            onSendUris = { items ->
                items.forEach { (uri, mime) -> sendUri(uri, mime) }
            },
            onSendLocationText = onSendLocationText,
        )
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
        val quotePreview = forwardPreview ?: replyPreview
        if (quotePreview != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (forwardingMode) {
                                "Пересылка от ${quotePreview.sender?.displayName.orEmpty()}"
                            } else {
                                "Ответ для ${quotePreview.sender?.displayName.orEmpty()}"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            messagePreviewText(quotePreview),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = if (forwardingMode) onCancelForward else onCancelReply,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Отменить")
                    }
                }
            }
        }
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
                    onClick = {
                        emojiPickerVisible = false
                        attachmentSheetVisible = true
                    },
                    enabled = !loading && !recording && quotePreview == null,
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
                    placeholder = {
                        Text(
                            when {
                                forwardingMode -> "Добавить комментарий…"
                                replyPreview != null -> "Напишите ответ…"
                                else -> "Сообщение"
                            },
                        )
                    },
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
                    enabled = !loading && !recording && quotePreview == null,
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
                    enabled = !loading && !recording && quotePreview == null,
                    modifier = Modifier.size(44.dp),
                ) {
                    AppIcon(
                        resId = R.drawable.ic_code,
                        contentDescription = "Написать код",
                        size = 28.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (aiStyleEnabled && !recording) {
                    IconButton(
                        onClick = {
                            emojiPickerVisible = false
                            onToggleAiReason()
                        },
                        enabled = !loading,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = if (aiReasonActive) {
                                    "Выключить Reason"
                                } else {
                                    "Включить Reason"
                                },
                                modifier = Modifier.size(24.dp),
                                tint = when {
                                    aiReasonActive || aiLoading -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (aiLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                if (text.isNotBlank() || forwardingMode) {
                    IconButton(
                        onClick = onSendText,
                        enabled = !loading,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (forwardingMode) "Переслать" else "Отправить",
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

        val showAiSuggestion = aiStyleEnabled && aiReasonActive && aiSuggestion.isNotBlank() && !recording
        AnimatedVisibility(visible = showAiSuggestion) {
            val holdProgress = remember(aiSuggestion) { Animatable(0f) }
            val acceptLatest by rememberUpdatedState(onAcceptAiSuggestion)
            val holdScope = rememberCoroutineScope()
            val progress = holdProgress.value
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .pointerInput(aiSuggestion) {
                        detectTapGestures(
                            onPress = {
                                holdProgress.snapTo(0f)
                                val animJob = holdScope.launch {
                                    holdProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 2000),
                                    )
                                    acceptLatest()
                                    holdProgress.snapTo(0f)
                                }
                                tryAwaitRelease()
                                if (animJob.isActive) {
                                    animJob.cancel()
                                    holdProgress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 160),
                                    )
                                }
                            },
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.55f + 0.35f * progress,
                ),
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Удерживайте 2 сек, чтобы принять",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.7f + 0.3f * progress,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = aiSuggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f + 0.62f * progress,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { holdProgress.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
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
    val tabSize = if (language == "javascript") 2 else 4
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val editorHeight = if (imeVisible) 132.dp else 180.dp
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = code, selection = TextRange(code.length)))
    }
    var lastSpaceAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(code) {
        if (code != fieldValue.text) {
            fieldValue = TextFieldValue(text = code, selection = TextRange(code.length))
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(chrome.bg)
            .padding(12.dp),
    ) {
        if (!imeVisible) {
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
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Двойной пробел — отступ · отправить кнопкой ниже",
                color = chrome.muted,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(editorHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(if (darkTheme) Color(0xFF252526) else Color(0xFFF3F3F3))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (fieldValue.text.isEmpty()) {
                Text(
                    "Введите код…",
                    color = chrome.muted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { next ->
                    val processed = applyDoubleSpaceIndent(
                        previous = fieldValue,
                        next = next,
                        tabSize = tabSize,
                        lastSpaceAt = lastSpaceAt,
                        onSpaceTyped = { lastSpaceAt = it },
                    )
                    fieldValue = processed
                    onCodeChange(processed.text)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = chrome.fg,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(chrome.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Default,
                ),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Отмена", color = chrome.fg)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = code.isNotBlank() && fileName.isNotBlank() && !loading,
            ) {
                Text(if (loading) "Отправка…" else "Отправить код")
            }
        }
    }
}

/** Двойной пробел подряд → отступ (вместо Tab на мобильной клавиатуре). */
private fun applyDoubleSpaceIndent(
    previous: TextFieldValue,
    next: TextFieldValue,
    tabSize: Int,
    lastSpaceAt: Long,
    onSpaceTyped: (Long) -> Unit,
): TextFieldValue {
    if (next.text.length != previous.text.length + 1) {
        onSpaceTyped(0L)
        return next
    }
    val cursor = next.selection.start
    if (cursor <= 0 || next.text.getOrNull(cursor - 1) != ' ') {
        onSpaceTyped(0L)
        return next
    }
    val now = System.currentTimeMillis()
    if (lastSpaceAt > 0L && now - lastSpaceAt < 400L && cursor >= 2 &&
        next.text.getOrNull(cursor - 2) == ' '
    ) {
        val indent = " ".repeat(tabSize.coerceAtLeast(2))
        val before = next.text.substring(0, cursor - 2)
        val after = next.text.substring(cursor)
        val text = before + indent + after
        val sel = before.length + indent.length
        onSpaceTyped(0L)
        return TextFieldValue(text = text, selection = TextRange(sel))
    }
    onSpaceTyped(now)
    return next
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
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Открыть секретный чат",
                    modifier = Modifier.size(25.dp),
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

private fun messagePreviewText(message: MessageItem): String = when (message.messageType) {
    "photo" -> message.caption ?: "Фотография"
    "voice" -> "Голосовое сообщение"
    "file" -> if (isVideoMime(message.mimeType, message.fileName)) {
        message.fileName ?: "Видео"
    } else {
        message.fileName ?: "Файл"
    }
    "forward" -> message.content.ifBlank { "Пересланные сообщения" }
    else -> message.content
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
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
    onOpenOriginal: (chatId: String, messageId: String) -> Unit,
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
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        } else if (highlighted) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
        } else {
            Color.Transparent
        },
        label = "messageHighlight",
    )
    var photoLightbox by remember(message.id) {
        mutableStateOf<Pair<List<PhotoViewerItem>, Int>?>(null)
    }

    photoLightbox?.let { (items, index) ->
        PhotoLightbox(
            items = items,
            initialIndex = index,
            api = vm.api,
            onDismiss = { photoLightbox = null },
            onDownload = onDownloadFile,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(highlightColor, RoundedCornerShape(18.dp))
            .messageForwardGestures(
                isOwn = isOwn,
                selectionMode = selectionMode,
                onTap = onToggleSelection,
                onLongPress = onLongPress,
                onSwipeReply = onSwipeReply,
            )
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = selectionMode && !isOwn,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MessageSelectionCircle(selected = selected, onClick = onToggleSelection)
                Spacer(Modifier.width(6.dp))
            }
        }
        Column(
            modifier = Modifier
                .then(if (codeLang != null) Modifier.fillMaxWidth(0.95f) else Modifier)
                .widthIn(max = if (codeLang != null) 420.dp else 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg.copy(alpha = if (codeLang != null) 1f else bubbleAlpha))
                .padding(10.dp),
        ) {
            Text(
                message.sender?.displayName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            message.replyToSummary?.let { reply ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (reply.chatId.isNotBlank() && reply.id.isNotBlank()) {
                                onOpenOriginal(reply.chatId, reply.id)
                            }
                        },
                    color = fg.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "Ответ для ${reply.sender?.displayName.orEmpty()}",
                            color = fg,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            reply.preview,
                            color = fg.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            when {
                message.messageType == "photo" -> {
                    MessagePhotoGallery(
                        message = message,
                        api = vm.api,
                        contentColor = fg,
                        onLongPress = onLongPress,
                        onOpen = { index ->
                            if (selectionMode) {
                                onToggleSelection()
                            } else if (!message.isUploading) {
                                photoLightbox = message.toPhotoViewerItems() to index
                            }
                        },
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
                message.messageType == "file" && isVideoMime(message.mimeType, message.fileName) -> {
                    VideoMessageBody(
                        message = message,
                        onDownloadFile = onDownloadFile,
                    )
                }
                message.messageType == "file" -> {
                    FileMessageBody(
                        message = message,
                        fg = fg,
                        onDownloadFile = onDownloadFile,
                    )
                }
                message.messageType == "forward" -> {
                    ForwardedBundleView(
                        bundle = message.forwardBundle,
                        comment = message.content,
                        api = vm.api,
                        foreground = fg,
                        onOpenOriginal = onOpenOriginal,
                        onDownloadFile = onDownloadFile,
                    )
                }
                else -> LinkAwareText(
                    text = message.content,
                    color = fg,
                    linkColor = if (isOwn) Color(0xFFDCEBFF) else Color(0xFF3B82F6),
                )
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
        AnimatedVisibility(
            visible = selectionMode && isOwn,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                MessageSelectionCircle(selected = selected, onClick = onToggleSelection)
            }
        }
    }
}

@Composable
private fun MessageSelectionCircle(
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector = if (selected) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = if (selected) "Снять выбор" else "Выбрать",
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun Modifier.messageForwardGestures(
    isOwn: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
): Modifier = pointerInput(isOwn, selectionMode) {
    val swipeThreshold = 76.dp.toPx()
    val moveTolerance = 14.dp.toPx()
    coroutineScope {
        val gestureScope = this
        awaitEachGesture {
            // Ссылки в тексте сами обрабатывают long-press (копирование).
            val down = awaitFirstDown(requireUnconsumed = true)
            var latestX = down.position.x
            var moved = false
            var longPressed = false
            val longPressJob = gestureScope.launch {
                delay(500)
                if (!moved) {
                    longPressed = true
                    onLongPress()
                }
            }
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: event.changes.firstOrNull()
                if (change != null) {
                    latestX = change.position.x
                    if (abs(latestX - down.position.x) > moveTolerance) {
                        moved = true
                        longPressJob.cancel()
                    }
                }
                pressed = event.changes.any { it.pressed }
            }
            longPressJob.cancel()
            if (longPressed) return@awaitEachGesture
            val delta = latestX - down.position.x
            val validSwipe = if (isOwn) delta <= -swipeThreshold else delta >= swipeThreshold
            when {
                validSwipe -> onSwipeReply()
                !moved && selectionMode -> onTap()
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
private fun VideoMessageBody(
    message: MessageItem,
    onDownloadFile: (path: String?, url: String?, name: String, mime: String?) -> Unit,
) {
    val context = LocalContext.current
    var playerOpen by remember(message.id) { mutableStateOf(false) }
    val videoLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val thumbUrl = MediaUrls.resolve(BuildConfig.API_BASE_URL, message.contentUrl)

    if (playerOpen) {
        VideoPlayerDialog(
            apiBaseUrl = BuildConfig.API_BASE_URL.trimEnd('/'),
            fileName = message.fileName,
            objectPath = message.content.takeIf { it.isNotBlank() },
            contentUrl = message.contentUrl,
            mimeType = message.mimeType,
            onDismiss = { playerOpen = false },
            onDownload = onDownloadFile,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = !message.isUploading) { playerOpen = true },
        contentAlignment = Alignment.Center,
    ) {
        if (!thumbUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbUrl)
                    .videoFrameMillis(0)
                    .crossfade(true)
                    .build(),
                imageLoader = videoLoader,
                contentDescription = message.fileName ?: "Видео",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = "Смотреть",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        if (message.isUploading) {
            UploadProgressOverlay(
                progress = message.uploadProgress,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
        Text(
            text = message.fileName ?: "Видео",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            maxLines = 1,
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
    val uploading = message.isUploading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (uploading) Modifier else Modifier.clickable {
                    onDownloadFile(
                        message.content.takeIf { it.isNotBlank() },
                        message.contentUrl,
                        name,
                        message.mimeType,
                    )
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
            if (uploading) {
                UploadProgressOverlay(
                    progress = message.uploadProgress,
                    dim = false,
                    color = fg,
                    trackColor = fg.copy(alpha = 0.25f),
                    indicatorSize = 34.dp,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = fg,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            if (uploading) {
                val pct = ((message.uploadProgress ?: 0f) * 100).toInt().coerceIn(0, 100)
                Text(
                    if (pct <= 0) "Загрузка…" else "Загрузка $pct%",
                    color = fg.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (!uploading) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Скачать",
                tint = fg.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }
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

