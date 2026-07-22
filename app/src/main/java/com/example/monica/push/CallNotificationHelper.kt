package com.example.monica.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.monica.MainActivity
import com.example.monica.MonicaApp
import com.example.monica.R

object CallNotificationHelper {
    const val NOTIFICATION_ID = 71001
    const val ACTION_OPEN = "com.example.monica.action.CALL_OPEN"
    const val ACTION_ACCEPT = "com.example.monica.action.CALL_ACCEPT"
    const val ACTION_REJECT = "com.example.monica.action.CALL_REJECT"

    /**
     * Показать/обновить постоянный пуш входящего звонка (foreground service).
     * Висит, пока не вызовут [cancel] — при accept / reject / miss / cancel.
     */
    fun showIncoming(
        context: Context,
        callId: String,
        chatId: String,
        mediaMode: String,
        callerNickname: String,
        callerId: String = "",
    ) {
        if (callId.isBlank()) return
        val app = context.applicationContext
        IncomingCallService.start(
            context = app,
            callId = callId,
            chatId = chatId,
            mediaMode = mediaMode,
            callerNickname = callerNickname,
            callerId = callerId,
        )
        // Дублируем notify — на части OEM FGS-уведомление иначе «схлопывается» при открытии UI.
        runCatching {
            NotificationManagerCompat.from(app).notify(
                NOTIFICATION_ID,
                buildIncomingNotification(
                    context = app,
                    callId = callId,
                    chatId = chatId,
                    mediaMode = mediaMode,
                    callerNickname = callerNickname,
                    callerId = callerId,
                ),
            )
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        IncomingCallService.stop(app)
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }

    fun buildIncomingNotification(
        context: Context,
        callId: String,
        chatId: String,
        mediaMode: String,
        callerNickname: String,
        callerId: String = "",
    ): Notification {
        val isVideo = mediaMode == "video"
        val title = if (isVideo) "Входящий видеозвонок" else "Входящий аудиозвонок"
        val displayName = if (callerNickname.isNotBlank()) {
            "@${callerNickname.removePrefix("@")}"
        } else {
            "Monica"
        }
        val body = "Звонит… Нажмите, чтобы ответить"

        val openPending = activityPending(context, ACTION_OPEN, callId, chatId, mediaMode, callerNickname, callerId, 0)
        val acceptPending = activityPending(context, ACTION_ACCEPT, callId, chatId, mediaMode, callerNickname, callerId, 1)
        val rejectPending = activityPending(context, ACTION_REJECT, callId, chatId, mediaMode, callerNickname, callerId, 2)

        val caller = Person.Builder()
            .setName(displayName)
            .setKey(callerId.ifBlank { displayName })
            .setIcon(NotificationStyle.personIcon(displayName))
            .setImportant(true)
            .build()

        val builder = NotificationCompat.Builder(context, MonicaApp.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText("$displayName · $body")
            .setSubText("Monica")
            .setLargeIcon(NotificationStyle.letterAvatar(displayName))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(openPending)
            .setFullScreenIntent(openPending, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(NotificationStyle.accentColor(context))
            .setColorized(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(caller, rejectPending, acceptPending)
                    .setIsVideo(isVideo),
            )
        } else {
            builder
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText("$displayName\n$body"),
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_stat_call_decline,
                        "Отклонить",
                        rejectPending,
                    ).build(),
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_stat_call_accept,
                        "Принять",
                        acceptPending,
                    ).build(),
                )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        return builder.build()
    }

    private fun activityPending(
        context: Context,
        action: String,
        callId: String,
        chatId: String,
        mediaMode: String,
        callerNickname: String,
        callerId: String,
        requestOffset: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
            putExtra(MainActivity.EXTRA_CALL_MEDIA_MODE, mediaMode)
            putExtra(MainActivity.EXTRA_CALL_CALLER_NICKNAME, callerNickname)
            putExtra(MainActivity.EXTRA_CALL_CALLER_ID, callerId)
            putExtra(MainActivity.EXTRA_CALL_ACTION, action)
        }
        return PendingIntent.getActivity(
            context,
            callId.hashCode() + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
