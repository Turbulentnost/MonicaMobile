package com.example.monica.data.ws

import android.content.Context
import com.example.monica.data.AppVisibility
import com.example.monica.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Единый presence-сокет.
 * Online только пока UI на переднем плане; в фоне сокет рвём (статус offline),
 * демон при этом может продолжать жить для FCM/звонков.
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

    /** UI стал видимым — online. */
    fun onAppForeground(context: Context) {
        AppVisibility.setForeground(true)
        ensureConnected(context)
    }

    /** UI ушёл в фон — offline, демон не трогаем. */
    fun onAppBackground() {
        AppVisibility.setForeground(false)
        disconnect()
    }

    fun ensureConnected(context: Context) {
        if (!AppVisibility.isForeground) return
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
