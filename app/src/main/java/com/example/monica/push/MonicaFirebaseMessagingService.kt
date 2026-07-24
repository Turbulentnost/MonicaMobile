package com.example.monica.push

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.monica.MainActivity
import com.example.monica.MonicaApp
import com.example.monica.data.MonicaApi
import com.example.monica.data.SessionStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonicaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM token")
        val session = SessionStore(this)
        if (!session.isLoggedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { MonicaApi(session).registerDevice(token) }
                .onFailure { Log.w(TAG, "Failed to upload new token: ${it.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"].orEmpty()
        if (type == "incoming_call") {
            handleIncomingCall(message)
            return
        }
        val chatId = message.data["chat_id"].orEmpty().ifBlank {
            message.data["chatId"].orEmpty()
        }
        // Уже в этом чате на переднем плане — пуш не показываем.
        if (
            com.example.monica.data.AppVisibility.isForeground &&
            chatId.isNotBlank() &&
            chatId == com.example.monica.data.AppVisibility.openChatId
        ) {
            return
        }
        showMessageNotification(message)
    }

    private fun handleIncomingCall(message: RemoteMessage) {
        val callId = message.data["call_id"].orEmpty()
        if (callId.isBlank()) return
        // Звонок показывает IncomingCallService; демон без FGS-уведомления.
        MonicaDaemonService.start(this)
        CallNotificationHelper.showIncoming(
            context = this,
            callId = callId,
            chatId = message.data["chat_id"].orEmpty(),
            mediaMode = message.data["media_mode"].orEmpty().ifBlank { "audio" },
            callerNickname = message.data["caller_nickname"].orEmpty(),
            callerId = message.data["caller_id"].orEmpty(),
        )
        // Full-screen intent на уведомлении сам откроет UI; startActivity из фона на Android 10+ часто блокируется.
    }

    private fun showMessageNotification(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Monica"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Новое сообщение"
        val type = message.data["type"].orEmpty()

        val chatId = message.data["chat_id"].orEmpty().ifBlank {
            message.data["chatId"].orEmpty()
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (chatId.isNotBlank()) {
                putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
                putExtra("chat_id", chatId)
            }
        }
        val requestCode = if (chatId.isNotBlank()) chatId.hashCode() else title.hashCode()
        val pending = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val avatar = NotificationStyle.letterAvatar(title)
        val builder = NotificationCompat.Builder(this, MonicaApp.CHANNEL_MESSAGES)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("Monica")
            .setLargeIcon(avatar)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (type == "chat_message" || chatId.isNotBlank()) {
                    NotificationCompat.CATEGORY_MESSAGE
                } else {
                    NotificationCompat.CATEGORY_SOCIAL
                },
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .setShowWhen(true)
            .setWhen(message.sentTime.takeIf { it > 0L } ?: System.currentTimeMillis())

        NotificationStyle.applyMonicaChrome(builder, this)

        if (type == "chat_message" || chatId.isNotBlank()) {
            builder.setStyle(
                NotificationStyle.messagingStyle(
                    senderName = title,
                    body = body,
                    timestampMs = message.sentTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
                ),
            )
            if (chatId.isNotBlank()) {
                builder.setGroup("monica_chat_$chatId")
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
                    .setSummaryText("Monica"),
            )
        }

        NotificationManagerCompat.from(this).notify(
            (message.messageId ?: "${chatId}:${System.currentTimeMillis()}").hashCode(),
            builder.build(),
        )
    }

    companion object {
        private const val TAG = "MonicaFCM"
    }
}
