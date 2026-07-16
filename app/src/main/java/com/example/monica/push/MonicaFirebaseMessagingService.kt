package com.example.monica.push

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.monica.MainActivity
import com.example.monica.MonicaApp
import com.example.monica.R
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
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Monica"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Новое сообщение"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data["chat_id"]?.let { putExtra("chat_id", it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, MonicaApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(this).notify(
            (message.messageId ?: System.currentTimeMillis().toString()).hashCode(),
            notification,
        )
    }

    companion object {
        private const val TAG = "MonicaFCM"
    }
}
