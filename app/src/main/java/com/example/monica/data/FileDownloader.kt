package com.example.monica.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

object FileDownloader {
    /**
     * Скачивает файл чата (через auth proxy) и сохраняет в «Загрузки».
     * @return content Uri сохранённого файла
     */
    fun downloadChatFile(
        context: Context,
        api: MonicaApi,
        objectPath: String?,
        contentUrl: String?,
        fileName: String,
        mimeType: String? = null,
    ): Uri {
        val bytes = api.fetchMediaBytes(objectPath, contentUrl)
        if (bytes.isEmpty()) error("Пустой файл")
        val safeName = sanitizeFileName(fileName.ifBlank { "monica-file" })
        val mime = resolveMime(safeName, mimeType)
        return saveBytes(context, bytes, safeName, mime)
            ?: error("Не удалось сохранить файл")
    }

    fun openUri(context: Context, uri: Uri, mimeType: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Открыть файл")) }
    }

    private fun saveBytes(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: run {
                        resolver.delete(uri, null, null)
                        return null
                    }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists() && !dir.mkdirs()) return null
            val target = uniqueFile(dir, fileName)
            FileOutputStream(target).use { it.write(bytes) }
            @Suppress("DEPRECATION")
            Uri.fromFile(target)
        }
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var target = File(dir, fileName)
        if (!target.exists()) return target
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var i = 1
        while (target.exists()) {
            target = File(dir, "$base ($i)$ext")
            i += 1
        }
        return target
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "monica-file" }.take(180)
    }

    private fun resolveMime(fileName: String, mimeType: String?): String {
        if (!mimeType.isNullOrBlank() && mimeType != "null" && mimeType != "application/octet-stream") {
            return mimeType
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}
