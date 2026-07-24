package com.example.monica.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.monica.data.FileDownloader
import com.example.monica.data.MonicaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Скачивание вложения чата в «Загрузки» с запросом permission на старых API. */
@Composable
fun rememberChatFileDownloader(
    api: MonicaApi,
): (path: String?, url: String?, name: String, mime: String?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingDownload?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun runDownload(path: String?, url: String?, name: String, mime: String?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    FileDownloader.downloadChatFile(
                        context = context,
                        api = api,
                        objectPath = path,
                        contentUrl = url,
                        fileName = name,
                        mimeType = mime,
                    )
                }
                Toast.makeText(context, "Сохранено в Загрузки: $name", Toast.LENGTH_SHORT).show()
                FileDownloader.openUri(context, uri, mime)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.message?.takeIf { it.isNotBlank() } ?: "Не удалось скачать файл",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val job = pending
        pending = null
        if (granted && job != null) {
            runDownload(job.path, job.url, job.name, job.mime)
        } else if (!granted) {
            Toast.makeText(context, "Нужно разрешение на сохранение файлов", Toast.LENGTH_SHORT).show()
        }
    }

    return download@{ path, url, name, mime ->
        if (busy) {
            Toast.makeText(context, "Скачивание…", Toast.LENGTH_SHORT).show()
            return@download
        }
        val needsWrite = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsWrite) {
            pending = PendingDownload(path, url, name, mime)
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runDownload(path, url, name, mime)
        }
    }
}

private data class PendingDownload(
    val path: String?,
    val url: String?,
    val name: String,
    val mime: String?,
)
