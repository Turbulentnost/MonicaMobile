package com.example.monica.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object TimeFormat {
    private fun parse(iso: String?): Calendar? {
        if (iso.isNullOrBlank()) return null

        // Нормализуем Z → +00:00 для паттернов с XXX
        val normalized = iso.trim().let { raw ->
            when {
                raw.endsWith("Z", ignoreCase = true) -> raw.dropLast(1) + "+00:00"
                else -> raw
            }
        }

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                // Если в строке есть offset — парсим как есть; иначе считаем UTC
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(normalized) ?: continue
                return Calendar.getInstance().apply { time = date }
            } catch (_: Exception) {
            }
        }

        return try {
            val cleaned = iso.removeSuffix("Z").removeSuffix("z")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(cleaned.take(19)) ?: return null
            Calendar.getInstance().apply { time = date }
        } catch (_: Exception) {
            null
        }
    }

    private fun startOfDay(c: Calendar): Calendar =
        (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    fun chatListTime(iso: String?): String {
        val then = parse(iso) ?: return ""
        val now = Calendar.getInstance()
        val diffDays = ((startOfDay(now).timeInMillis - startOfDay(then).timeInMillis) / 86_400_000L).toInt()
        return when (diffDays) {
            0 -> SimpleDateFormat("HH:mm", Locale("ru")).format(then.time)
            1 -> "вчера"
            2 -> "позавчера"
            else -> {
                val fmt = if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "dd.MM" else "dd.MM.yy"
                SimpleDateFormat(fmt, Locale("ru")).format(then.time)
            }
        }
    }

    fun messageTime(iso: String?): String {
        val then = parse(iso) ?: return ""
        return SimpleDateFormat("HH:mm", Locale("ru")).format(then.time)
    }

    fun searchResultTime(iso: String?): String {
        val then = parse(iso) ?: return ""
        return SimpleDateFormat("dd.MM HH:mm", Locale("ru")).format(then.time)
    }

    fun dayLabel(iso: String?): String {
        val then = parse(iso) ?: return ""
        val now = Calendar.getInstance()
        val diffDays = ((startOfDay(now).timeInMillis - startOfDay(then).timeInMillis) / 86_400_000L).toInt()
        return when (diffDays) {
            0 -> "Сегодня"
            1 -> "Вчера"
            2 -> "Позавчера"
            else -> SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(then.time)
        }
    }

    fun dayKey(iso: String?): String {
        val then = parse(iso) ?: return ""
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(then.time)
    }

    fun lastSeen(iso: String?): String {
        val then = parse(iso) ?: return "не в сети"
        val now = Calendar.getInstance()
        val diffMs = (now.timeInMillis - then.timeInMillis).coerceAtLeast(0)
        val minutes = (diffMs / 60_000).toInt()
        val hours = (diffMs / 3_600_000).toInt()
        val days = (diffMs / 86_400_000).toInt()
        return when {
            minutes < 1 -> "был(а) только что"
            minutes < 60 -> "был(а) $minutes мин. назад"
            hours == 1 -> "был(а) час назад"
            hours < 24 -> "был(а) $hours ч. назад"
            days == 1 -> "был(а) день назад"
            days < 7 -> "был(а) $days дн. назад"
            else -> "был(а) давно"
        }
    }
}
