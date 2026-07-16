package com.example.monica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.monica.data.AvatarCache
import com.example.monica.data.SessionStore
import com.example.monica.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun UserAvatar(
    user: UserProfile?,
    size: Dp = 44.dp,
    showOnline: Boolean = false,
    isOnline: Boolean = false,
) {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val label = (user?.nickname ?: "?").take(2).uppercase()
    val photoKey = AvatarCache.cacheKey(user)
    val updatedAt = user?.updatedAt

    var localFile by remember(photoKey, updatedAt) {
        mutableStateOf(
            AvatarCache.getCachedFile(context, photoKey, updatedAt),
        )
    }

    LaunchedEffect(photoKey, updatedAt, user?.photo, user?.photoUrl) {
        if (photoKey.isNullOrBlank() || user == null) {
            localFile = null
            return@LaunchedEffect
        }
        val cached = AvatarCache.getCachedFile(context, photoKey, updatedAt)
        if (cached != null) {
            localFile = cached
            return@LaunchedEffect
        }
        val fetched = withContext(Dispatchers.IO) {
            AvatarCache.getOrFetch(context, session, user)
        }
        localFile = fetched
    }

    val file: File? = localFile?.takeIf { it.exists() && it.length() > 0L }

    Box(modifier = Modifier.size(size)) {
        if (file != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey(photoKey)
                    .diskCacheKey(photoKey)
                    .crossfade(true)
                    .build(),
                contentDescription = user?.nickname,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = (size.value * 0.32f).sp,
                )
            }
        }
        if (showOnline && isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(Color(0xFF34A853), CircleShape),
            )
        }
    }
}
