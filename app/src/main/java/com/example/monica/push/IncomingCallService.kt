package com.example.monica.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Держит ongoing foreground-уведомление входящего звонка
 * и непрерывную вибрацию, пока пользователь не примет / отклонит.
 */
class IncomingCallService : Service() {
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCallForeground()
            return START_NOT_STICKY
        }

        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank() || intent == null) {
            stopCallForeground()
            return START_NOT_STICKY
        }

        val notification = CallNotificationHelper.buildIncomingNotification(
            context = this,
            callId = callId,
            chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty(),
            mediaMode = intent.getStringExtra(EXTRA_MEDIA_MODE).orEmpty().ifBlank { "audio" },
            callerNickname = intent.getStringExtra(EXTRA_CALLER_NICKNAME).orEmpty(),
            callerId = intent.getStringExtra(EXTRA_CALLER_ID).orEmpty(),
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    CallNotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(CallNotificationHelper.NOTIFICATION_ID, notification)
            }
        }.onFailure {
            @Suppress("DEPRECATION")
            startForeground(CallNotificationHelper.NOTIFICATION_ID, notification)
        }

        startRingVibration()
        return START_STICKY
    }

    override fun onDestroy() {
        stopRingVibration()
        super.onDestroy()
    }

    private fun startRingVibration() {
        val vib = resolveVibrator() ?: return
        vibrator = vib
        // Паттерн: вибро 1с — пауза 0.5с — повтор бесконечно (repeat index = 0).
        val pattern = longArrayOf(0, 1000, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(
                VibrationEffect.createWaveform(pattern, 0),
            )
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    private fun stopRingVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun resolveVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun stopCallForeground() {
        stopRingVibration()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.example.monica.action.STOP_INCOMING_CALL_SERVICE"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_MEDIA_MODE = "media_mode"
        const val EXTRA_CALLER_NICKNAME = "caller_nickname"
        const val EXTRA_CALLER_ID = "caller_id"

        fun start(
            context: Context,
            callId: String,
            chatId: String,
            mediaMode: String,
            callerNickname: String,
            callerId: String,
        ) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_MEDIA_MODE, mediaMode)
                putExtra(EXTRA_CALLER_NICKNAME, callerNickname)
                putExtra(EXTRA_CALLER_ID, callerId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
            runCatching { context.stopService(Intent(context, IncomingCallService::class.java)) }
        }
    }
}
