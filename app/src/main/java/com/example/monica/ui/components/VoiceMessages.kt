package com.example.monica.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.log10

data class VoiceRecordingSnapshot(
    val elapsedMs: Long = 0L,
    val amplitude: Float = 0f,
    val cancelled: Boolean = false,
)

class VoiceRecorderController(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private var output: File? = null

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        if (recorder != null) return false
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        return try {
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioEncodingBitRate(96_000)
            instance.setAudioSamplingRate(44_100)
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
            recorder = instance
            output = file
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (_: Exception) {
            runCatching { instance.release() }
            file.delete()
            false
        }
    }

    fun snapshot(cancelled: Boolean = false): VoiceRecordingSnapshot {
        val raw = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val normalized = if (raw <= 0) 0f else {
            ((20f * log10(raw / 32767f)) + 55f).div(55f).coerceIn(0f, 1f)
        }
        return VoiceRecordingSnapshot(
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            amplitude = normalized,
            cancelled = cancelled,
        )
    }

    fun stop(send: Boolean): File? {
        val current = recorder ?: return null
        val file = output
        recorder = null
        output = null
        runCatching { current.stop() }
        runCatching { current.reset() }
        runCatching { current.release() }
        if (!send) {
            file?.delete()
            return null
        }
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    fun release() {
        stop(send = false)
    }
}

@Composable
fun VoiceMessagePlayer(
    url: String,
    waveform: List<Float> = emptyList(),
    recordedDurationMs: Long? = null,
    modifier: Modifier = Modifier,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
) {
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(url) { mutableStateOf(false) }
    var duration by remember(url) { mutableIntStateOf(0) }
    var position by remember(url) { mutableIntStateOf(0) }

    DisposableEffect(url) {
        onDispose {
            player?.release()
            player = null
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(100)
        }
    }

    fun toggle() {
        val existing = player
        if (existing != null) {
            if (existing.isPlaying) {
                existing.pause()
                playing = false
            } else {
                existing.start()
                playing = true
            }
            return
        }
        val created = MediaPlayer()
        created.setDataSource(url)
        created.setOnPreparedListener {
            duration = it.duration
            it.start()
            playing = true
        }
        created.setOnCompletionListener {
            playing = false
            position = 0
            it.seekTo(0)
        }
        created.setOnErrorListener { _, _, _ ->
            playing = false
            true
        }
        player = created
        created.prepareAsync()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = ::toggle, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Воспроизвести",
                tint = foreground,
            )
        }
        val totalDuration = duration.takeIf { it > 0 }?.toLong()
            ?: recordedDurationMs
            ?: 0L
        val progress = if (totalDuration > 0L) position.toFloat() / totalDuration else 0f
        val visibleWaveform = waveform.ifEmpty {
            List(30) { index -> 0.28f + (((index * 17) % 11) / 10f) * 0.62f }
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
        ) {
            val bars = visibleWaveform.size
            val active = (bars * progress).toInt()
            visibleWaveform.forEachIndexed { index, level ->
                val h = size.height * level.coerceIn(0.08f, 1f)
                drawLine(
                    color = foreground.copy(alpha = if (index <= active) 0.95f else 0.32f),
                    start = Offset(index * size.width / bars, (size.height - h) / 2f),
                    end = Offset(index * size.width / bars, (size.height + h) / 2f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        Text(
            formatVoiceDuration(if (playing) position.toLong() else totalDuration),
            style = MaterialTheme.typography.labelMedium,
            color = foreground.copy(alpha = 0.8f),
        )
    }
}

fun formatVoiceDuration(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
