package com.example.monica.data.ws

import com.example.monica.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Сигналинг WebRTC: `/ws/call/{callId}/`.
 * Передаёт offer/answer/ICE; lifecycle (ring/accept) идёт через presence.
 */
class CallSocket(
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var callId: String? = null
    private var reconnectJob: Job? = null
    private var retry = 0
    private var allowReconnect = false
    private var openContinuation: Continuation<Unit>? = null

    private val _signals = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val signals: SharedFlow<JSONObject> = _signals.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    suspend fun connect(callId: String) {
        if (this.callId == callId && _connected.value && socket != null) return
        allowReconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        this.callId = callId
        closeSocketOnly()
        suspendCoroutine { cont ->
            openContinuation = cont
            openSocket(callId)
        }
    }

    private fun openSocket(callId: String) {
        val token = session.accessToken
        if (token.isNullOrBlank()) {
            openContinuation?.resumeWithException(IllegalStateException("Сессия истекла. Войдите снова."))
            openContinuation = null
            return
        }
        val request = Request.Builder()
            .url("${session.wsBaseUrl}/ws/call/$callId/?token=$token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                retry = 0
                send(JSONObject().put("action", "call.rejoin"))
                openContinuation?.resume(Unit)
                openContinuation = null
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                _signals.tryEmit(data)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket !== webSocket) return
                _connected.value = false
                failOpenIfNeeded("Канал звонка закрыт.")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                _connected.value = false
                failOpenIfNeeded(t.message ?: "Не удалось открыть канал звонка.")
                scheduleReconnect()
            }
        })
    }

    private fun failOpenIfNeeded(message: String) {
        val cont = openContinuation ?: return
        openContinuation = null
        cont.resumeWithException(IllegalStateException(message))
    }

    private fun scheduleReconnect() {
        if (!allowReconnect) return
        val id = callId ?: return
        if (session.accessToken.isNullOrBlank()) return
        if (reconnectJob?.isActive == true) return
        if (retry >= 5) {
            _signals.tryEmit(
                JSONObject()
                    .put("action", "call.failed")
                    .put("detail", "signaling_lost"),
            )
            return
        }
        val attempt = retry
        retry += 1
        val delayMs = minOf(1000L * (1L shl attempt), 8_000L)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (allowReconnect && callId == id && !session.accessToken.isNullOrBlank()) {
                openSocket(id)
            }
        }
    }

    fun send(payload: JSONObject): Boolean {
        val ws = socket ?: return false
        if (!_connected.value) return false
        return ws.send(payload.toString())
    }

    fun sendSignal(action: String, data: JSONObject? = null): Boolean {
        val payload = JSONObject().put("action", action)
        if (data != null) payload.put("data", data)
        return send(payload)
    }

    private fun closeSocketOnly() {
        val old = socket
        socket = null
        _connected.value = false
        old?.close(1000, null)
    }

    fun disconnect() {
        allowReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        retry = 0
        callId = null
        failOpenIfNeeded("Канал звонка закрыт.")
        closeSocketOnly()
    }
}
