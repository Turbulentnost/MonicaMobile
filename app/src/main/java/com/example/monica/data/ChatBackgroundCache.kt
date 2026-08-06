package com.example.monica.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Локальный кэш персонального фона чата.
 * Ключ — chatId; meta хранит sourceUrl, чтобы перекачать при смене URL на сервере.
 */
object ChatBackgroundCache {
    private const val DIR = "chat-backgrounds"
    private const val TAG = "MonicaChatBg"
    private val inflight = ConcurrentHashMap<String, Any>()

    fun getCached(context: Context, chatId: String, sourceUrl: String?): File? {
        if (chatId.isBlank()) return null
        val file = imageFile(context, chatId)
        if (!file.exists() || file.length() == 0L) return null
        val metaUrl = readMeta(context, chatId)?.sourceUrl
        if (!sourceUrl.isNullOrBlank() && !metaUrl.isNullOrBlank() && metaUrl != sourceUrl) {
            return null
        }
        return file
    }

    fun putBytes(context: Context, chatId: String, bytes: ByteArray, sourceUrl: String?): File? {
        if (chatId.isBlank() || bytes.isEmpty()) return null
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bitmap.recycle()
            val dir = dir(context)
            if (!dir.exists() && !dir.mkdirs()) return null
            val target = imageFile(context, chatId)
            val tmp = File(dir, "${safeId(chatId)}.tmp")
            tmp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            writeMeta(context, chatId, sourceUrl.orEmpty())
            target.takeIf { it.exists() && it.length() > 0L }
        } catch (exc: Exception) {
            Log.w(TAG, "putBytes failed: $chatId", exc)
            null
        }
    }

    fun invalidate(context: Context, chatId: String) {
        if (chatId.isBlank()) return
        runCatching { imageFile(context, chatId).delete() }
        runCatching { metaFile(context, chatId).delete() }
    }

    fun getOrFetch(
        context: Context,
        api: MonicaApi,
        chatId: String,
        sourceUrl: String?,
    ): File? {
        if (chatId.isBlank() || sourceUrl.isNullOrBlank()) return null
        getCached(context, chatId, sourceUrl)?.let { return it }

        val lock = inflight.getOrPut(chatId) { Any() }
        synchronized(lock) {
            try {
                getCached(context, chatId, sourceUrl)?.let { return it }
                val bytes = api.fetchMediaBytes(objectPath = null, contentUrl = sourceUrl)
                if (bytes.isEmpty()) return null
                return putBytes(context, chatId, bytes, sourceUrl)
            } catch (exc: Exception) {
                Log.w(TAG, "Background download failed: $chatId", exc)
                return null
            } finally {
                inflight.remove(chatId)
            }
        }
    }

    private data class Meta(val sourceUrl: String)

    private fun dir(context: Context) = File(context.filesDir, DIR)

    private fun safeId(chatId: String): String =
        chatId.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun imageFile(context: Context, chatId: String) =
        File(dir(context), "${safeId(chatId)}.img")

    private fun metaFile(context: Context, chatId: String) =
        File(dir(context), "${safeId(chatId)}.meta.json")

    private fun readMeta(context: Context, chatId: String): Meta? {
        val file = metaFile(context, chatId)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            Meta(sourceUrl = json.optString("sourceUrl"))
        }.getOrNull()
    }

    private fun writeMeta(context: Context, chatId: String, sourceUrl: String) {
        val dir = dir(context)
        if (!dir.exists()) dir.mkdirs()
        metaFile(context, chatId).writeText(
            JSONObject().put("sourceUrl", sourceUrl).toString(),
        )
    }
}
