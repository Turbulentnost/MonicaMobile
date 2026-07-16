package com.example.monica.data.ws

import com.example.monica.data.AppNotification
import com.example.monica.data.MonicaApi
import com.example.monica.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PresenceSocket(
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var pingJob: Job? = null
    private var retry = 0

    private val _onlineIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineIds: StateFlow<Set<String>> = _onlineIds.asStateFlow()

    private val _lastSeen = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastSeen: StateFlow<Map<String, String>> = _lastSeen.asStateFlow()

    private val _notifications = MutableSharedFlow<AppNotification>(extraBufferCapacity = 16)
    val notifications: SharedFlow<AppNotification> = _notifications.asSharedFlow()

    /** Превью last_message для списка чатов (без открытой комнаты). */
    private val _chatPreviews = MutableSharedFlow<org.json.JSONObject>(extraBufferCapacity = 32)
    val chatPreviews: SharedFlow<org.json.JSONObject> = _chatPreviews.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun connect() {
        disconnect(reconnect = false)
        val token = session.accessToken ?: return
        val request = Request.Builder()
            .url("${session.wsBaseUrl}/ws/presence/?token=$token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                retry = 0
                pingJob?.cancel()
                pingJob = scope.launch {
                    while (isActive) {
                        webSocket.send("""{"action":"presence.ping"}""")
                        delay(20_000)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (data.optString("action")) {
                    "presence.snapshot" -> {
                        val ids = mutableSetOf<String>()
                        val arr = data.optJSONArray("online_user_ids")
                        if (arr != null) {
                            for (i in 0 until arr.length()) ids.add(arr.get(i).toString())
                        }
                        _onlineIds.value = ids
                    }
                    "presence.update" -> {
                        val uid = data.optString("user_id")
                        val online = data.optBoolean("is_online")
                        _onlineIds.value = _onlineIds.value.toMutableSet().also {
                            if (online) it.add(uid) else it.remove(uid)
                        }
                        if (!online) {
                            val seen = data.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" }
                            if (seen != null) {
                                _lastSeen.value = _lastSeen.value + (uid to seen)
                            }
                        }
                    }
                    "notification.new" -> {
                        val n = data.optJSONObject("notification") ?: return
                        _notifications.tryEmit(MonicaApi.parseNotification(n))
                    }
                    "chat.preview" -> {
                        val msg = data.optJSONObject("message") ?: return
                        _chatPreviews.tryEmit(msg)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (session.accessToken.isNullOrBlank()) return
        if (retry >= 12) return
        val delayMs = minOf(1000L * (1L shl retry), 10_000L)
        retry += 1
        scope.launch {
            delay(delayMs)
            connect()
        }
    }

    fun disconnect(reconnect: Boolean = true) {
        pingJob?.cancel()
        pingJob = null
        socket?.close(1000, null)
        socket = null
        _connected.value = false
        if (!reconnect) retry = 99
    }

    fun isOnline(userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        return _onlineIds.value.contains(userId)
    }
}

class ChatSocket(
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var chatId: String? = null

    private val _messages = MutableSharedFlow<org.json.JSONObject>(extraBufferCapacity = 32)
    val messages: SharedFlow<org.json.JSONObject> = _messages.asSharedFlow()

    private val _typing = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 8)
    val typing: SharedFlow<Pair<String, Boolean>> = _typing.asSharedFlow()

    private val _deleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val deleted: SharedFlow<String> = _deleted.asSharedFlow()

    data class MessagesReadEvent(
        val messageIds: List<String>,
        val readerId: String,
        val readAt: String,
    )

    private val _reads = MutableSharedFlow<MessagesReadEvent>(extraBufferCapacity = 16)
    val reads: SharedFlow<MessagesReadEvent> = _reads.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun connect(chatId: String) {
        disconnect()
        this.chatId = chatId
        val token = session.accessToken ?: return
        val request = Request.Builder()
            .url("${session.wsBaseUrl}/ws/chat/$chatId/?token=$token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (data.optString("action")) {
                    "message.new", "chat.message" -> {
                        val msg = data.optJSONObject("message") ?: data
                        _messages.tryEmit(msg)
                    }
                    "message.deleted" -> {
                        val id = data.optString("message_id").ifBlank {
                            data.optString("id")
                        }
                        if (id.isNotBlank()) _deleted.tryEmit(id)
                    }
                    "messages.read" -> {
                        val idsJson = data.optJSONArray("message_ids")
                        val ids = buildList {
                            if (idsJson != null) {
                                for (i in 0 until idsJson.length()) {
                                    val id = idsJson.optString(i)
                                    if (id.isNotBlank()) add(id)
                                }
                            }
                        }
                        if (ids.isNotEmpty()) {
                            _reads.tryEmit(
                                MessagesReadEvent(
                                    messageIds = ids,
                                    readerId = data.optString("reader_id"),
                                    readAt = data.optString("read_at"),
                                ),
                            )
                        }
                    }
                    "typing.update" -> {
                        val uid = data.optString("user_id")
                        _typing.tryEmit(uid to data.optBoolean("is_typing"))
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
            }
        })
    }

    fun sendText(content: String, clientId: String? = null): Boolean {
        val ws = socket ?: return false
        if (!_connected.value) return false
        val payload = JSONObject()
            .put("action", "message.send")
            .put("content", content)
            .put("message_type", "text")
        if (!clientId.isNullOrBlank()) payload.put("client_id", clientId)
        return ws.send(payload.toString())
    }

    fun sendFile(
        path: String,
        messageType: String,
        fileName: String?,
        mimeType: String?,
        fileSize: Long?,
        clientId: String? = null,
    ): Boolean {
        val ws = socket ?: return false
        if (!_connected.value) return false
        val payload = JSONObject()
            .put("action", "message.send")
            .put("content", path)
            .put("message_type", messageType)
            .put("file_name", fileName ?: "")
            .put("mime_type", mimeType ?: "")
        if (fileSize != null) payload.put("file_size", fileSize)
        if (!clientId.isNullOrBlank()) payload.put("client_id", clientId)
        return ws.send(payload.toString())
    }

    fun markRead(messageIds: List<String>? = null) {
        val ws = socket ?: return
        if (!_connected.value) return
        val payload = JSONObject().put("action", "messages.read")
        if (messageIds != null) {
            val arr = org.json.JSONArray()
            messageIds.forEach { arr.put(it) }
            payload.put("message_ids", arr)
        }
        ws.send(payload.toString())
    }

    fun sendTyping(isTyping: Boolean) {
        val ws = socket ?: return
        if (!_connected.value) return
        val payload = JSONObject()
            .put("action", if (isTyping) "typing.start" else "typing.stop")
        ws.send(payload.toString())
    }

    fun disconnect() {
        socket?.close(1000, null)
        socket = null
        chatId = null
        _connected.value = false
    }
}

class PrivateSocket(
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    private val _peerText = MutableStateFlow("")
    val peerText: StateFlow<String> = _peerText.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    private var retryCount = 0

    fun connect(sessionId: String) {
        disconnect(clearRetry = false)
        _peerText.value = ""
        val token = session.accessToken ?: return
        val request = Request.Builder()
            .url("${session.wsBaseUrl}/ws/private/$sessionId/?token=$token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                retryCount = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (data.optString("action")) {
                    "private.ready" -> _connected.value = true
                    "private.peer_text" -> _peerText.value = data.optString("text")
                    "private.closed" -> {
                        _closed.tryEmit(Unit)
                        disconnect()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                if (retryCount >= 5) return
                retryCount += 1
                scope.launch(Dispatchers.IO) {
                    delay(300L * retryCount)
                    if (session.accessToken != null) {
                        connect(sessionId)
                    }
                }
            }
        })
    }

    fun sync(text: String) {
        val ws = socket ?: return
        if (!_connected.value) return
        val payload = JSONObject()
            .put("action", "private.sync")
            .put("text", text)
        ws.send(payload.toString())
    }

    fun disconnect(clearRetry: Boolean = true) {
        socket?.close(1000, null)
        socket = null
        _connected.value = false
        if (clearRetry) retryCount = 0
    }
}
