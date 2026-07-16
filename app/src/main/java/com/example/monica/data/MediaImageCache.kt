package com.example.monica.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Авторизованный дисковый кэш фотографий из сообщений. */
object MediaImageCache {
    private const val DIR = "message-images"
    private const val TAG = "MonicaMedia"
    private val inflight = ConcurrentHashMap<String, Any>()

    fun key(objectPath: String?, contentUrl: String?): String? =
        objectPath?.trim()?.takeIf { it.isNotBlank() }
            ?: contentUrl?.trim()?.takeIf { it.isNotBlank() }

    fun getCached(context: Context, key: String?): File? {
        if (key.isNullOrBlank()) return null
        val file = imageFile(context, key)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    fun getOrFetch(
        context: Context,
        api: MonicaApi,
        objectPath: String?,
        contentUrl: String?,
    ): File? {
        val key = key(objectPath, contentUrl) ?: return null
        getCached(context, key)?.let { return it }

        val lock = inflight.getOrPut(key) { Any() }
        synchronized(lock) {
            try {
                getCached(context, key)?.let { return it }
                val bytes = api.fetchMediaBytes(objectPath, contentUrl)
                if (bytes.isEmpty()) return null

                // Не сохраняем JSON ошибки или повреждённые данные как картинку.
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) {
                    Log.w(TAG, "Response is not an image: $objectPath")
                    return null
                }
                bitmap.recycle()

                val dir = File(context.filesDir, DIR)
                if (!dir.exists() && !dir.mkdirs()) return null
                val tmp = File(dir, "${digest(key)}.tmp")
                val target = imageFile(context, key)
                tmp.writeBytes(bytes)
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    target.writeBytes(bytes)
                    tmp.delete()
                }
                return target
            } catch (exc: Exception) {
                Log.w(TAG, "Image download failed: $objectPath", exc)
                return null
            } finally {
                inflight.remove(key)
            }
        }
    }

    private fun imageFile(context: Context, key: String): File =
        File(File(context.filesDir, DIR), "${digest(key)}.img")

    private fun digest(value: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }
}
