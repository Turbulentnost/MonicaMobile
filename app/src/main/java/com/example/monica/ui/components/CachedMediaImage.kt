package com.example.monica.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.monica.data.MediaImageCache
import com.example.monica.data.MessageItem
import com.example.monica.data.MonicaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CachedMediaImage(
    message: MessageItem,
    api: MonicaApi,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cacheKey = MediaImageCache.key(message.content, message.contentUrl)
    var file by remember(cacheKey) {
        mutableStateOf<File?>(MediaImageCache.getCached(context, cacheKey))
    }
    var loading by remember(cacheKey) { mutableStateOf(file == null) }
    var failed by remember(cacheKey) { mutableStateOf(false) }

    LaunchedEffect(cacheKey, message.contentUrl) {
        if (cacheKey == null) {
            loading = false
            failed = true
            return@LaunchedEffect
        }
        MediaImageCache.getCached(context, cacheKey)?.let {
            file = it
            loading = false
            failed = false
            return@LaunchedEffect
        }
        loading = true
        failed = false
        file = withContext(Dispatchers.IO) {
            MediaImageCache.getOrFetch(
                context = context,
                api = api,
                objectPath = message.content,
                contentUrl = message.contentUrl,
            )
        }
        loading = false
        failed = file == null
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            file != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .crossfade(true)
                        .build(),
                    contentDescription = message.fileName ?: "Фото",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            loading -> CircularProgressIndicator(strokeWidth = 2.dp)
            failed -> Text(
                "Не удалось загрузить фото",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
