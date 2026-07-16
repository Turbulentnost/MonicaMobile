package com.example.monica.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val NeonRed = Color(0xFFFF1744)
private val NeonGlow = Color(0xFFFF5252)

/**
 * Красная линия бежит по периметру блока (не вращение фигуры вокруг центра).
 */
@Composable
fun NeonInviteBorder(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "neon-invite")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "neon-progress",
    )
    val borderPath = remember { Path() }
    val measure = remember { PathMeasure() }
    val segmentPath = remember { Path() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .then(
                if (enabled) {
                    Modifier.drawWithContent {
                        drawContent()

                        val stroke = 2.5.dp.toPx()
                        val glowStroke = 5.dp.toPx()
                        val inset = stroke / 2f
                        val w = size.width - stroke
                        val h = size.height - stroke
                        val radius = corner.toPx().coerceAtMost(minOf(w, h) / 2f)

                        // спокойная базовая рамка
                        drawRoundRect(
                            color = NeonRed.copy(alpha = 0.22f),
                            topLeft = Offset(inset, inset),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = stroke),
                        )

                        borderPath.reset()
                        borderPath.addRoundRect(
                            RoundRect(
                                left = inset,
                                top = inset,
                                right = inset + w,
                                bottom = inset + h,
                                radiusX = radius,
                                radiusY = radius,
                            ),
                        )
                        measure.setPath(borderPath, forceClosed = false)
                        val length = measure.length
                        if (length <= 0f) return@drawWithContent

                        // длина «бегущей» линии ≈ 18% периметра
                        val segmentLen = length * 0.18f
                        val start = (progress * length) % length

                        fun drawSegment(path: Path, color: Color, width: Float, alpha: Float) {
                            drawPath(
                                path = path,
                                color = color.copy(alpha = alpha),
                                style = Stroke(
                                    width = width,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }

                        segmentPath.reset()
                        val end = start + segmentLen
                        if (end <= length) {
                            measure.getSegment(start, end, segmentPath, startWithMoveTo = true)
                        } else {
                            // переход через начало контура
                            measure.getSegment(start, length, segmentPath, startWithMoveTo = true)
                            measure.getSegment(0f, end - length, segmentPath, startWithMoveTo = true)
                        }

                        // мягкое свечение + яркая линия
                        drawSegment(segmentPath, NeonGlow, glowStroke, 0.35f)
                        drawSegment(segmentPath, NeonRed, stroke, 1f)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
