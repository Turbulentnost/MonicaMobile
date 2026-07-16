package com.example.monica

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.monica.push.PushRegistrar
import okhttp3.OkHttpClient
import java.io.File

class MonicaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        PushRegistrar.refreshTokenIfLoggedIn(this)
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
        val channel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о новых сообщениях Monica"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
    }
}
