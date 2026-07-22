package com.example.monica.push

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.monica.MainActivity
import com.example.monica.MonicaApp
import com.example.monica.R
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
 * Лёгкий фоновый «демон»: держит process + presence WebSocket,
 * чтобы пользователь оставался «в сети» и принимал звонки при закрытом UI.
 */
class MonicaDaemonService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var callWatchJob: Job? = null
    private var keepAliveJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startDaemonForeground()
        PresenceHub.ensureConnected(this)
        watchIncomingCalls()
        startKeepAliveLoop()
        Log.i(TAG, "Daemon started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val session = SessionStore(this)
        if (!session.isLoggedIn) {
            stopSelf()
            return START_NOT_STICKY
        }
        startDaemonForeground()
        PresenceHub.ensureConnected(this)
        if (callWatchJob?.isActive != true) watchIncomingCalls()
        if (keepAliveJob?.isActive != true) startKeepAliveLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        callWatchJob?.cancel()
        callWatchJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null
        scope.cancel()
        Log.i(TAG, "Daemon stopped")
        // Не трогаем PresenceHub здесь: stop() вызывается только при logout,
        // а logout сам делает PresenceHub.disconnect().
        super.onDestroy()
    }

    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                if (SessionStore(this@MonicaDaemonService).isLoggedIn) {
                    PresenceHub.ensureConnected(this@MonicaDaemonService)
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

    private fun startDaemonForeground() {
        val notification = buildDaemonNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildDaemonNotification(): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MonicaApp.CHANNEL_DAEMON)
            .setSmallIcon(R.drawable.ic_stat_monica)
            .setContentTitle("Monica на связи")
            .setContentText("Вы в сети · ожидание звонков")
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    companion object {
        private const val TAG = "MonicaDaemon"
        const val NOTIFICATION_ID = 70001
        const val ACTION_STOP = "com.example.monica.action.STOP_DAEMON"

        fun start(context: Context) {
            val session = SessionStore(context)
            if (!session.isLoggedIn) return
            val intent = Intent(context, MonicaDaemonService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonicaDaemonService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
            runCatching { context.stopService(Intent(context, MonicaDaemonService::class.java)) }
        }
    }
}
