package com.example.monica.data

import com.example.monica.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val assetName: String,
    val assetSize: Long,
    val releaseUrl: String,
    val notes: String,
)

class AppUpdateChecker(
    private val session: SessionStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun check(force: Boolean = false): AppUpdateInfo? {
        val now = System.currentTimeMillis()
        if (!force && now - session.updateLastCheckAt < CHECK_COOLDOWN_MS) {
            return null
        }
        session.updateLastCheckAt = now

        val owner = BuildConfig.UPDATE_GITHUB_OWNER
        val repo = BuildConfig.UPDATE_GITHUB_REPO
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Monica-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            val asset = findApkAsset(json) ?: return null
            val tag = json.optString("tag_name").trim()
            val body = json.optString("body").trim()
            val versionName = tag.removePrefix("v").ifBlank {
                json.optString("name").trim().removePrefix("v").ifBlank { tag }
            }
            val remoteVersionCode = parseVersionCode(body)
                ?: parseVersionCode(tag)
                ?: parseVersionCode(json.optString("name"))
                ?: 0

            val hasNewVersion = if (remoteVersionCode > 0) {
                remoteVersionCode > BuildConfig.VERSION_CODE
            } else {
                compareSemver(versionName, BuildConfig.VERSION_NAME) > 0
            }
            if (!hasNewVersion) return null

            val effectiveVersionCode = remoteVersionCode.takeIf { it > 0 }
                ?: semverToComparable(versionName).coerceAtLeast(BuildConfig.VERSION_CODE + 1)
            if (session.dismissedUpdateVersionCode == effectiveVersionCode) return null

            return AppUpdateInfo(
                versionName = versionName.ifBlank { tag.ifBlank { "новая" } },
                versionCode = effectiveVersionCode,
                apkUrl = asset.optString("browser_download_url"),
                assetName = asset.optString("name"),
                assetSize = asset.optLong("size", 0L),
                releaseUrl = json.optString("html_url"),
                notes = body,
            )
        }
    }

    private fun findApkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return asset
            }
        }
        return null
    }

    private fun parseVersionCode(text: String): Int? {
        val match = VERSION_CODE_REGEX.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun compareSemver(remote: String, current: String): Int {
        val remoteParts = remote.removePrefix("v").split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        val currentParts = current.removePrefix("v").split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        val max = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until max) {
            val left = remoteParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun semverToComparable(version: String): Int {
        val parts = version.removePrefix("v").split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        return parts.take(3).fold(0) { acc, part -> acc * 1000 + part.coerceIn(0, 999) }
    }

    companion object {
        const val CHECK_COOLDOWN_MS = 6 * 60 * 60 * 1000L
        private val VERSION_CODE_REGEX = Regex("""(?im)\bversionCode\s*:\s*(\d+)\b""")
    }
}
