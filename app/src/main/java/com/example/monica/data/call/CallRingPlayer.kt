package com.example.monica.data.call

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import java.util.concurrent.atomic.AtomicReference

/**
 * Исходящий «трррр — пауза — трррр» (стандартный ringback ToneGenerator).
 * Входящий рингтон играет в [com.example.monica.push.IncomingCallService].
 */
object CallRingPlayer {
    private val toneRef = AtomicReference<ToneGenerator?>(null)

    fun startOutgoing(context: Context) {
        stopOutgoing()
        // STREAM_VOICE_CALL — слышно в выбранном маршруте звонка (ухо / громкая связь).
        val stream = AudioManager.STREAM_VOICE_CALL
        val tone = runCatching {
            ToneGenerator(stream, 80)
        }.getOrNull() ?: return
        if (!toneRef.compareAndSet(null, tone)) {
            runCatching { tone.release() }
            return
        }
        // TONE_SUP_RINGTONE — классический телефонный ringback (трррр… пауза…).
        runCatching {
            tone.startTone(ToneGenerator.TONE_SUP_RINGTONE)
        }.onFailure {
            stopOutgoing()
        }
        // На всякий случай поднимаем громкость voice call stream чуть выше нуля.
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.getStreamVolume(stream) == 0) {
                am.setStreamVolume(stream, (am.getStreamMaxVolume(stream) * 0.4f).toInt().coerceAtLeast(1), 0)
            }
        }
    }

    fun stopOutgoing() {
        val tone = toneRef.getAndSet(null) ?: return
        runCatching { tone.stopTone() }
        runCatching { tone.release() }
    }
}
