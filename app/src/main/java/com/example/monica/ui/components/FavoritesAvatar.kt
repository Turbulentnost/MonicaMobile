package com.example.monica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Telegram-style bookmark avatar for Saved Messages / Избранное (как на вебе). */
@Composable
fun FavoritesAvatar(
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6EA0FF),
                        Color(0xFF3B6FF0),
                        Color(0xFF2F5ED8),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = "Избранное",
            tint = Color.White,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}
