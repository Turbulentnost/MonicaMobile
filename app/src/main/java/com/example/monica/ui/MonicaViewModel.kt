package com.example.monica.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.monica.data.AppNotification
import com.example.monica.data.AppUpdateChecker
import com.example.monica.data.AppUpdateInfo
import com.example.monica.data.AppUpdateInstaller
import com.example.monica.data.AvatarCache
import com.example.monica.data.CallUiState
import com.example.monica.data.CallUiStatus
import com.example.monica.data.ChatBackgroundCache
import com.example.monica.data.ChatSummary
import com.example.monica.data.MediaImageCache
import com.example.monica.data.MessageItem
import com.example.monica.data.MonicaApi
import com.example.monica.data.PendingForward
import com.example.monica.data.PrivateNavTarget
import com.example.monica.data.ReplySummary
import com.example.monica.data.SessionStore
import com.example.monica.data.UserProfile
import com.example.monica.data.call.CallController
import com.example.monica.data.isPendingPrivateInvite
import com.example.monica.data.ws.ChatSocket
import com.example.monica.data.ws.PresenceHub
import com.example.monica.data.ws.PrivateSocket
import com.example.monica.push.MonicaDaemonService
import com.example.monica.push.PushRegistrar
import kotlinx.coroutines.CancellationException
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

    private val presence = PresenceHub.get(app)
    private val chatSocket = ChatSocket(session, viewModelScope)
    private val privateSocket = PrivateSocket(session, viewModelScope)
    val callController = CallController(
        appContext = app.applicationContext,
        api = api,
        session = session,
        scope = viewModelScope,
    )

    val callState: StateFlow<CallUiState> = callController.state

    private val _darkTheme = MutableStateFlow(session.darkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _loggedIn = MutableStateFlow(session.isLoggedIn)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds.asStateFlow()

    private val _pendingForward = MutableStateFlow<PendingForward?>(null)
    val pendingForward: StateFlow<PendingForward?> = _pendingForward.asStateFlow()

    private val _replyTo = MutableStateFlow<MessageItem?>(null)
    val replyTo: StateFlow<MessageItem?> = _replyTo.asStateFlow()

    private val _forwardBusy = MutableStateFlow(false)
    val forwardBusy: StateFlow<Boolean> = _forwardBusy.asStateFlow()

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

    /** Прокрутка к сообщению из поиска в деталях чата */
    private val _pendingScrollToMessageId = MutableStateFlow<String?>(null)
    val pendingScrollToMessageId: StateFlow<String?> = _pendingScrollToMessageId.asStateFlow()

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId.asStateFlow()

    private val _myPrivateText = MutableStateFlow("")
    val myPrivateText: StateFlow<String> = _myPrivateText.asStateFlow()

    val peerPrivateText = privateSocket.peerText
    val privateConnected = privateSocket.connected
    val chatConnected = chatSocket.connected

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _profileSaving = MutableStateFlow(false)
    val profileSaving: StateFlow<Boolean> = _profileSaving.asStateFlow()

    private val _inviteBanner = MutableStateFlow<AppNotification?>(null)
    val inviteBanner: StateFlow<AppNotification?> = _inviteBanner.asStateFlow()

    private val _aiStyleEnabled = MutableStateFlow(true)
    val aiStyleEnabled: StateFlow<Boolean> = _aiStyleEnabled.asStateFlow()

    private val _aiReasonActive = MutableStateFlow(false)
    val aiReasonActive: StateFlow<Boolean> = _aiReasonActive.asStateFlow()

    private val _aiSuggestion = MutableStateFlow("")
    val aiSuggestion: StateFlow<String> = _aiSuggestion.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _chatBackgroundBusy = MutableStateFlow(false)
    val chatBackgroundBusy: StateFlow<Boolean> = _chatBackgroundBusy.asStateFlow()

    private val updateChecker = AppUpdateChecker(session)
    private val updateInstaller = AppUpdateInstaller(app)

    private val _appUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdate: StateFlow<AppUpdateInfo?> = _appUpdate.asStateFlow()

    /** Баннер сверху; после свайпа/таймаута скрывается, но update остаётся для кнопки в меню. */
    private val _updateBannerVisible = MutableStateFlow(false)
    val updateBannerVisible: StateFlow<Boolean> = _updateBannerVisible.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    /** Soft-dismiss в рамках сессии — не пишем в prefs, чтобы кнопка «Обновить» осталась в меню. */
    private var softDismissedUpdateVersionCode: Int = 0

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
    private var aiCompleteJob: Job? = null
    private var lastFetchedAiDraft: String = ""
    private var lastFetchedAiChatId: String? = null
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var pendingUpdateInstallPermission = false

    init {
        if (session.isLoggedIn) {
            startRealtime()
            refreshChats()
            refreshNotifications()
            loadCurrentUser()
            callController.restoreActiveCallIfAny()
        }
        viewModelScope.launch {
            presence.callEvents.collect { event ->
                callController.onCallEvent(event)
            }
        }
        viewModelScope.launch {
            var lastCallError: String? = null
            callController.state.collect { state ->
                val msg = state.error?.takeIf { it.isNotBlank() }
                if (msg != null && msg != lastCallError) {
                    lastCallError = msg
                    _error.value = msg
                } else if (msg == null) {
                    lastCallError = null
                }
            }
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
                // Снимаем точку непрочитанного в списке чатов.
                if (idSet.isNotEmpty()) {
                    _chats.update { list ->
                        list.map { chat ->
                            val lm = chat.lastMessage ?: return@map chat
                            if (lm.id in idSet && lm.readAt.isNullOrBlank()) {
                                chat.copy(lastMessage = lm.copy(readAt = event.readAt))
                            } else {
                                chat
                            }
                        }
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
                    // Как на вебе: при открытии чата помечаем бэклог прочитанным.
                    chatSocket.markRead()
                    clearUnreadPreview(chatId)
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

    fun login(login: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // Старые refresh-токены не должны участвовать в логине (иначе 401→retry давал 400).
                session.clearAuth()
                val result = withContext(Dispatchers.IO) {
                    api.login(login, password)
                }
                session.saveLogin(result.access, result.refresh, result.userId, result.nickname)
                _loggedIn.value = true
                startRealtime()
                refreshChats()
                refreshNotifications()
                loadCurrentUser()
                callController.restoreActiveCallIfAny()
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

    fun sendRegistrationCode(email: String, onSuccess: (debugCode: String?) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val debugCode = withContext(Dispatchers.IO) {
                    api.registerEmail(email)
                }
                onSuccess(debugCode)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun verifyRegistrationCode(
        email: String,
        code: String,
        onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val token = withContext(Dispatchers.IO) {
                    api.verifyRegistrationCode(email, code)
                }
                onSuccess(token)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun saveRegistrationProfile(
        registrationToken: String,
        firstName: String,
        lastName: String,
        password: String,
        nickname: String,
        city: String,
        birthDate: String? = null,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    api.registerProfile(
                        registrationToken = registrationToken,
                        firstName = firstName,
                        lastName = lastName,
                        password = password,
                        nickname = nickname,
                        city = city,
                        birthDate = birthDate,
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun completeRegistration(
        registrationToken: String,
        avatarUri: Uri? = null,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    if (avatarUri != null) {
                        val resolver = getApplication<Application>().contentResolver
                        val mimeType = resolver.getType(avatarUri) ?: "image/jpeg"
                        val fileName = resolver.query(avatarUri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex >= 0) {
                                cursor.getString(nameIndex)
                            } else {
                                null
                            }
                        } ?: "avatar.jpg"
                        val bytes = resolver.openInputStream(avatarUri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Не удалось прочитать фото")
                        api.registerAvatar(
                            registrationToken = registrationToken,
                            photoBytes = bytes,
                            fileName = fileName,
                            mimeType = mimeType,
                        )
                    }
                    api.completeRegistration(registrationToken)
                }
                session.saveLogin(result.access, result.refresh, result.userId, result.nickname)
                _loggedIn.value = true
                startRealtime()
                refreshChats()
                refreshNotifications()
                loadCurrentUser()
                callController.restoreActiveCallIfAny()
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

    fun loadCurrentUser(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val user = withContext(Dispatchers.IO) { api.me() }
                _currentUser.value = user
                if (user.nickname.isNotBlank()) {
                    session.nickname = user.nickname
                }
                AvatarCache.warm(getApplication(), session, user)
            } catch (e: Exception) {
                if (_currentUser.value == null) {
                    _error.value = e.message
                }
            } finally {
                onDone?.invoke()
            }
        }
    }

    fun saveAccountProfile(
        firstName: String,
        lastName: String,
        city: String,
        birthDate: String?,
        avatarUri: Uri?,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _profileSaving.value = true
            _error.value = null
            try {
                val previous = _currentUser.value
                val updated = withContext(Dispatchers.IO) {
                    var user = api.updateProfile(
                        firstName = firstName,
                        lastName = lastName,
                        city = city,
                        birthDate = birthDate,
                    )
                    if (avatarUri != null) {
                        val resolver = getApplication<Application>().contentResolver
                        val mimeType = resolver.getType(avatarUri) ?: "image/jpeg"
                        if (!ALLOWED_AVATAR_MIME.contains(mimeType.lowercase())) {
                            throw IllegalStateException("Поддерживаются JPG, PNG, WEBP и GIF")
                        }
                        val fileName = resolver.query(avatarUri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex >= 0) {
                                cursor.getString(nameIndex)
                            } else {
                                null
                            }
                        } ?: "avatar.jpg"
                        val bytes = resolver.openInputStream(avatarUri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Не удалось прочитать фото")
                        if (bytes.size > MAX_AVATAR_BYTES) {
                            throw IllegalStateException("Файл больше 10 МБ")
                        }
                        user = api.updateAvatar(
                            photoBytes = bytes,
                            fileName = fileName,
                            mimeType = mimeType,
                        )
                    }
                    user
                }
                AvatarCache.invalidate(getApplication(), AvatarCache.cacheKey(previous))
                AvatarCache.invalidate(getApplication(), AvatarCache.cacheKey(updated))
                AvatarCache.warm(getApplication(), session, updated)
                _currentUser.value = updated
                if (updated.nickname.isNotBlank()) {
                    session.nickname = updated.nickname
                }
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _profileSaving.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            when (callController.state.value.status) {
                CallUiStatus.Incoming -> callController.rejectCall()
                CallUiStatus.Outgoing -> callController.cancelCall()
                CallUiStatus.Connecting, CallUiStatus.Active -> callController.hangup()
                else -> Unit
            }
            withContext(Dispatchers.IO) { api.leavePrivate() }
            stopRealtime()
            session.clearAuth()
            _loggedIn.value = false
            _currentUser.value = null
            _chats.value = emptyList()
            _messages.value = emptyList()
            _notifications.value = emptyList()
            _outgoingPending.value = emptyMap()
            _inviteBanner.value = null
            closePrivateLocal()
        }
    }

    fun startCall(chatId: String, mediaMode: String = "audio") =
        callController.startCall(chatId, mediaMode)

    fun acceptCall() = callController.acceptCall()
    fun rejectCall() = callController.rejectCall()
    fun cancelCall() = callController.cancelCall()
    fun hangupCall() = callController.hangup()
    fun toggleCallMute() = callController.toggleMute()
    fun cycleCallAudioRoute() = callController.cycleAudioRoute()
    fun toggleCallCamera() = callController.toggleCamera()
    fun switchCallCamera() = callController.switchCamera()
    fun upgradeCallToVideo() = callController.upgradeToVideo()
    fun hasCameraDevice(): Boolean = callController.hasCameraDevice()

    fun handleIncomingCallIntent(
        callId: String,
        chatId: String,
        mediaMode: String,
        callerId: String,
        callerNickname: String,
        action: String?,
    ) {
        if (callId.isBlank()) return
        callController.presentIncomingFromPush(
            callId = callId,
            chatId = chatId,
            mediaMode = mediaMode,
            callerId = callerId,
            callerNickname = callerNickname,
        )
        if (chatId.isNotBlank()) {
            openChatFromNotification(chatId)
        }
        when (action) {
            com.example.monica.push.CallNotificationHelper.ACTION_ACCEPT -> acceptCall()
            com.example.monica.push.CallNotificationHelper.ACTION_REJECT -> rejectCall()
            else -> Unit
        }
    }

    private fun startRealtime() {
        PresenceHub.ensureConnected(getApplication())
        MonicaDaemonService.start(getApplication())
    }

    private fun stopRealtime() {
        MonicaDaemonService.stop(getApplication())
        PresenceHub.disconnect()
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
                        AvatarCache.warm(getApplication(), session, chat.avatarUser())
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
        // Если чат уже открыт и это чужое сообщение — сразу считаем прочитанным в превью.
        val preview = if (
            chatId == currentChatId &&
            msg.sender?.id != null &&
            msg.sender.id != session.userId &&
            msg.readAt.isNullOrBlank()
        ) {
            msg.copy(readAt = msg.sentAt.ifBlank { "read" })
        } else {
            msg
        }
        _chats.update { list ->
            val current = list.find { it.id == chatId } ?: return@update list
            val updated = current.copy(
                lastMessage = preview,
                updatedAt = preview.sentAt.ifBlank { current.updatedAt },
            )
            listOf(updated) + list.filterNot { it.id == chatId }
        }
    }

    /** Оптимистично убрать точку непрочитанного у открытого чата. */
    private fun clearUnreadPreview(chatId: String) {
        _chats.update { list ->
            list.map { chat ->
                if (chat.id != chatId) return@map chat
                val lm = chat.lastMessage ?: return@map chat
                if (lm.sender?.id == session.userId || !lm.readAt.isNullOrBlank()) return@map chat
                chat.copy(lastMessage = lm.copy(readAt = lm.sentAt.ifBlank { "read" }))
            }
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

    fun setChatBackground(
        chatId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        onDone: () -> Unit = {},
    ) {
        if (chatId.isBlank() || bytes.isEmpty()) return
        viewModelScope.launch {
            _chatBackgroundBusy.value = true
            try {
                val uploaded = withContext(Dispatchers.IO) {
                    api.uploadChatBackground(chatId, bytes, fileName, mimeType)
                }
                withContext(Dispatchers.IO) {
                    ChatBackgroundCache.putBytes(
                        getApplication(),
                        chatId,
                        bytes,
                        uploaded.objectPath,
                        uploaded.updatedAt,
                    )
                }
                _chats.update { list ->
                    list.map { chat ->
                        if (chat.id != chatId) chat else chat.copy(
                            backgroundMobile = uploaded.objectPath.ifBlank { null },
                            backgroundMobileUrl = uploaded.contentUrl,
                            backgroundMobileUpdatedAt = uploaded.updatedAt,
                        )
                    }
                }
                onDone()
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось загрузить фон"
            } finally {
                _chatBackgroundBusy.value = false
            }
        }
    }

    fun clearChatBackground(chatId: String, onDone: () -> Unit = {}) {
        if (chatId.isBlank()) return
        viewModelScope.launch {
            _chatBackgroundBusy.value = true
            try {
                withContext(Dispatchers.IO) {
                    api.deleteChatBackground(chatId)
                    ChatBackgroundCache.invalidate(getApplication(), chatId)
                }
                _chats.update { list ->
                    list.map { chat ->
                        if (chat.id != chatId) chat else chat.copy(
                            backgroundMobile = null,
                            backgroundMobileUrl = null,
                            backgroundMobileUpdatedAt = null,
                        )
                    }
                }
                onDone()
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось сбросить фон"
            } finally {
                _chatBackgroundBusy.value = false
            }
        }
    }

    fun createGroup(
        title: String,
        memberIds: List<String>,
        photoBytes: ByteArray? = null,
        photoFileName: String = "group.jpg",
        photoMime: String = "image/jpeg",
        onCreated: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val chat = withContext(Dispatchers.IO) {
                    api.createGroup(
                        title = title,
                        memberIds = memberIds,
                        photoBytes = photoBytes,
                        photoFileName = photoFileName,
                        photoMime = photoMime,
                    )
                }
                _chats.value = listOf(chat) + _chats.value.filter { it.id != chat.id }
                withContext(Dispatchers.IO) {
                    AvatarCache.warm(getApplication(), session, chat.avatarUser())
                }
                refreshChats()
                onCreated(chat.id)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun openChat(chatId: String) {
        if (chatId.isBlank()) return
        // Повторный вход в тот же чат (например после экрана деталей) — не сбрасываем историю.
        if (currentChatId == chatId) {
            com.example.monica.data.AppVisibility.setOpenChatId(chatId)
            chatSocket.connect(chatId)
            if (_messages.value.isEmpty()) {
                openChatJob?.cancel()
                openChatJob = viewModelScope.launch {
                    refreshOpenChatMessages(chatId, _pendingScrollToMessageId.value)
                }
            }
            return
        }
        openChatJob?.cancel()
        currentChatId = chatId
        resetAiReason()
        com.example.monica.data.AppVisibility.setOpenChatId(chatId)
        _partnerTyping.value = false
        _messages.value = emptyList()
        _pendingScrollToMessageId.value = null
        _highlightedMessageId.value = null
        clearUnreadPreview(chatId)
        chatSocket.connect(chatId)
        openChatJob = viewModelScope.launch {
            refreshOpenChatMessages(chatId, _pendingScrollToMessageId.value)
            if (chatSocket.connected.value) {
                chatSocket.markRead()
            }
        }
    }

    fun openOriginalMessage(chatId: String, messageId: String) {
        if (chatId.isBlank() || messageId.isBlank()) return
        _pendingScrollToMessageId.value = messageId
        _highlightedMessageId.value = messageId
        _pendingChatNav.value = chatId
        refreshChats()
        viewModelScope.launch {
            delay(2_200)
            if (_highlightedMessageId.value == messageId) {
                _highlightedMessageId.value = null
            }
        }
    }

    fun enterMessageSelection(message: MessageItem) {
        if (!messageCanBeSelected(message)) return
        _replyTo.value = null
        _pendingForward.value = null
        _selectedMessageIds.value = setOf(message.id)
    }

    fun selectSingleForForward(message: MessageItem) {
        enterMessageSelection(message)
    }

    fun toggleMessageSelection(message: MessageItem) {
        if (!messageCanBeSelected(message)) return
        _selectedMessageIds.update { current ->
            if (message.id in current) current - message.id else current + message.id
        }
    }

    fun clearMessageSelection() {
        _selectedMessageIds.value = emptySet()
    }

    fun cancelPendingForward() {
        _pendingForward.value = null
    }

    fun beginReply(message: MessageItem) {
        if (!messageCanBeSelected(message)) return
        _pendingForward.value = null
        _replyTo.value = message
        clearMessageSelection()
    }

    fun replyToSelectedMessage() {
        val id = _selectedMessageIds.value.singleOrNull() ?: return
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        beginReply(message)
    }

    fun cancelReply() {
        _replyTo.value = null
    }

    private fun messageCanBeSelected(message: MessageItem): Boolean =
        !message.isPending && message.messageType != "call"

    fun prepareForwardToChat(targetChatId: String) {
        val sourceChatId = currentChatId ?: return
        val selected = _messages.value.filter { it.id in _selectedMessageIds.value }
        if (selected.isEmpty() || _forwardBusy.value) return
        _replyTo.value = null
        val ids = selected.map { it.id }
        if (ids.size == 1) {
            _pendingForward.value = PendingForward(
                sourceChatId = sourceChatId,
                targetChatId = targetChatId,
                messageIds = ids,
                preview = selected.first(),
            )
            clearMessageSelection()
            openChatFromNotification(targetChatId)
            return
        }
        _forwardBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.forwardMessages(targetChatId, sourceChatId, ids)
                }
                clearMessageSelection()
                openChatFromNotification(targetChatId)
                refreshChats()
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось переслать сообщения"
            } finally {
                _forwardBusy.value = false
            }
        }
    }

    fun prepareForwardToUser(userId: String) {
        if (_forwardBusy.value) return
        _forwardBusy.value = true
        viewModelScope.launch {
            try {
                val target = withContext(Dispatchers.IO) { api.startChat(userId) }
                _forwardBusy.value = false
                refreshChats()
                prepareForwardToChat(target.id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось открыть чат"
                _forwardBusy.value = false
            }
        }
    }

    fun completePendingForward(comment: String, onSuccess: () -> Unit = {}) {
        val pending = _pendingForward.value ?: return
        if (_forwardBusy.value) return
        _forwardBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.forwardMessages(
                        targetChatId = pending.targetChatId,
                        sourceChatId = pending.sourceChatId,
                        messageIds = pending.messageIds,
                        comment = comment.trim(),
                    )
                }
                _pendingForward.value = null
                refreshOpenChatMessages(pending.targetChatId)
                refreshChats()
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось переслать сообщение"
            } finally {
                _forwardBusy.value = false
            }
        }
    }

    fun jumpToMessage(chatId: String, messageId: String) {
        if (chatId.isBlank() || messageId.isBlank()) return
        viewModelScope.launch {
            try {
                val inMemory = _messages.value.any { it.id == messageId }
                if (!inMemory) {
                    val window = withContext(Dispatchers.IO) {
                        api.listMessages(chatId = chatId, around = messageId, limit = 100)
                    }
                    if (currentChatId == chatId) {
                        _messages.value = window
                    }
                }
                _pendingScrollToMessageId.value = messageId
                _highlightedMessageId.value = messageId
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
        viewModelScope.launch {
            delay(2200)
            if (_highlightedMessageId.value == messageId) {
                _highlightedMessageId.value = null
            }
        }
    }

    fun clearPendingScrollToMessage() {
        _pendingScrollToMessageId.value = null
    }

    private suspend fun refreshOpenChatMessages(chatId: String, around: String? = null) {
        try {
            val msgs = withContext(Dispatchers.IO) {
                api.listMessages(chatId = chatId, around = around)
            }
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
        resetAiReason()
        com.example.monica.data.AppVisibility.setOpenChatId(null)
        clearMessageSelection()
        _replyTo.value = null
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
        val reply = _replyTo.value
        val ok = enqueueOutgoing(
            content = content,
            messageType = "text",
            replyToSummary = reply?.let {
                ReplySummary(
                    id = it.id,
                    chatId = it.chatId.orEmpty(),
                    sender = it.sender,
                    preview = when (it.messageType) {
                        "photo" -> it.caption ?: "Фото"
                        "voice" -> "Голосовое сообщение"
                        "file" -> it.fileName ?: "Файл"
                        else -> it.content
                    }.take(160),
                    messageType = it.messageType,
                )
            },
        ) { clientId ->
            chatSocket.sendText(content, clientId, reply?.id)
        }
        if (ok) _replyTo.value = null
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
            val clientId = UUID.randomUUID().toString()
            val tempId = "temp-$clientId"
            val safeMime = mimeType.ifBlank { "application/octet-stream" }
            val guessedType = when {
                safeMime.startsWith("image/") -> "photo"
                safeMime.startsWith("audio/") || waveform.isNotEmpty() || voiceDurationMs != null -> "voice"
                else -> "file"
            }
            val localKey = "local-$clientId"
            val localPreviewPath = if (guessedType == "photo") {
                withContext(Dispatchers.IO) {
                    MediaImageCache.putBytes(getApplication(), localKey, bytes)?.absolutePath
                }
            } else null
            val me = UserProfile(
                id = session.userId.orEmpty(),
                nickname = session.nickname.orEmpty(),
            )
            val optimistic = MessageItem(
                id = tempId,
                chatId = currentChatId,
                sender = me,
                messageType = guessedType,
                content = if (guessedType == "photo") localKey else "",
                contentUrl = null,
                fileName = fileName,
                mimeType = safeMime,
                fileSize = bytes.size.toLong(),
                waveform = waveform,
                voiceDurationMs = voiceDurationMs,
                sentAt = isoNow(),
                readAt = null,
                clientId = clientId,
                clientStatus = "sending",
                uploadProgress = 0f,
                localPreviewPath = localPreviewPath,
            )
            _messages.update { it + optimistic }
            applyChatPreview(optimistic)

            fun patchTemp(transform: (MessageItem) -> MessageItem) {
                _messages.update { list ->
                    list.map { if (it.id == tempId) transform(it) else it }
                }
            }

            try {
                var lastStep = -1
                val uploaded = withContext(Dispatchers.IO) {
                    api.uploadFile(chatId, fileName, bytes, safeMime) { progress ->
                        val step = (progress * 40).toInt()
                        if (step != lastStep || progress >= 1f) {
                            lastStep = step
                            patchTemp { it.copy(uploadProgress = progress.coerceIn(0f, 1f)) }
                        }
                    }
                }
                if (guessedType == "photo" || uploaded.messageType == "photo") {
                    withContext(Dispatchers.IO) {
                        MediaImageCache.alias(getApplication(), localKey, uploaded.path)
                    }
                }
                patchTemp {
                    it.copy(
                        content = uploaded.path,
                        contentUrl = uploaded.contentUrl,
                        fileName = uploaded.fileName,
                        mimeType = uploaded.mimeType,
                        fileSize = uploaded.fileSize,
                        messageType = uploaded.messageType,
                        uploadProgress = null,
                    )
                }
                val ok = chatSocket.sendFile(
                    path = uploaded.path,
                    messageType = uploaded.messageType,
                    fileName = uploaded.fileName,
                    mimeType = uploaded.mimeType,
                    fileSize = uploaded.fileSize,
                    waveform = waveform,
                    voiceDurationMs = voiceDurationMs,
                    clientId = clientId,
                )
                if (!ok) {
                    _messages.update { list -> list.filterNot { it.id == tempId } }
                    _error.value = "Файл загружен, но WebSocket отключился"
                }
                onDone(ok)
            } catch (e: Exception) {
                _messages.update { list -> list.filterNot { it.id == tempId } }
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
        replyToSummary: ReplySummary? = null,
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
            replyToSummary = replyToSummary,
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

    fun loadAiStyle() {
        viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { api.getAiStyle() }
                _aiStyleEnabled.value = profile.enabled
                if (!profile.enabled) {
                    resetAiReason()
                }
            } catch (_: Exception) {
                _aiStyleEnabled.value = true
            }
        }
    }

    fun toggleAiReason(draft: String = "", chatId: String? = null) {
        if (!_aiStyleEnabled.value) return
        if (_aiReasonActive.value) {
            resetAiReason()
            return
        }
        lastFetchedAiDraft = ""
        lastFetchedAiChatId = null
        _aiReasonActive.value = true
        if (draft.isNotBlank()) {
            scheduleAiComplete(draft, chatId)
        }
    }

    fun onAiDraftChange(draft: String, chatId: String?) {
        scheduleAiComplete(draft, chatId)
    }

    fun acceptAiSuggestion(draft: String): String? {
        val suggestion = _aiSuggestion.value.takeIf { it.isNotBlank() } ?: return null
        val next = draft + suggestion
        _aiSuggestion.value = ""
        lastFetchedAiDraft = next
        lastFetchedAiChatId = currentChatId
        return next
    }

    fun clearAiSuggestion() {
        aiCompleteJob?.cancel()
        aiCompleteJob = null
        _aiSuggestion.value = ""
        _aiLoading.value = false
        lastFetchedAiDraft = ""
        lastFetchedAiChatId = null
    }

    private fun resetAiReason() {
        clearAiSuggestion()
        _aiReasonActive.value = false
    }

    private fun scheduleAiComplete(draft: String, chatId: String?) {
        if (!_aiStyleEnabled.value || !_aiReasonActive.value || draft.isBlank()) {
            aiCompleteJob?.cancel()
            aiCompleteJob = null
            _aiSuggestion.value = ""
            _aiLoading.value = false
            return
        }
        if (draft == lastFetchedAiDraft && chatId == lastFetchedAiChatId) {
            return
        }

        aiCompleteJob?.cancel()
        _aiSuggestion.value = ""
        _aiLoading.value = false
        aiCompleteJob = viewModelScope.launch {
            delay(AI_COMPLETE_DEBOUNCE_MS)
            if (!_aiStyleEnabled.value || !_aiReasonActive.value || draft.isBlank()) {
                return@launch
            }
            _aiLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    api.aiComplete(draft, chatId)
                }
                if (!_aiReasonActive.value || !_aiStyleEnabled.value) return@launch
                lastFetchedAiDraft = draft
                lastFetchedAiChatId = chatId
                when {
                    result.disabled -> {
                        _aiStyleEnabled.value = false
                        resetAiReason()
                    }
                    result.rateLimited || result.error -> {
                        _aiSuggestion.value = ""
                    }
                    else -> {
                        _aiSuggestion.value = result.suggestion
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _aiSuggestion.value = ""
            } finally {
                _aiLoading.value = false
            }
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

    fun onMainActivityResumed() {
        if (pendingUpdateInstallPermission && updateInstaller.canRequestPackageInstalls()) {
            pendingUpdateInstallPermission = false
            startUpdateDownload()
            return
        }
        checkAppUpdate()
    }

    fun checkAppUpdate() {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    updateChecker.check()
                }
                _appUpdate.value = update
                if (update == null) {
                    _updateBannerVisible.value = false
                } else if (
                    softDismissedUpdateVersionCode != update.versionCode &&
                    _updateDownloadProgress.value == null
                ) {
                    _updateBannerVisible.value = true
                    // Прогрев DNS/TLS/CDN GitHub до клика «Обновить».
                    if (update.apkUrl.isNotBlank()) {
                        launch(Dispatchers.IO) {
                            runCatching { updateInstaller.warmUp(update.apkUrl) }
                        }
                    }
                }
            } catch (_: Exception) {
                // Update checks are best-effort and should not interrupt chat startup.
            }
        }
    }

    /** Смахнули / автоскрытие — баннер уходит, кнопка в боковом меню остаётся. */
    fun dismissUpdateBanner() {
        val update = _appUpdate.value
        if (update != null) {
            softDismissedUpdateVersionCode = update.versionCode
        }
        _updateBannerVisible.value = false
    }

    fun dismissUpdate() {
        val update = _appUpdate.value ?: return
        session.dismissedUpdateVersionCode = update.versionCode
        softDismissedUpdateVersionCode = update.versionCode
        pendingUpdateInstallPermission = false
        _updateDownloadProgress.value = null
        _updateBannerVisible.value = false
        _appUpdate.value = null
    }

    fun startUpdateDownload() {
        val update = _appUpdate.value ?: return
        if (updateDownloadJob?.isActive == true) return
        // Баннер уезжает вверх; прогресс показываем снизу над футером.
        _updateBannerVisible.value = false
        softDismissedUpdateVersionCode = update.versionCode
        // Пока APK ещё не загружен в GitHub Release — открываем страницу релиза.
        if (update.apkUrl.isBlank()) {
            updateInstaller.openReleasePage(update.releaseUrl)
            _error.value = "APK ещё готовится — открыл страницу релиза"
            return
        }
        if (!updateInstaller.canRequestPackageInstalls()) {
            pendingUpdateInstallPermission = true
            updateInstaller.openInstallPermissionSettings()
            _error.value = "Разрешите установку Monica из этого источника и вернитесь в приложение"
            return
        }
        pendingUpdateInstallPermission = false
        updateDownloadJob = viewModelScope.launch {
            _updateDownloadProgress.value = 0f
            try {
                val apk = withContext(Dispatchers.IO) {
                    updateInstaller.downloadApk(update) { progress ->
                        _updateDownloadProgress.value = progress
                    }
                }
                updateInstaller.startInstall(apk)
            } catch (e: Exception) {
                val cancelled = e is CancellationException ||
                    e is java.util.concurrent.CancellationException ||
                    e.message == "download cancelled"
                if (!cancelled) {
                    _error.value = e.message ?: "Не удалось скачать обновление"
                }
            } finally {
                _updateDownloadProgress.value = null
            }
        }
    }

    fun cancelUpdateDownload() {
        updateInstaller.cancelDownload()
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        pendingUpdateInstallPermission = false
        _updateDownloadProgress.value = null
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
        callController.clearError()
    }

    fun chatById(id: String): ChatSummary? = _chats.value.find { it.id == id }

    fun isUserOnline(userId: String?): Boolean = presence.isOnline(userId)

    override fun onCleared() {
        // Presence + daemon продолжают жить в фоне — иначе при закрытии UI звонки пропадут.
        callController.dispose()
        chatSocket.disconnect()
        privateSocket.disconnect()
        super.onCleared()
    }

    companion object {
        private const val AI_COMPLETE_DEBOUNCE_MS = 450L
        private const val MAX_AVATAR_BYTES = 10 * 1024 * 1024
        private val ALLOWED_AVATAR_MIME = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
        )
    }
}
