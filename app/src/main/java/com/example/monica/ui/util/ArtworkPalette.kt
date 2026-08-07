package com.example.monica.ui.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

data class ArtworkPalette(
    /** Медиана отфильтрованных пикселей. */
    val median: Color,
    /** Среднее RGB тех же пикселей. */
    val average: Color,
    /** Смесь среднего и медианы — основной тон блока. */
    val base: Color,
    val bright: Color,
    val deep: Color,
) {
    companion object {
        val Fallback = ArtworkPalette(
            median = Color(0xFF3A4558),
            average = Color(0xFF3A4558),
            base = Color(0xFF3A4558),
            bright = Color(0xFF6E8AB8),
            deep = Color(0xFF1E2533),
        )
    }
}

/**
 * Сэмплирует обложку: медиана + среднее по насыщенным пикселям,
 * затем яркий и тёмный варианты для перелива.
 */
fun extractArtworkPalette(bitmap: Bitmap?): ArtworkPalette {
    if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
        return ArtworkPalette.Fallback
    }
    val sample = try {
        Bitmap.createScaledBitmap(bitmap, 28, 28, true)
    } catch (_: Exception) {
        return ArtworkPalette.Fallback
    }
    val w = sample.width
    val h = sample.height
    val pixels = IntArray(w * h)
    try {
        sample.getPixels(pixels, 0, w, 0, 0, w, h)
    } catch (_: Exception) {
        if (sample !== bitmap) sample.recycle()
        return ArtworkPalette.Fallback
    }
    if (sample !== bitmap) sample.recycle()

    val rs = ArrayList<Int>(pixels.size)
    val gs = ArrayList<Int>(pixels.size)
    val bs = ArrayList<Int>(pixels.size)
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L

    for (p in pixels) {
        if (AndroidColor.alpha(p) < 180) continue
        val r = AndroidColor.red(p)
        val g = AndroidColor.green(p)
        val b = AndroidColor.blue(p)
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val lum = (r + g + b) / 3
        val sat = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC
        // Отбрасываем почти чёрное / белое / серое — берём «музыкальный» цвет.
        if (lum < 28 || lum > 235) continue
        if (sat < 0.12f && lum in 80..180) continue
        rs.add(r)
        gs.add(g)
        bs.add(b)
        sumR += r
        sumG += g
        sumB += b
    }

    if (rs.isEmpty()) return ArtworkPalette.Fallback

    rs.sort()
    gs.sort()
    bs.sort()
    val mid = rs.size / 2
    val median = Color(
        red = rs[mid] / 255f,
        green = gs[mid] / 255f,
        blue = bs[mid] / 255f,
    )
    val n = rs.size.toFloat()
    val average = Color(
        red = (sumR / n / 255f).coerceIn(0f, 1f),
        green = (sumG / n / 255f).coerceIn(0f, 1f),
        blue = (sumB / n / 255f).coerceIn(0f, 1f),
    )
    // «Среднее медианное»: ближе к медиане, но с влиянием среднего.
    val base = lerp(median, average, 0.35f)
    val bright = boostForShimmer(base)
    val deep = deepen(base)
    return ArtworkPalette(
        median = median,
        average = average,
        base = base,
        bright = bright,
        deep = deep,
    )
}

private fun boostForShimmer(color: Color): Color {
    val hsl = FloatArray(3)
    AndroidColor.RGBToHSV(
        (color.red * 255).roundToInt().coerceIn(0, 255),
        (color.green * 255).roundToInt().coerceIn(0, 255),
        (color.blue * 255).roundToInt().coerceIn(0, 255),
        hsl,
    )
    hsl[1] = (hsl[1] * 1.25f + 0.08f).coerceIn(0.35f, 1f)
    hsl[2] = (hsl[2] * 0.55f + 0.55f).coerceIn(0.55f, 0.92f)
    return Color(AndroidColor.HSVToColor(hsl))
}

private fun deepen(color: Color): Color {
    val hsl = FloatArray(3)
    AndroidColor.RGBToHSV(
        (color.red * 255).roundToInt().coerceIn(0, 255),
        (color.green * 255).roundToInt().coerceIn(0, 255),
        (color.blue * 255).roundToInt().coerceIn(0, 255),
        hsl,
    )
    hsl[1] = (hsl[1] * 1.1f).coerceIn(0.2f, 1f)
    hsl[2] = (hsl[2] * 0.45f).coerceIn(0.12f, 0.42f)
    return Color(AndroidColor.HSVToColor(hsl))
}

fun Color.shiftHue(degrees: Float): Color {
    val hsl = FloatArray(3)
    AndroidColor.RGBToHSV(
        (red * 255).roundToInt().coerceIn(0, 255),
        (green * 255).roundToInt().coerceIn(0, 255),
        (blue * 255).roundToInt().coerceIn(0, 255),
        hsl,
    )
    hsl[0] = (hsl[0] + degrees + 360f) % 360f
    return Color(AndroidColor.HSVToColor(hsl))
}
