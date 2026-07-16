package com.example.monica.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.R
import com.example.monica.data.DeliveryStatus
import com.example.monica.data.MessageItem
import com.example.monica.data.MonicaApi
import com.example.monica.ui.MonicaViewModel
import com.example.monica.ui.components.AppIcon
import com.example.monica.ui.components.CachedMediaImage
import com.example.monica.ui.components.CodeViewerView
import com.example.monica.ui.components.MonicaAppBar
import com.example.monica.ui.components.MonacoEditorView
import com.example.monica.ui.components.UserAvatar
import com.example.monica.ui.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    vm: MonicaViewModel,
    onBack: () -> Unit,
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
    val myId = vm.session.userId

    val chat = chats.find { it.id == chatId }
    val partner = chat?.partner
    val isOnline = onlineIds.contains(partner?.id)
    val lastSeen = lastSeenMap[partner?.id] ?: partner?.lastSeenAt
    val statusText = if (isOnline) "в сети" else TimeFormat.lastSeen(lastSeen)
    val incomingInvite = incomingInvites[chatId]
    val isOutgoingPending = outgoingPending.containsKey(chatId)
    val isPrivateActiveHere = privateSessionId != null && privateChatId == chatId

    var input by remember { mutableStateOf("") }
    var codeMode by remember { mutableStateOf(false) }
    var codeLanguage by remember { mutableStateOf("python") }
    var codeFileName by remember { mutableStateOf("script.py") }
    var codeText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollToBottomTick by vm.scrollChatToBottom.collectAsStateWithLifecycle()

    DisposableEffect(chatId) {
        vm.openChat(chatId)
        onDispose { vm.leaveChat() }
    }

    LaunchedEffect(chatId, messages.lastOrNull()?.id, scrollToBottomTick) {
        if (messages.isEmpty()) return@LaunchedEffect
        // ждём, пока LazyColumn отрисует day-группы + сообщения
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.scrollToItem(lastIndex)
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

    val dayGroups = remember(messages) {
        messages.groupBy { TimeFormat.dayKey(it.sentAt) }
            .entries
            .map { (key, msgs) ->
                key to (TimeFormat.dayLabel(msgs.firstOrNull()?.sentAt) to msgs)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            codeMode = true
                            if (codeText.isBlank()) {
                                codeLanguage = "python"
                                codeFileName = "script.py"
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = "Режим кода")
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            vm.onComposerChange(it)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение…") },
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            vm.sendMessage(input)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
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
                    size = 28.dp,
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
                    size = 26.dp,
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
                    size = 26.dp,
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

@Composable
private fun MessageBubble(
    message: MessageItem,
    isOwn: Boolean,
    chatId: String,
    vm: MonicaViewModel,
    darkTheme: Boolean,
) {
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

    Column(
        modifier = Modifier.fillMaxWidth(),
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
                    )
                }
                message.messageType == "file" -> {
                    Text(
                        message.fileName ?: "Файл",
                        color = fg,
                        fontWeight = FontWeight.Medium,
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
private fun CodeMessageBody(
    message: MessageItem,
    language: String,
    chatId: String,
    vm: MonicaViewModel,
    darkTheme: Boolean,
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

