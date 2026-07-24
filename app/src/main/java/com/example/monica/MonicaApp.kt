package com.example.monica

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.monica.push.MonicaDaemonService
import com.example.monica.push.PushRegistrar
import okhttp3.OkHttpClient
import java.io.File

class MonicaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        PushRegistrar.refreshTokenIfLoggedIn(this)
        // Лёгкий демон без постоянного уведомления (presence только в UI).
        val session = com.example.monica.data.SessionStore(this)
        if (session.isLoggedIn) {
            MonicaDaemonService.start(this)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.2)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil_images"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder().build()
            }
            .crossfade(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        // Старые каналы — пересоздаём под брендированный вид / рингтон звонка.
        runCatching { manager.deleteNotificationChannel("messages") }
        runCatching { manager.deleteNotificationChannel("calls") }
        runCatching { manager.deleteNotificationChannel("calls_monica") }

        val accent = getColor(R.color.monica_notification_accent)
        val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT
        } else {
            AudioAttributes.USAGE_NOTIFICATION
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Сообщения Monica",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Новые сообщения и события чата"
                enableLights(true)
                lightColor = accent
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 100, 180)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attrs)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        val callAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Новый id канала — иначе Android не обновит vibration pattern у уже созданного.
        runCatching { manager.deleteNotificationChannel("calls_monica_v2") }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CALLS,
                "Звонки Monica",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Входящие аудио- и видеозвонки — постоянное уведомление до ответа"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                // Повторный паттерн канала + непрерывная вибрация из IncomingCallService.
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500)
                setBypassDnd(true)
                setSound(Settings.System.DEFAULT_RINGTONE_URI, callAttrs)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        runCatching { manager.deleteNotificationChannel("daemon_monica") }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DAEMON,
                "Фон Monica",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Служебный фон для доставки звонков (без звука)"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            },
        )
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages_monica"
        const val CHANNEL_CALLS = "calls_monica_v3"
        const val CHANNEL_DAEMON = "daemon_monica_v2"
    }
}
