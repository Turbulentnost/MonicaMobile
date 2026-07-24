package com.example.monica.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.monica.data.AppVisibility
import com.example.monica.data.SessionStore
import com.example.monica.data.ws.PresenceHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Лёгкий фоновый сервис без постоянного уведомления.
 * Presence (online) — только когда UI на переднем плане.
 * Входящие звонки в фоне доставляет FCM (+ IncomingCallService), не этот демон.
 */
class MonicaDaemonService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var callWatchJob: Job? = null
    private var keepAliveJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // На случай обновления со старой версии — убрать «Фоновый режим».
        clearDaemonNotification()
        if (AppVisibility.isForeground) {
            PresenceHub.ensureConnected(this)
        }
        watchIncomingCalls()
        startKeepAliveLoop()
        Log.i(TAG, "Daemon started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        clearDaemonNotification()
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UI_FOREGROUND -> {
                PresenceHub.ensureConnected(this)
                return START_STICKY
            }
            ACTION_UI_BACKGROUND -> {
                PresenceHub.onAppBackground()
                return START_STICKY
            }
        }
        val session = SessionStore(this)
        if (!session.isLoggedIn) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (AppVisibility.isForeground) {
            PresenceHub.ensureConnected(this)
        }
        if (callWatchJob?.isActive != true) watchIncomingCalls()
        if (keepAliveJob?.isActive != true) startKeepAliveLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        callWatchJob?.cancel()
        callWatchJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null
        clearDaemonNotification()
        scope.cancel()
        Log.i(TAG, "Daemon stopped")
        super.onDestroy()
    }

    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                if (SessionStore(this@MonicaDaemonService).isLoggedIn) {
                    if (AppVisibility.isForeground) {
                        PresenceHub.ensureConnected(this@MonicaDaemonService)
                    }
                }
                delay(15_000)
            }
        }
    }

    private fun watchIncomingCalls() {
        callWatchJob?.cancel()
        val presence = PresenceHub.get(this)
        callWatchJob = scope.launch {
            presence.callEvents.collect { event ->
                val action = event.optString("action")
                when (action) {
                    "call.incoming" -> {
                        val call = event.optJSONObject("call") ?: return@collect
                        CallNotificationHelper.showIncoming(
                            context = applicationContext,
                            callId = call.optString("id"),
                            chatId = when (val chat = call.opt("chat")) {
                                is String -> chat
                                is org.json.JSONObject -> chat.optString("id")
                                else -> ""
                            },
                            mediaMode = call.optString("media_mode", "audio"),
                            callerNickname = call.optJSONObject("caller")?.optString("nickname").orEmpty(),
                            callerId = call.optJSONObject("caller")?.optString("id").orEmpty(),
                        )
                    }
                    "call.accepted",
                    "call.rejected",
                    "call.cancelled",
                    "call.missed",
                    "call.ended",
                    "call.failed",
                    -> CallNotificationHelper.cancel(applicationContext)
                }
            }
        }
    }

    private fun clearDaemonNotification() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        runCatching {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        }
    }

    companion object {
        private const val TAG = "MonicaDaemon"
        const val NOTIFICATION_ID = 70001
        const val ACTION_STOP = "com.example.monica.action.STOP_DAEMON"
        const val ACTION_UI_FOREGROUND = "com.example.monica.action.DAEMON_UI_FOREGROUND"
        const val ACTION_UI_BACKGROUND = "com.example.monica.action.DAEMON_UI_BACKGROUND"

        fun start(context: Context) {
            val session = SessionStore(context)
            if (!session.isLoggedIn) return
            val intent = Intent(context, MonicaDaemonService::class.java)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Daemon start skipped: ${it.message}") }
        }

        fun notifyUiForeground(context: Context) {
            if (!SessionStore(context).isLoggedIn) return
            val intent = Intent(context, MonicaDaemonService::class.java).apply {
                action = ACTION_UI_FOREGROUND
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Daemon foreground notify skipped: ${it.message}") }
        }

        fun notifyUiBackground(context: Context) {
            if (!SessionStore(context).isLoggedIn) return
            val intent = Intent(context, MonicaDaemonService::class.java).apply {
                action = ACTION_UI_BACKGROUND
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Daemon background notify skipped: ${it.message}") }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonicaDaemonService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
            runCatching { context.stopService(Intent(context, MonicaDaemonService::class.java)) }
            runCatching {
                NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
            }
        }
    }
}
