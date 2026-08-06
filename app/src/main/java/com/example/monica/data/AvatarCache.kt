package com.example.monica.data

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Дисковый кэш аватаров.
 *
 * Стабильный ключ — путь MinIO (`user.photo`, напр. `user-avatars/uuid.jpg`).
 * Скачивание: 1) `/api/media/?path=...` с Bearer (надёжно с телефона),
 * 2) иначе rewritten `photo_url` (MinIO).
 * Если имя/дата в метаданных не совпадают — файл качается заново.
 */
object AvatarCache {
    private const val DIR = "avatar-cache"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val stickyUrls = ConcurrentHashMap<String, String>()
    private val inflight = ConcurrentHashMap<String, Any>()

    /** Путь объекта в MinIO — единственный надёжный ключ для прокси/кэша. */
    fun objectPath(user: UserProfile?): String? =
        user?.photo?.trim()?.takeIf { it.isNotBlank() && it != "null" && "/" in it }

    /** Ключ кэша только если у пользователя реально есть фото — иначе не дергаем GET avatar. */
    fun cacheKey(user: UserProfile?): String? = objectPath(user)

    private fun hasRemoteAvatar(user: UserProfile?): Boolean {
        if (user == null) return false
        if (objectPath(user) != null) return true
        return !user.photoUrl.isNullOrBlank() && user.photoUrl != "null"
    }

    fun fileNameOf(photoKey: String): String = photoKey.substringAfterLast('/').ifBlank { photoKey }

    fun resolveRemoteUrl(session: SessionStore, remoteUrl: String?): String? =
        MediaUrls.resolve(session.apiBaseUrl, remoteUrl)

    fun getCachedFile(context: Context, photoKey: String?, updatedAt: String?): File? {
        if (photoKey.isNullOrBlank()) return null
        val meta = readMeta(context, photoKey) ?: return null
        val file = imageFile(context, photoKey)
        if (!file.exists() || file.length() == 0L) return null

        val expectedName = fileNameOf(photoKey)
        val nameMatches = meta.photoKey == photoKey && meta.fileName == expectedName
        val incomingDate = normalizeDate(updatedAt)
        val dateMatches = when {
            incomingDate.isEmpty() || meta.updatedAt.isBlank() -> true
            else -> normalizeDate(meta.updatedAt) == incomingDate
        }
        return if (nameMatches && dateMatches) file else null
    }

    /**
     * Скачать и закэшировать. Не требует photo_url — достаточно `photo` (path).
     */
    fun getOrFetch(
        context: Context,
        session: SessionStore,
        user: UserProfile?,
    ): File? {
        if (!hasRemoteAvatar(user)) return null
        val photoKey = cacheKey(user)
            ?: user?.id?.takeIf { it.isNotBlank() }?.let { "user:$it" }
            ?: return null
        val updatedAt = user?.updatedAt
        getCachedFile(context, photoKey, updatedAt)?.let { return it }

        val lock = inflight.getOrPut(photoKey) { Any() }
        synchronized(lock) {
            try {
                getCachedFile(context, photoKey, updatedAt)?.let { return it }

                val token = session.accessToken
                val path = objectPath(user)
                val userId = user?.id.orEmpty()
                // Групповой аватар лежит в chat-files/… — /api/users/{id}/avatar/ не подходит.
                val isGroupAvatar =
                    userId.startsWith("group-") ||
                        path?.startsWith("chat-files/") == true

                // 1) Endpoint по user id — только для личных аватаров.
                if (
                    !isGroupAvatar &&
                    path != null &&
                    userId.isNotBlank() &&
                    !token.isNullOrBlank()
                ) {
                    runCatching {
                        MonicaApi(session).fetchUserAvatarBytes(userId)
                    }.getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { bytes ->
                            storeBytes(context, photoKey, updatedAt, bytes)
                                ?.let { return it }
                        }
                }

                // 2) API proxy по MinIO path
                if (!path.isNullOrBlank() && !token.isNullOrBlank()) {
                    val proxy = MediaUrls.proxyUrl(session.apiBaseUrl, path)
                    downloadAndStore(context, photoKey, updatedAt, proxy, bearerToken = token)
                        ?.let { return it }
                }

                // 3) Прямой MinIO URL (после rewrite localhost → LAN IP)
                val remote = resolveRemoteUrl(session, user?.photoUrl)
                    ?: stickyUrls[photoKey]
                if (!remote.isNullOrBlank()) {
                    stickyUrls.putIfAbsent(photoKey, remote)
                    downloadAndStore(context, photoKey, updatedAt, remote)?.let { return it }
                }

                return null
            } finally {
                inflight.remove(photoKey)
            }
        }
    }

    fun warm(context: Context, session: SessionStore, user: UserProfile?) {
        if (!hasRemoteAvatar(user)) return
        getOrFetch(context, session, user)
    }

    fun invalidate(context: Context, photoKey: String?) {
        if (photoKey.isNullOrBlank()) return
        stickyUrls.remove(photoKey)
        imageFile(context, photoKey).delete()
        metaFile(context, photoKey).delete()
    }

    private fun downloadAndStore(
        context: Context,
        photoKey: String,
        updatedAt: String?,
        remoteUrl: String?,
        bearerToken: String? = null,
    ): File? {
        if (remoteUrl.isNullOrBlank()) return null
        return try {
            val builder = Request.Builder().url(remoteUrl).get()
            if (!bearerToken.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $bearerToken")
            }
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Avatar HTTP ${response.code}: $remoteUrl")
                    return null
                }
                val bytes = response.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                storeBytes(context, photoKey, updatedAt, bytes)
            }
        } catch (exc: Exception) {
            Log.w(TAG, "Avatar download failed: $remoteUrl", exc)
            null
        }
    }

    private fun storeBytes(
        context: Context,
        photoKey: String,
        updatedAt: String?,
        bytes: ByteArray,
    ): File? {
        if (bytes.isEmpty()) return null
        val dir = cacheDir(context)
        if (!dir.exists() && !dir.mkdirs()) return null

        val file = imageFile(context, photoKey)
        file.writeBytes(bytes)

        val meta = JSONObject()
            .put("photoKey", photoKey)
            .put("fileName", fileNameOf(photoKey))
            .put("updatedAt", updatedAt.orEmpty())
        metaFile(context, photoKey).writeText(meta.toString())
        return file
    }

    private data class Meta(
        val photoKey: String,
        val fileName: String,
        val updatedAt: String,
    )

    private fun readMeta(context: Context, photoKey: String): Meta? {
        val f = metaFile(context, photoKey)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText())
            Meta(
                photoKey = json.optString("photoKey"),
                fileName = json.optString("fileName"),
                updatedAt = json.optString("updatedAt"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeDate(value: String?): String = value?.trim().orEmpty()

    private fun cacheDir(context: Context): File = File(context.filesDir, DIR)

    private fun digest(photoKey: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(photoKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun imageFile(context: Context, photoKey: String): File =
        File(cacheDir(context), "${digest(photoKey).take(32)}.img")

    private fun metaFile(context: Context, photoKey: String): File =
        File(cacheDir(context), "${digest(photoKey).take(32)}.meta.json")

    private const val TAG = "MonicaAvatar"
}
