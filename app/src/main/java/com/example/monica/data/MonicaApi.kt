package com.example.monica.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class MonicaApi(private val sessionStore: SessionStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class LoginResult(
        val access: String,
        val refresh: String,
        val userId: String,
        val nickname: String,
    )

    fun login(email: String, password: String): LoginResult {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val json = execute(Request.Builder()
            .url("${sessionStore.apiBaseUrl}/api/auth/login/")
            .post(body)
            .build())
        val tokens = json.getJSONObject("tokens")
        val user = json.getJSONObject("user")
        return LoginResult(
            access = tokens.getString("access"),
            refresh = tokens.getString("refresh"),
            userId = user.getString("id"),
            nickname = user.getString("nickname"),
        )
    }

    fun me(): UserProfile {
        val json = authGet("/api/auth/me/")
        return Companion.parseUser(json)
    }

    fun listChats(): List<ChatSummary> {
        val arr = authGetArray("/api/chats/")
        return (0 until arr.length()).map { Companion.parseChat(arr.getJSONObject(it)) }
    }

    fun listMessages(chatId: String, limit: Int = 100): List<MessageItem> {
        val url = "${sessionStore.apiBaseUrl}/api/chats/$chatId/messages/".toHttpUrl()
            .newBuilder()
            .addQueryParameter("limit", limit.coerceIn(1, 200).toString())
            .build()
        val arr = authGetArray(url.toString(), absolute = true)
        return (0 until arr.length()).map { Companion.parseMessage(arr.getJSONObject(it)) }
    }

    fun searchUsers(query: String): List<UserProfile> {
        val url = "${sessionStore.apiBaseUrl}/api/users/search/".toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .build()
        val arr = authGetArray(url.toString(), absolute = true)
        return (0 until arr.length()).map { Companion.parseUser(arr.getJSONObject(it)) }
    }

    fun startChat(recipientId: String): ChatSummary {
        val body = JSONObject().put("recipient_id", recipientId)
            .toString().toRequestBody(JSON_MEDIA_TYPE)
        val json = authPost("/api/chats/start/", body)
        return ChatSummary(
            id = json.getString("id"),
            partner = json.optJSONObject("partner")?.let { Companion.parseUser(it) },
            lastMessage = null,
            updatedAt = null,
        )
    }

    fun invitePrivate(chatId: String): PrivateSessionInfo {
        val json = authPost("/api/chats/$chatId/private/invite/", "{}".toRequestBody(JSON_MEDIA_TYPE))
        return PrivateSessionInfo(
            id = json.getString("id"),
            chatId = json.optString("chat_id").takeIf { it.isNotBlank() } ?: chatId,
            status = json.optString("status"),
            handshake = json.optBoolean("handshake"),
        )
    }

    fun acceptPrivate(sessionId: String): PrivateSessionInfo {
        val json = authPost("/api/private/$sessionId/accept/", "{}".toRequestBody(JSON_MEDIA_TYPE))
        return PrivateSessionInfo(
            id = json.getString("id"),
            chatId = json.optString("chat_id").takeIf { it.isNotBlank() },
            status = json.optString("status"),
        )
    }

    fun declinePrivate(sessionId: String) {
        authPost("/api/private/$sessionId/decline/", "{}".toRequestBody(JSON_MEDIA_TYPE))
    }

    fun closePrivate(sessionId: String) {
        authPost("/api/private/$sessionId/close/", "{}".toRequestBody(JSON_MEDIA_TYPE))
    }

    fun leavePrivate() {
        runCatching {
            authPost("/api/private/leave/", "{}".toRequestBody(JSON_MEDIA_TYPE))
        }
    }

    data class UploadedFile(
        val path: String,
        val contentUrl: String?,
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        val messageType: String,
    )

    data class CodeRunResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val timedOut: Boolean,
        val memoryExceeded: Boolean,
    )

    fun uploadFile(
        chatId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): UploadedFile {
        val safeMime = mimeType.ifBlank { "application/octet-stream" }
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart(
                "files",
                fileName,
                bytes.toRequestBody(safeMime.toMediaType()),
            )
            .build()
        val text = executeRaw(
            authRequest("/api/chats/$chatId/messages/upload/").post(body).build(),
        )
        val item = org.json.JSONObject(text).getJSONArray("files").getJSONObject(0)
        return UploadedFile(
            path = item.getString("path"),
            contentUrl = item.optString("content_url").takeIf { it.isNotBlank() },
            fileName = item.optString("file_name", fileName),
            mimeType = item.optString("mime_type", safeMime),
            fileSize = item.optLong("file_size", bytes.size.toLong()),
            messageType = item.optString("message_type", "file"),
        )
    }

    fun uploadCodeFile(chatId: String, fileName: String, content: String, mimeType: String): UploadedFile {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return uploadFile(chatId, fileName, bytes, mimeType)
    }

    fun runCode(chatId: String, messageId: String): CodeRunResult {
        val json = authPost("/api/chats/$chatId/messages/$messageId/run/", "{}".toRequestBody(JSON_MEDIA_TYPE))
        return CodeRunResult(
            stdout = json.optString("stdout"),
            stderr = json.optString("stderr"),
            exitCode = json.optInt("exit_code", -1),
            timedOut = json.optBoolean("timed_out"),
            memoryExceeded = json.optBoolean("memory_exceeded"),
        )
    }

    fun fetchUrlText(url: String): String {
        val resolved = MediaUrls.resolve(sessionStore.apiBaseUrl, url) ?: url
        val request = Request.Builder().url(resolved).get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(response.code, text)
            return text
        }
    }

    /**
     * Текст/байты медиа: сначала публичный URL (с rewrite localhost),
     * при ошибке — прокси `/api/media/?path=...` через бэкенд.
     */
    fun fetchMediaText(objectPath: String?, contentUrl: String?): String {
        val proxy = MediaUrls.proxyUrl(sessionStore.apiBaseUrl, objectPath)
        if (!proxy.isNullOrBlank()) {
            runCatching {
                executeRaw(authRequestUrl(proxy).get().build())
            }.getOrNull()?.let { return it }
        }

        val rewritten = MediaUrls.resolve(sessionStore.apiBaseUrl, contentUrl)
        if (!rewritten.isNullOrBlank()) {
            runCatching { fetchUrlText(rewritten) }.getOrNull()?.let { return it }
        }
        throw IllegalStateException("Не удалось загрузить файл")
    }

    fun fetchMediaBytes(objectPath: String?, contentUrl: String?): ByteArray {
        val proxy = MediaUrls.proxyUrl(sessionStore.apiBaseUrl, objectPath)
        if (!proxy.isNullOrBlank()) {
            runCatching {
                executeBytes(authRequestUrl(proxy).get().build())
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        val rewritten = MediaUrls.resolve(sessionStore.apiBaseUrl, contentUrl)
        if (!rewritten.isNullOrBlank()) {
            try {
                val request = Request.Builder().url(rewritten).get().build()
                client.newCall(request).execute().use { response ->
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    if (response.isSuccessful && bytes.isNotEmpty()) return bytes
                }
            } catch (_: Exception) {
                // fallback to proxy
            }
        }
        throw IllegalStateException("Не удалось загрузить изображение")
    }

    fun fetchUserAvatarBytes(userId: String): ByteArray =
        executeBytes(
            authRequest("/api/users/$userId/avatar/").get().build(),
        )


    fun listNotifications(): List<AppNotification> {
        val arr = authGetArray("/api/notifications/")
        return (0 until arr.length()).map { Companion.parseNotification(arr.getJSONObject(it)) }
    }

    fun markNotificationRead(id: String) {
        authPost("/api/notifications/$id/read/", "{}".toRequestBody(JSON_MEDIA_TYPE))
    }

    fun markAllNotificationsRead() {
        authPost("/api/notifications/read-all/", "{}".toRequestBody(JSON_MEDIA_TYPE))
    }

    fun clearNotifications() {
        authDelete("/api/notifications/clear/")
    }

    fun registerDevice(fcmToken: String, platform: String = "android") {
        val body = JSONObject()
            .put("token", fcmToken)
            .put("platform", platform)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        authPost("/api/devices/", body)
    }

    fun refreshAccessToken(): Boolean {
        val refresh = sessionStore.refreshToken ?: return false
        val body = JSONObject().put("refresh", refresh)
            .toString().toRequestBody(JSON_MEDIA_TYPE)
        return try {
            val json = execute(
                Request.Builder()
                    .url("${sessionStore.apiBaseUrl}/api/auth/token/refresh/")
                    .post(body)
                    .build(),
            )
            sessionStore.accessToken = json.getString("access")
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun authGet(path: String): JSONObject =
        execute(authRequest(path).get().build())

    private fun authGetArray(path: String, absolute: Boolean = false): JSONArray {
        val url = if (absolute) path else "${sessionStore.apiBaseUrl}$path"
        val text = executeRaw(authRequestUrl(url).get().build())
        return JSONArray(text)
    }

    private fun authPost(path: String, body: okhttp3.RequestBody): JSONObject =
        execute(authRequest(path).post(body).build())

    private fun authDelete(path: String): JSONObject {
        val text = executeRaw(authRequest(path).delete().build())
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun authRequest(path: String): Request.Builder =
        authRequestUrl("${sessionStore.apiBaseUrl}$path")

    private fun authRequestUrl(url: String): Request.Builder {
        val access = sessionStore.accessToken ?: throw IllegalStateException("Нет access token")
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $access")
    }

    private fun execute(request: Request): JSONObject {
        val text = executeRaw(request)
        return JSONObject(text)
    }

    private fun executeRaw(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 && sessionStore.refreshToken != null) {
                if (refreshAccessToken()) {
                    val retry = request.newBuilder()
                        .header("Authorization", "Bearer ${sessionStore.accessToken}")
                        .build()
                    client.newCall(retry).execute().use { retryResp ->
                        val retryText = retryResp.body?.string().orEmpty()
                        if (!retryResp.isSuccessful) {
                            throw apiError(retryResp.code, retryText)
                        }
                        return retryText
                    }
                }
            }
            if (!response.isSuccessful) throw apiError(response.code, text)
            return text
        }
    }

    private fun executeBytes(request: Request): ByteArray {
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (response.code == 401 && sessionStore.refreshToken != null) {
                if (refreshAccessToken()) {
                    val retry = request.newBuilder()
                        .header("Authorization", "Bearer ${sessionStore.accessToken}")
                        .build()
                    client.newCall(retry).execute().use { retryResp ->
                        val retryBytes = retryResp.body?.bytes() ?: ByteArray(0)
                        if (!retryResp.isSuccessful) {
                            throw apiError(
                                retryResp.code,
                                retryBytes.toString(Charsets.UTF_8),
                            )
                        }
                        return retryBytes
                    }
                }
            }
            if (!response.isSuccessful) {
                throw apiError(response.code, bytes.toString(Charsets.UTF_8))
            }
            return bytes
        }
    }

    private fun apiError(code: Int, text: String): IllegalStateException {
        val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
        return IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Ошибка API ($code)")
    }

    companion object {
        fun parseUser(json: JSONObject): UserProfile = UserProfile(
            id = json.getString("id"),
            nickname = json.optString("nickname"),
            firstName = json.optString("first_name"),
            lastName = json.optString("last_name"),
            photo = json.optString("photo").takeIf { it.isNotBlank() && it != "null" },
            photoUrl = json.optString("photo_url").takeIf { it.isNotBlank() && it != "null" },
            isOnline = json.optBoolean("is_online"),
            lastSeenAt = json.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() && it != "null" },
        )

        fun parseMessage(json: JSONObject): MessageItem {
            val waveformJson = json.optJSONArray("waveform")
            val waveform = if (waveformJson == null) {
                emptyList()
            } else {
                (0 until waveformJson.length()).map {
                    waveformJson.optDouble(it, 0.0).toFloat().coerceIn(0f, 1f)
                }
            }
            return MessageItem(
                id = json.getString("id"),
                chatId = json.optString("chat").takeIf { it.isNotBlank() },
                sender = json.optJSONObject("sender")?.let { parseUser(it) },
                messageType = json.optString("message_type", "text"),
                content = json.optString("content"),
                contentUrl = json.optString("content_url").takeIf { it.isNotBlank() },
                fileName = json.optString("file_name").takeIf { it.isNotBlank() },
                mimeType = json.optString("mime_type").takeIf { it.isNotBlank() },
                fileSize = if (json.has("file_size") && !json.isNull("file_size")) {
                    json.getLong("file_size")
                } else null,
                waveform = waveform,
                voiceDurationMs = if (
                    json.has("voice_duration_ms") && !json.isNull("voice_duration_ms")
                ) json.getLong("voice_duration_ms") else null,
                sentAt = json.optString("sent_at"),
                readAt = json.optString("read_at").takeIf { it.isNotBlank() && it != "null" },
                clientId = json.optString("client_id").takeIf { it.isNotBlank() && it != "null" },
                clientStatus = null,
            )
        }

        fun parseChat(json: JSONObject): ChatSummary = ChatSummary(
            id = json.getString("id"),
            partner = json.optJSONObject("partner")?.let { parseUser(it) },
            lastMessage = json.optJSONObject("last_message")?.let { parseMessage(it) },
            updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() },
        )

        fun parseNotification(json: JSONObject): AppNotification {
            val payloadJson = json.optJSONObject("payload")
            val payload = mutableMapOf<String, String>()
            if (payloadJson != null) {
                payloadJson.keys().forEach { key ->
                    payload[key] = payloadJson.opt(key)?.toString().orEmpty()
                }
            }
            return AppNotification(
                id = json.getString("id"),
                type = json.optString("notification_type"),
                title = json.optString("title"),
                body = json.optString("body"),
                isRead = json.optBoolean("is_read"),
                payload = payload,
                createdAt = json.optString("created_at").takeIf { it.isNotBlank() },
            )
        }
    }
}
