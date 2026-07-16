package com.example.monica.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.monica.data.AppNotification
import com.example.monica.data.AvatarCache
import com.example.monica.data.ChatSummary
import com.example.monica.data.MessageItem
import com.example.monica.data.MonicaApi
import com.example.monica.data.PrivateNavTarget
import com.example.monica.data.SessionStore
import com.example.monica.data.UserProfile
import com.example.monica.data.isPendingPrivateInvite
import com.example.monica.data.ws.ChatSocket
import com.example.monica.data.ws.PresenceSocket
import com.example.monica.data.ws.PrivateSocket
import com.example.monica.push.PushRegistrar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class MonicaViewModel(app: Application) : AndroidViewModel(app) {
    val session = SessionStore(app)
    val api = MonicaApi(session)

    private val presence = PresenceSocket(session, viewModelScope)
    private val chatSocket = ChatSocket(session, viewModelScope)
    private val privateSocket = PrivateSocket(session, viewModelScope)

    private val _darkTheme = MutableStateFlow(session.darkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _loggedIn = MutableStateFlow(session.isLoggedIn)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _onlineIds = presence.onlineIds
    val onlineIds: StateFlow<Set<String>> = _onlineIds

    private val _lastSeen = presence.lastSeen
    val lastSeenMap: StateFlow<Map<String, String>> = _lastSeen

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()

    private val _partnerTyping = MutableStateFlow(false)
    val partnerTyping: StateFlow<Boolean> = _partnerTyping.asStateFlow()

    private val _privateSessionId = MutableStateFlow<String?>(null)
    val privateSessionId: StateFlow<String?> = _privateSessionId.asStateFlow()

    private val _privateChatId = MutableStateFlow<String?>(null)
    val privateChatId: StateFlow<String?> = _privateChatId.asStateFlow()

    /** chatId → sessionId исходящего pending-приглашения */
    private val _outgoingPending = MutableStateFlow<Map<String, String>>(emptyMap())
    val outgoingPending: StateFlow<Map<String, String>> = _outgoingPending.asStateFlow()

    /** Навигация на экран приватного чата */
    private val _privateNav = MutableStateFlow<PrivateNavTarget?>(null)
    val privateNav: StateFlow<PrivateNavTarget?> = _privateNav.asStateFlow()

    /** Открыть обычный чат из push / deep link */
    private val _pendingChatNav = MutableStateFlow<String?>(null)
    val pendingChatNav: StateFlow<String?> = _pendingChatNav.asStateFlow()

    /** Триггер прокрутки вниз после открытия из уведомления */
    private val _scrollChatToBottom = MutableStateFlow(0L)
    val scrollChatToBottom: StateFlow<Long> = _scrollChatToBottom.asStateFlow()

    private val _myPrivateText = MutableStateFlow("")
    val myPrivateText: StateFlow<String> = _myPrivateText.asStateFlow()

    val peerPrivateText = privateSocket.peerText
    val privateConnected = privateSocket.connected
    val chatConnected = chatSocket.connected

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _inviteBanner = MutableStateFlow<AppNotification?>(null)
    val inviteBanner: StateFlow<AppNotification?> = _inviteBanner.asStateFlow()

    /** chatId → уведомление входящего invite */
    val incomingInvitesByChat: StateFlow<Map<String, AppNotification>> = _notifications
        .map { list ->
            list.filter { it.isPendingPrivateInvite() }
                .mapNotNull { n ->
                    val chatId = n.payload["chat_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    chatId to n
                }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var currentChatId: String? = null
    private var typingJob: Job? = null
    private var privateSyncJob: Job? = null
    private var openChatJob: Job? = null

    init {
        if (session.isLoggedIn) {
            startRealtime()
            refreshChats()
            refreshNotifications()
        }
        viewModelScope.launch {
            presence.notifications.collect { n ->
                _notifications.update { list ->
                    if (list.any { it.id == n.id }) list else listOf(n) + list
                }
                if (n.isPendingPrivateInvite()) {
                    _inviteBanner.value = n
                }
                if (n.type == "private_accepted") {
                    val sid = n.payload["session_id"]
                    val chatId = n.payload["chat_id"]
                    if (!sid.isNullOrBlank()) {
                        clearOutgoingForSession(sid)
                        openPrivate(sid, chatId)
                    }
                }
                if (n.type == "private_closed" || n.type == "private_declined" || n.type == "private_cancelled") {
                    val sid = n.payload["session_id"]
                    if (!sid.isNullOrBlank()) {
                        clearPendingForSession(sid, resolved = when (n.type) {
                            "private_declined" -> "declined"
                            "private_cancelled" -> "cancelled"
                            else -> "closed"
                        })
                        if (sid == _privateSessionId.value) {
                            closePrivateLocal()
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            var wasConnected = false
            presence.connected.collect { connected ->
                if (connected && !wasConnected && session.isLoggedIn) {
                    refreshNotifications()
                }
                wasConnected = connected
            }
        }
        viewModelScope.launch {
            presence.chatPreviews.collect { json ->
                val msg = MonicaApi.parseMessage(json)
                applyChatPreview(msg)
            }
        }
        viewModelScope.launch {
            chatSocket.messages.collect { json ->
                val msg = MonicaApi.parseMessage(json)
                val openId = currentChatId
                if (openId.isNullOrBlank()) return@collect
                if (!msg.chatId.isNullOrBlank() && msg.chatId != openId) return@collect
                _messages.update { list ->
                    val withoutTemp = if (!msg.clientId.isNullOrBlank()) {
                        list.filterNot {
                            it.clientId == msg.clientId || it.id == "temp-${msg.clientId}"
                        }
                    } else {
                        list
                    }
                    if (withoutTemp.any { it.id == msg.id }) withoutTemp else withoutTemp + msg
                }
                if (msg.sender?.id != null && msg.sender.id != session.userId && !msg.id.startsWith("temp-")) {
                    chatSocket.markRead(listOf(msg.id))
                }
                applyChatPreview(msg)
            }
        }
        viewModelScope.launch {
            chatSocket.reads.collect { event ->
                if (event.readAt.isBlank()) return@collect
                val idSet = event.messageIds.toSet()
                _messages.update { list ->
                    list.map { m ->
                        if (m.id in idSet) m.copy(readAt = event.readAt, clientStatus = null) else m
                    }
                }
            }
        }
        viewModelScope.launch {
            chatSocket.deleted.collect { id ->
                _messages.update { it.filterNot { m -> m.id == id } }
            }
        }
        viewModelScope.launch {
            chatSocket.typing.collect { (uid, typing) ->
                if (uid != session.userId) _partnerTyping.value = typing
            }
        }
        viewModelScope.launch {
            privateSocket.closed.collect { closePrivateLocal() }
        }
        viewModelScope.launch {
            var wasConnected = false
            chatSocket.connected.collect { connected ->
                val chatId = currentChatId
                if (connected && !wasConnected && !chatId.isNullOrBlank()) {
                    refreshOpenChatMessages(chatId)
                }
                wasConnected = connected
            }
        }
    }

    fun toggleTheme() {
        val next = !_darkTheme.value
        _darkTheme.value = next
        session.darkTheme = next
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    api.login(email, password)
                }
                session.saveLogin(result.access, result.refresh, result.userId, result.nickname)
                _loggedIn.value = true
                startRealtime()
                refreshChats()
                refreshNotifications()
                runCatching {
                    withContext(Dispatchers.IO) {
                        PushRegistrar.registerCurrentToken(getApplication())
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { api.leavePrivate() }
            stopRealtime()
            session.clearAuth()
            _loggedIn.value = false
            _chats.value = emptyList()
            _messages.value = emptyList()
            _notifications.value = emptyList()
            _outgoingPending.value = emptyMap()
            _inviteBanner.value = null
            closePrivateLocal()
        }
    }

    private fun startRealtime() {
        presence.connect()
    }

    private fun stopRealtime() {
        presence.disconnect(reconnect = false)
        chatSocket.disconnect()
        privateSocket.disconnect()
    }

    fun refreshChats() {
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { api.listChats() }
                _chats.value = list
                withContext(Dispatchers.IO) {
                    list.forEach { chat ->
                        AvatarCache.warm(getApplication(), session, chat.partner)
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Мгновенно обновить превью и поднять чат вверх (WS chat.preview / message.new). */
    private fun applyChatPreview(msg: MessageItem) {
        val chatId = msg.chatId?.takeIf { it.isNotBlank() } ?: return
        val exists = _chats.value.any { it.id == chatId }
        if (!exists) {
            refreshChats()
            return
        }
        _chats.update { list ->
            val current = list.find { it.id == chatId } ?: return@update list
            val updated = current.copy(
                lastMessage = msg,
                updatedAt = msg.sentAt.ifBlank { current.updatedAt },
            )
            listOf(updated) + list.filterNot { it.id == chatId }
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { api.listNotifications() }
                _notifications.value = list
                _inviteBanner.value = list.firstOrNull { it.isPendingPrivateInvite() }
            } catch (_: Exception) {
            }
        }
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            if (query.trim().length < 2) {
                _searchResults.value = emptyList()
                return@launch
            }
            try {
                val results = withContext(Dispatchers.IO) { api.searchUsers(query.trim()) }
                _searchResults.value = results
                withContext(Dispatchers.IO) {
                    results.forEach { AvatarCache.warm(getApplication(), session, it) }
                }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    fun startChatWith(userId: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val chat = withContext(Dispatchers.IO) { api.startChat(userId) }
                _searchResults.value = emptyList()
                refreshChats()
                onOpened(chat.id)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun openChat(chatId: String) {
        openChatJob?.cancel()
        currentChatId = chatId
        _partnerTyping.value = false
        _messages.value = emptyList()
        chatSocket.connect(chatId)
        openChatJob = viewModelScope.launch {
            refreshOpenChatMessages(chatId)
        }
    }

    private suspend fun refreshOpenChatMessages(chatId: String) {
        try {
            val msgs = withContext(Dispatchers.IO) { api.listMessages(chatId) }
            if (currentChatId != chatId) return
            _messages.update { live ->
                mergeMessages(msgs, live.filter {
                    it.chatId.isNullOrBlank() || it.chatId == chatId
                })
            }
            withContext(Dispatchers.IO) {
                msgs.mapNotNull { it.sender }.distinctBy { it.id }.forEach { sender ->
                    AvatarCache.warm(getApplication(), session, sender)
                }
            }
        } catch (e: Exception) {
            if (currentChatId == chatId) {
                _error.value = e.message
            }
        }
    }

    fun leaveChat() {
        openChatJob?.cancel()
        openChatJob = null
        chatSocket.disconnect()
        currentChatId = null
        _messages.value = emptyList()
        _partnerTyping.value = false
    }

    /** REST-снимок + live/optimistic сообщения, без потери новых за время загрузки. */
    private fun mergeMessages(
        fromApi: List<MessageItem>,
        live: List<MessageItem>,
    ): List<MessageItem> {
        if (live.isEmpty()) return fromApi
        val merged = LinkedHashMap<String, MessageItem>()
        fromApi.forEach { merged[it.id] = it }
        live.forEach { msg ->
            val existing = merged[msg.id]
            when {
                existing == null -> merged[msg.id] = msg
                !msg.readAt.isNullOrBlank() && existing.readAt.isNullOrBlank() -> {
                    merged[msg.id] = existing.copy(readAt = msg.readAt, clientStatus = null)
                }
            }
        }
        return merged.values.sortedWith(
            compareBy<MessageItem> { it.sentAt.ifBlank { "" } }.thenBy { it.id },
        )
    }

    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val ok = enqueueOutgoing(
            content = content,
            messageType = "text",
        ) { clientId ->
            chatSocket.sendText(content, clientId)
        }
        if (!ok) _error.value = "Нет соединения с чатом"
        stopTyping()
    }

    fun sendCodeFile(
        chatId: String,
        language: String,
        fileName: String,
        code: String,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val mime = if (language == "javascript") "text/javascript" else "text/x-python"
                var name = fileName.trim().ifBlank {
                    if (language == "javascript") "script.js" else "script.py"
                }
                val ext = if (language == "javascript") ".js" else ".py"
                if (!name.lowercase().endsWith(ext)) name += ext

                val uploaded = withContext(Dispatchers.IO) {
                    api.uploadCodeFile(chatId, name, code, mime)
                }
                val ok = enqueueOutgoing(
                    content = uploaded.path,
                    messageType = uploaded.messageType,
                    contentUrl = uploaded.contentUrl,
                    fileName = uploaded.fileName,
                    mimeType = uploaded.mimeType,
                    fileSize = uploaded.fileSize,
                ) { clientId ->
                    chatSocket.sendFile(
                        path = uploaded.path,
                        messageType = uploaded.messageType,
                        fileName = uploaded.fileName,
                        mimeType = uploaded.mimeType,
                        fileSize = uploaded.fileSize,
                        clientId = clientId,
                    )
                }
                if (!ok) {
                    _error.value = "Файл загружен, но WebSocket отключился"
                }
                onDone()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun sendUploadedFile(
        chatId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        waveform: List<Float> = emptyList(),
        voiceDurationMs: Long? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val uploaded = withContext(Dispatchers.IO) {
                    api.uploadFile(chatId, fileName, bytes, mimeType)
                }
                val ok = enqueueOutgoing(
                    content = uploaded.path,
                    messageType = uploaded.messageType,
                    contentUrl = uploaded.contentUrl,
                    fileName = uploaded.fileName,
                    mimeType = uploaded.mimeType,
                    fileSize = uploaded.fileSize,
                    waveform = waveform,
                    voiceDurationMs = voiceDurationMs,
                ) { clientId ->
                    chatSocket.sendFile(
                        path = uploaded.path,
                        messageType = uploaded.messageType,
                        fileName = uploaded.fileName,
                        mimeType = uploaded.mimeType,
                        fileSize = uploaded.fileSize,
                        waveform = waveform,
                        voiceDurationMs = voiceDurationMs,
                        clientId = clientId,
                    )
                }
                if (!ok) {
                    _error.value = "Файл загружен, но WebSocket отключился"
                }
                onDone(ok)
            } catch (e: Exception) {
                _error.value = e.message
                onDone(false)
            } finally {
                _loading.value = false
            }
        }
    }

    private fun enqueueOutgoing(
        content: String,
        messageType: String,
        contentUrl: String? = null,
        fileName: String? = null,
        mimeType: String? = null,
        fileSize: Long? = null,
        waveform: List<Float> = emptyList(),
        voiceDurationMs: Long? = null,
        send: (clientId: String) -> Boolean,
    ): Boolean {
        val clientId = UUID.randomUUID().toString()
        val me = UserProfile(
            id = session.userId.orEmpty(),
            nickname = session.nickname.orEmpty(),
        )
        val optimistic = MessageItem(
            id = "temp-$clientId",
            chatId = currentChatId,
            sender = me,
            messageType = messageType,
            content = content,
            contentUrl = contentUrl,
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            waveform = waveform,
            voiceDurationMs = voiceDurationMs,
            sentAt = isoNow(),
            readAt = null,
            clientId = clientId,
            clientStatus = "sending",
        )
        _messages.update { it + optimistic }
        applyChatPreview(optimistic)
        val ok = send(clientId)
        if (!ok) {
            _messages.update { list -> list.filterNot { it.id == optimistic.id } }
        }
        return ok
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    fun runCodeMessage(
        chatId: String,
        messageId: String,
        onResult: (MonicaApi.CodeRunResult) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    api.runCode(chatId, messageId)
                }
                onResult(result)
            } catch (e: Exception) {
                onError(e.message ?: "Ошибка запуска")
            }
        }
    }


    fun onComposerChange(text: String) {
        if (text.isBlank()) {
            stopTyping()
            return
        }
        chatSocket.sendTyping(true)
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(1500)
            chatSocket.sendTyping(false)
        }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        chatSocket.sendTyping(false)
    }

    fun invitePrivate(chatId: String) {
        if (_outgoingPending.value.containsKey(chatId)) return
        if (incomingInvitesByChat.value.containsKey(chatId)) return
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { api.invitePrivate(chatId) }
                when {
                    info.status == "active" || info.handshake -> {
                        clearOutgoingForSession(info.id)
                        openPrivate(info.id, info.chatId ?: chatId)
                    }
                    info.status == "pending" -> {
                        _outgoingPending.update { it + (chatId to info.id) }
                    }
                    else -> {
                        _outgoingPending.update { it + (chatId to info.id) }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun cancelOutgoingInvite(chatId: String) {
        val sessionId = _outgoingPending.value[chatId] ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.closePrivate(sessionId) }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                clearPendingForSession(sessionId, resolved = "cancelled")
            }
        }
    }

    fun openPrivate(sessionId: String, chatId: String? = null) {
        val resolvedChatId = chatId?.takeIf { it.isNotBlank() }
            ?: _outgoingPending.value.entries.find { it.value == sessionId }?.key
            ?: _notifications.value.find { it.payload["session_id"] == sessionId }?.payload?.get("chat_id")
            ?: currentChatId
            ?: _privateChatId.value

        val alreadyOpen = _privateSessionId.value == sessionId
        _privateSessionId.value = sessionId
        if (!resolvedChatId.isNullOrBlank()) {
            _privateChatId.value = resolvedChatId
        }
        if (!alreadyOpen) {
            _myPrivateText.value = ""
        }
        clearOutgoingForSession(sessionId)
        resolveInvitesForSession(sessionId, "accepted")
        if (_inviteBanner.value?.payload?.get("session_id") == sessionId) {
            _inviteBanner.value = null
        }
        if (!alreadyOpen) {
            privateSocket.connect(sessionId)
        }
        if (!resolvedChatId.isNullOrBlank() && !alreadyOpen) {
            _privateNav.value = PrivateNavTarget(sessionId, resolvedChatId)
        }
    }

    fun consumePrivateNav() {
        _privateNav.value = null
    }

    fun openChatFromNotification(chatId: String?) {
        val id = chatId?.trim()?.takeIf { it.isNotBlank() } ?: return
        _pendingChatNav.value = id
        _scrollChatToBottom.value = System.currentTimeMillis()
        refreshChats()
    }

    fun consumePendingChatNav() {
        _pendingChatNav.value = null
    }

    fun reopenPrivate() {
        val sid = _privateSessionId.value ?: return
        val cid = _privateChatId.value ?: return
        _privateNav.value = PrivateNavTarget(sid, cid)
    }

    fun updateMyPrivateText(text: String) {
        _myPrivateText.value = text
        privateSyncJob?.cancel()
        privateSyncJob = viewModelScope.launch {
            delay(40)
            privateSocket.sync(text)
        }
    }

    fun closePrivate() {
        val sid = _privateSessionId.value
        viewModelScope.launch {
            if (sid != null) {
                runCatching { withContext(Dispatchers.IO) { api.closePrivate(sid) } }
            }
            closePrivateLocal()
        }
    }

    private fun closePrivateLocal() {
        privateSocket.disconnect()
        _privateSessionId.value = null
        _privateChatId.value = null
        _myPrivateText.value = ""
    }

    fun acceptInvite(notification: AppNotification) {
        val sessionId = notification.payload["session_id"] ?: return
        val chatId = notification.payload["chat_id"]
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.acceptPrivate(sessionId) }
                markRead(notification.id)
                resolveInvite(notification.id, "accepted")
                _inviteBanner.value = null
                openPrivate(sessionId, chatId)
            } catch (e: Exception) {
                openPrivate(sessionId, chatId)
                _error.value = e.message
            }
        }
    }

    fun acceptInviteForChat(chatId: String) {
        val notification = incomingInvitesByChat.value[chatId] ?: return
        acceptInvite(notification)
    }

    fun declineInvite(notification: AppNotification) {
        val sessionId = notification.payload["session_id"] ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.declinePrivate(sessionId) }
                markRead(notification.id)
                resolveInvite(notification.id, "declined")
                if (_inviteBanner.value?.id == notification.id) {
                    _inviteBanner.value = null
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun dismissInviteBanner() {
        _inviteBanner.value = null
    }

    fun outgoingSessionForChat(chatId: String): String? = _outgoingPending.value[chatId]

    fun incomingInviteForChat(chatId: String): AppNotification? = incomingInvitesByChat.value[chatId]

    private fun clearOutgoingForSession(sessionId: String) {
        _outgoingPending.update { map -> map.filterValues { it != sessionId } }
    }

    private fun resolveInvitesForSession(sessionId: String, resolved: String) {
        _notifications.update { list ->
            list.map {
                if (it.type == "private_invite" &&
                    it.payload["session_id"] == sessionId &&
                    it.payload["resolved"].isNullOrBlank()
                ) {
                    it.copy(
                        isRead = true,
                        payload = it.payload + ("resolved" to resolved),
                    )
                } else {
                    it
                }
            }
        }
    }

    private fun clearPendingForSession(sessionId: String, resolved: String) {
        clearOutgoingForSession(sessionId)
        resolveInvitesForSession(sessionId, resolved)
        if (_inviteBanner.value?.payload?.get("session_id") == sessionId) {
            _inviteBanner.value = null
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.markNotificationRead(id) } }
            _notifications.update { list ->
                list.map { if (it.id == id) it.copy(isRead = true) else it }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.markAllNotificationsRead() } }
            _notifications.update { list -> list.map { it.copy(isRead = true) } }
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.clearNotifications() } }
            _notifications.value = emptyList()
        }
    }

    private fun resolveInvite(id: String, resolved: String) {
        _notifications.update { list ->
            list.map {
                if (it.id == id) it.copy(
                    isRead = true,
                    payload = it.payload + ("resolved" to resolved),
                ) else it
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun chatById(id: String): ChatSummary? = _chats.value.find { it.id == id }

    fun isUserOnline(userId: String?): Boolean = presence.isOnline(userId)

    override fun onCleared() {
        stopRealtime()
        super.onCleared()
    }
}
