package com.example.monica.data.ws

import android.content.Context
import com.example.monica.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Единый presence-сокет на всё приложение (UI + фоновый daemon).
 * Живёт дольше Activity/ViewModel, чтобы статус «в сети» не падал при закрытии UI.
 */
object PresenceHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var socket: PresenceSocket? = null

    fun get(context: Context): PresenceSocket {
        socket?.let { return it }
        synchronized(this) {
            socket?.let { return it }
            val created = PresenceSocket(
                session = SessionStore(context.applicationContext),
                scope = scope,
            )
            socket = created
            return created
        }
    }

    fun isConnected(): Boolean = socket?.connected?.value == true

    fun ensureConnected(context: Context) {
        val session = SessionStore(context.applicationContext)
        if (!session.isLoggedIn) return
        val presence = get(context)
        if (presence.connected.value) return
        presence.connect()
    }

    fun disconnect() {
        socket?.disconnect(reconnect = false)
    }
}
