package com.example.monica.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AppUpdateInstaller(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val activeCall = AtomicReference<Call?>(null)
    private val warmCall = AtomicReference<Call?>(null)

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun openReleasePage(releaseUrl: String) {
        val url = releaseUrl.trim().ifBlank { return }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    /**
     * Прогревает DNS/TLS/редирект GitHub → CDN, пока пользователь ещё смотрит баннер.
     * Без этого первый клик «Обновить» часто висит на 0% несколько секунд.
     */
    fun warmUp(apkUrl: String) {
        val url = apkUrl.trim()
        if (url.isBlank()) return
        warmCall.getAndSet(null)?.cancel()
        // GitHub иногда отвечает 403 на HEAD — тогда берём 1 байт Range.
        val requests = listOf(
            Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Monica-Android")
                .build(),
            Request.Builder()
                .url(url)
                .header("User-Agent", "Monica-Android")
                .header("Range", "bytes=0-0")
                .build(),
        )
        for (request in requests) {
            if (Thread.interrupted()) return
            val call = client.newCall(request)
            warmCall.set(call)
            try {
                call.execute().use { response ->
                    if (response.isSuccessful || response.code == 206) return
                }
            } catch (_: Exception) {
                // пробуем следующий вариант / игнорируем
            } finally {
                warmCall.compareAndSet(call, null)
            }
        }
    }

    fun cancelDownload() {
        warmCall.getAndSet(null)?.cancel()
        activeCall.getAndSet(null)?.cancel()
    }

    fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (Float) -> Unit,
    ): File {
        warmCall.getAndSet(null)?.cancel()
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "Monica-Android")
            .header("Accept-Encoding", "identity")
            .build()
        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(updatesDir, "monica-update-${info.versionCode}.apk")
        if (target.exists()) target.delete()

        val call = client.newCall(request)
        activeCall.set(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Не удалось скачать APK: HTTP ${response.code}")
                }
                val body = response.body
                    ?: throw IllegalStateException("Пустой ответ при скачивании APK")
                val total = body.contentLength().takeIf { it > 0L } ?: info.assetSize
                var loaded = 0L
                var lastEmitAt = 0L
                var lastEmittedProgress = -1f

                fun emitProgress(force: Boolean = false) {
                    if (total <= 0L) return
                    val progress = (loaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    val now = SystemClock.elapsedRealtime()
                    if (
                        force ||
                        progress >= 1f ||
                        progress - lastEmittedProgress >= 0.01f ||
                        now - lastEmitAt >= PROGRESS_EMIT_MS
                    ) {
                        lastEmitAt = now
                        lastEmittedProgress = progress
                        onProgress(progress)
                    }
                }

                // Крупный буфер Okio: меньше системных вызовов, чем 8 КБ DEFAULT_BUFFER_SIZE.
                body.source().use { source ->
                    target.sink().buffer().use { sink ->
                        val buffer = okio.Buffer()
                        while (true) {
                            if (call.isCanceled()) {
                                throw java.util.concurrent.CancellationException("download cancelled")
                            }
                            val read = source.read(buffer, READ_CHUNK_BYTES)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            loaded += read
                            emitProgress()
                        }
                        sink.flush()
                    }
                }
                emitProgress(force = true)
            }
            onProgress(1f)
            return target
        } catch (e: IOException) {
            if (call.isCanceled()) {
                throw java.util.concurrent.CancellationException("download cancelled")
            }
            throw e
        } finally {
            activeCall.compareAndSet(call, null)
            if (call.isCanceled() && target.exists()) {
                target.delete()
            }
        }
    }

    fun startInstall(apk: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        appContext.startActivity(intent)
    }

    private companion object {
        const val READ_CHUNK_BYTES = 256L * 1024L
        const val PROGRESS_EMIT_MS = 100L
    }
}
