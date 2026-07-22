package com.example.monica.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.monica.R
import kotlin.math.abs

object NotificationStyle {
    fun accentColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.monica_notification_accent)

    fun applyMonicaChrome(builder: NotificationCompat.Builder, context: Context): NotificationCompat.Builder {
        return builder
            .setSmallIcon(R.drawable.ic_stat_monica)
            .setColor(accentColor(context))
            .setColorized(false)
    }

    fun letterAvatar(name: String, sizePx: Int = 128): Bitmap {
        val label = name.trim().removePrefix("@").take(1).uppercase().ifBlank { "M" }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = avatarColor(name)
            style = Paint.Style.FILL
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = sizePx * 0.42f
        }
        val y = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, radius, y, textPaint)
        return bitmap
    }

    fun messagingStyle(
        senderName: String,
        body: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): NotificationCompat.MessagingStyle {
        val cleanName = senderName.trim().ifBlank { "Monica" }
        val sender = Person.Builder()
            .setName(cleanName)
            .setKey(cleanName.lowercase())
            .build()
        val me = Person.Builder()
            .setName("Вы")
            .setKey("me")
            .build()
        return NotificationCompat.MessagingStyle(me)
            .addMessage(body, timestampMs, sender)
    }

    fun personIcon(name: String): IconCompat =
        IconCompat.createWithBitmap(letterAvatar(name))

    private fun avatarColor(seed: String): Int {
        val palette = intArrayOf(
            0xFF5B7FFF.toInt(),
            0xFF3D5AFE.toInt(),
            0xFF7C4DFF.toInt(),
            0xFF00897B.toInt(),
            0xFFFB8C00.toInt(),
            0xFFE53935.toInt(),
            0xFF8E24AA.toInt(),
            0xFF3949AB.toInt(),
        )
        val index = abs(seed.trim().lowercase().hashCode()) % palette.size
        return palette[index]
    }
}
