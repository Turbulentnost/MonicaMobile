package com.example.monica.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Presigned URL MinIO часто содержат localhost/127.0.0.1 —
 * на телефоне их нужно заменить на хост API (LAN IP).
 */
object MediaUrls {
    fun resolve(apiBaseUrl: String, url: String?): String? {
        if (url.isNullOrBlank()) return null
        val api = apiBaseUrl.toHttpUrlOrNull() ?: return url
        val media = url.toHttpUrlOrNull() ?: return url
        val host = media.host.lowercase()
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "minio") {
            return media.newBuilder().host(api.host).build().toString()
        }
        return url
    }

    /** Прокси через Django: работает, даже если порт MinIO закрыт с телефона. */
    fun proxyUrl(apiBaseUrl: String, objectPath: String?): String? {
        val path = objectPath?.trim()?.trimStart('/') ?: return null
        if (path.isBlank()) return null
        val base = apiBaseUrl.trimEnd('/')
        return "$base/api/media/?path=${java.net.URLEncoder.encode(path, Charsets.UTF_8.name())}"
    }
}
