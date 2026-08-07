package com.example.monica.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Локальный кэш мобильного фона чата.
 *
 * Инвалидация: если в БД изменились [objectPath] (название/путь в MinIO)
 * или [updatedAt], файл перекачивается из MinIO заново.
 * Presigned URL в мета не ключ — он может ротироваться без смены фона.
 */
object ChatBackgroundCache {
    private const val DIR = "chat-backgrounds-mobile"
    private const val TAG = "MonicaChatBg"
    private val inflight = ConcurrentHashMap<String, Any>()

    data class Version(
        val objectPath: String?,
        val updatedAt: String?,
    )

    fun getCached(
        context: Context,
        chatId: String,
        objectPath: String?,
        updatedAt: String?,
    ): File? {
        if (chatId.isBlank() || objectPath.isNullOrBlank()) return null
        val file = imageFile(context, chatId)
        if (!file.exists() || file.length() == 0L) return null
        val meta = readMeta(context, chatId) ?: return null
        if (meta.objectPath != objectPath) return null
        if (!updatedAt.isNullOrBlank() &&
            !meta.updatedAt.isNullOrBlank() &&
            meta.updatedAt != updatedAt
        ) {
            return null
        }
        return file
    }

    fun putBytes(
        context: Context,
        chatId: String,
        bytes: ByteArray,
        objectPath: String?,
        updatedAt: String?,
    ): File? {
        if (chatId.isBlank() || bytes.isEmpty() || objectPath.isNullOrBlank()) return null
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
            writeMeta(context, chatId, objectPath, updatedAt.orEmpty())
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
        objectPath: String?,
        contentUrl: String?,
        updatedAt: String?,
    ): File? {
        if (chatId.isBlank() || objectPath.isNullOrBlank() || contentUrl.isNullOrBlank()) {
            return null
        }
        getCached(context, chatId, objectPath, updatedAt)?.let { return it }

        val lock = inflight.getOrPut(chatId) { Any() }
        synchronized(lock) {
            try {
                getCached(context, chatId, objectPath, updatedAt)?.let { return it }
                val bytes = api.fetchMediaBytes(objectPath = objectPath, contentUrl = contentUrl)
                if (bytes.isEmpty()) return null
                return putBytes(context, chatId, bytes, objectPath, updatedAt)
            } catch (exc: Exception) {
                Log.w(TAG, "Background download failed: $chatId", exc)
                return null
            } finally {
                inflight.remove(chatId)
            }
        }
    }

    private data class Meta(
        val objectPath: String,
        val updatedAt: String?,
    )

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
            Meta(
                objectPath = json.optString("objectPath").ifBlank {
                    // Совместимость со старым meta.sourceUrl — считаем устаревшим.
                    ""
                },
                updatedAt = json.optString("updatedAt").takeIf { it.isNotBlank() },
            ).takeIf { it.objectPath.isNotBlank() }
        }.getOrNull()
    }

    private fun writeMeta(
        context: Context,
        chatId: String,
        objectPath: String,
        updatedAt: String,
    ) {
        val dir = dir(context)
        if (!dir.exists()) dir.mkdirs()
        metaFile(context, chatId).writeText(
            JSONObject()
                .put("objectPath", objectPath)
                .put("updatedAt", updatedAt)
                .toString(),
        )
    }
}
