package com.example.monica.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Иконка главного меню (4 блока).
 * В покое статична; при [play] один раз наклоняет верхний правый блок и замирает.
 */
@Composable
fun MainMenuIcon(
    play: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onFinished: () -> Unit = {},
) {
    val tilt = remember { Animatable(0f) }

    LaunchedEffect(play) {
        if (play) {
            if (tilt.value < 11.5f) {
                tilt.snapTo(0f)
                tilt.animateTo(
                    targetValue = 12f,
                    animationSpec = tween(
                        durationMillis = 700,
                        easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f),
                    ),
                )
            }
            onFinished()
        } else {
            tilt.snapTo(0f)
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.078f)
        val gap = size.toPx() * 0.16f
        val tile = (size.toPx() - gap) / 2f
        val radius = tile * 0.15f
        val cr = CornerRadius(radius, radius)

        fun drawTile(x: Float, y: Float) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(tile, tile),
                cornerRadius = cr,
                style = stroke,
            )
        }

        drawTile(0f, 0f)
        drawTile(0f, tile + gap)
        drawTile(tile + gap, tile + gap)

        val cx = tile + gap + tile / 2f
        val cy = tile / 2f
        rotate(degrees = tilt.value, pivot = Offset(cx, cy)) {
            drawTile(tile + gap, 0f)
        }
    }
}
