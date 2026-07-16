package com.example.monica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.monica.R

@Composable
fun AppIcon(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null,
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}
