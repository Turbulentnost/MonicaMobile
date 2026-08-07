package com.example.monica.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingUiState(
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val canSkipPrev: Boolean,
    val canSkipNext: Boolean,
    val canPlayPause: Boolean,
)

/**
 * Наблюдает системные MediaSession других приложений (Яндекс Музыка и т.п.).
 * Требует включённый Notification Listener для Monica.
 */
class ActiveMediaSessionRepository private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val sessionManager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val listenerComponent =
        ComponentName(appContext, MonicaNotificationListenerService::class.java)

    private val _nowPlaying = MutableStateFlow<NowPlayingUiState?>(null)
    val nowPlaying: StateFlow<NowPlayingUiState?> = _nowPlaying.asStateFlow()

    private val _listenerEnabled = MutableStateFlow(false)
    val listenerEnabled: StateFlow<Boolean> = _listenerEnabled.asStateFlow()

    private val _musicDisplayEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MUSIC_DISPLAY, true),
    )
    val musicDisplayEnabled: StateFlow<Boolean> = _musicDisplayEnabled.asStateFlow()

    private val _permissionPromptVisible = MutableStateFlow(false)
    val permissionPromptVisible: StateFlow<Boolean> = _permissionPromptVisible.asStateFlow()

    private var activeController: MediaController? = null
    private var suppressForCall = false
    private var started = false

    private val positionTicker = object : Runnable {
        override fun run() {
            val controller = activeController ?: return
            val state = controller.playbackState
            if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                publishFromController(controller)
                mainHandler.postDelayed(this, TICK_MS)
            }
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindBestSession(controllers)
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            activeController?.let { publishFromController(it) }
            scheduleTicker()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            activeController?.let { publishFromController(it) }
        }

        override fun onSessionDestroyed() {
            activeController?.unregisterCallback(this)
            activeController = null
            refreshSessions()
        }
    }

    fun start() {
        if (started) {
            refreshListenerAccess()
            refreshSessions()
            return
        }
        started = true
        refreshListenerAccess()
        if (_musicDisplayEnabled.value && _listenerEnabled.value) {
            runCatching {
                sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    listenerComponent,
                    mainHandler,
                )
            }
            refreshSessions()
        }
        updatePermissionPrompt()
    }

    fun stop() {
        // Держим listener на жизнь процесса; при stop UI только снимаем тикер.
        mainHandler.removeCallbacks(positionTicker)
    }

    fun setCallActive(active: Boolean) {
        suppressForCall = active
        if (active) {
            mainHandler.removeCallbacks(positionTicker)
            _nowPlaying.value = null
        } else {
            refreshSessions()
        }
    }

    fun onListenerConnected() {
        refreshListenerAccess()
        if (_musicDisplayEnabled.value && _listenerEnabled.value) {
            runCatching {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            }
            runCatching {
                sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    listenerComponent,
                    mainHandler,
                )
            }
            refreshSessions()
        }
        updatePermissionPrompt()
    }

    fun onListenerDisconnected() {
        refreshListenerAccess()
        clearController()
        _nowPlaying.value = null
        updatePermissionPrompt()
    }

    fun refreshListenerAccess() {
        _listenerEnabled.value = isNotificationListenerEnabled()
        if (!_listenerEnabled.value || !_musicDisplayEnabled.value) {
            clearController()
            _nowPlaying.value = null
        } else if (started) {
            runCatching {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            }
            runCatching {
                sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    listenerComponent,
                    mainHandler,
                )
            }
            refreshSessions()
        }
        updatePermissionPrompt()
    }

    fun dismissPermissionPrompt() {
        prefs.edit()
            .putBoolean(KEY_PROMPT_DISMISSED, true)
            .putBoolean(KEY_MUSIC_DISPLAY, false)
            .apply()
        _musicDisplayEnabled.value = false
        _permissionPromptVisible.value = false
        clearController()
        _nowPlaying.value = null
    }

    /**
     * Переключатель «Отображать музыку» в настройках.
     * @return true, если системный доступ уже есть; false — нужно открыть системные настройки.
     */
    fun setMusicDisplayEnabled(enabled: Boolean): Boolean {
        prefs.edit().putBoolean(KEY_MUSIC_DISPLAY, enabled).apply()
        _musicDisplayEnabled.value = enabled
        if (!enabled) {
            prefs.edit().putBoolean(KEY_PROMPT_DISMISSED, true).apply()
            _permissionPromptVisible.value = false
            clearController()
            _nowPlaying.value = null
            runCatching {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            }
            return true
        }
        // Из настроек баннер не возвращаем — пользователь уже в разделе «Отображать музыку».
        prefs.edit().putBoolean(KEY_PROMPT_DISMISSED, true).apply()
        _permissionPromptVisible.value = false
        refreshListenerAccess()
        return _listenerEnabled.value
    }

    fun playPause() {
        val controller = activeController ?: return
        val state = controller.playbackState ?: return
        val actions = state.actions
        val playing = state.state == PlaybackState.STATE_PLAYING
        val canPause = actions and (
            PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
            ) != 0L
        val canPlay = actions and (
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE
            ) != 0L
        when {
            playing && canPause -> controller.transportControls.pause()
            !playing && canPlay -> controller.transportControls.play()
        }
    }

    fun skipNext() {
        val controller = activeController ?: return
        val actions = controller.playbackState?.actions ?: return
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT == 0L) return
        controller.transportControls.skipToNext()
    }

    fun skipPrevious() {
        val controller = activeController ?: return
        val actions = controller.playbackState?.actions ?: return
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS == 0L) return
        controller.transportControls.skipToPrevious()
    }

    private fun refreshSessions() {
        if (!_musicDisplayEnabled.value || !_listenerEnabled.value || suppressForCall) {
            clearController()
            _nowPlaying.value = null
            return
        }
        val controllers = runCatching {
            sessionManager.getActiveSessions(listenerComponent)
        }.getOrElse { emptyList() }
        bindBestSession(controllers)
    }

    private fun bindBestSession(controllers: List<MediaController>?) {
        if (suppressForCall) {
            clearController()
            _nowPlaying.value = null
            return
        }
        val best = pickBest(controllers.orEmpty())
        if (best == null) {
            clearController()
            _nowPlaying.value = null
            return
        }
        if (activeController?.sessionToken == best.sessionToken) {
            publishFromController(best)
            scheduleTicker()
            return
        }
        clearController()
        activeController = best
        best.registerCallback(controllerCallback, mainHandler)
        publishFromController(best)
        scheduleTicker()
    }

    private fun pickBest(controllers: List<MediaController>): MediaController? {
        fun score(c: MediaController): Int {
            val state = c.playbackState?.state ?: return -1
            // Без активного пуша/воспроизведения сессия часто «висит» в STOPPED —
            // такую панель в Monica не показываем.
            if (!isActivelyPlaying(state)) return -1
            val meta = c.metadata
            val hasTitle = !meta?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() ||
                !meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank()
            val hasArtist = !meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrBlank() ||
                !meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).isNullOrBlank()
            if (!hasTitle && !hasArtist && meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) == null) {
                return -1
            }
            return when (state) {
                PlaybackState.STATE_PLAYING -> 3
                PlaybackState.STATE_BUFFERING -> 2
                else -> -1
            }
        }
        return controllers
            .map { it to score(it) }
            .filter { it.second >= 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun publishFromController(controller: MediaController) {
        if (suppressForCall) {
            _nowPlaying.value = null
            return
        }
        val meta = controller.metadata
        val state = controller.playbackState
        if (meta == null || state == null || !isActivelyPlaying(state.state)) {
            _nowPlaying.value = null
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        if (title.isBlank() && artist.isBlank()) {
            _nowPlaying.value = null
            return
        }
        val artwork = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val position = estimatePosition(state).coerceAtLeast(0L).let { pos ->
            if (duration > 0L) pos.coerceAtMost(duration) else pos
        }
        val actions = state.actions
        val playing = isActivelyPlaying(state.state)
        val canPlayPause = actions and (
            PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE
            ) != 0L
        _nowPlaying.value = NowPlayingUiState(
            title = title.ifBlank { artist },
            artist = artist,
            artwork = artwork,
            positionMs = position,
            durationMs = duration,
            isPlaying = playing,
            canSkipPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canPlayPause = canPlayPause,
        )
    }

    private fun estimatePosition(state: PlaybackState): Long {
        val base = state.position
        if (state.state != PlaybackState.STATE_PLAYING) return base
        val elapsed = android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        val speed = if (state.playbackSpeed == 0f) 1f else state.playbackSpeed
        return base + (elapsed * speed).toLong()
    }

    /** Только реальное воспроизведение — не paused/stopped «хвосты» без медиа-пуша. */
    private fun isActivelyPlaying(state: Int): Boolean {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun scheduleTicker() {
        mainHandler.removeCallbacks(positionTicker)
        val playing = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing && !suppressForCall) {
            mainHandler.postDelayed(positionTicker, TICK_MS)
        }
    }

    private fun clearController() {
        mainHandler.removeCallbacks(positionTicker)
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val expected = listenerComponent.flattenToString()
        val shortName = "${appContext.packageName}/${listenerComponent.className}"
        val className = listenerComponent.className
        return flat.split(':').any { entry ->
            entry.equals(expected, ignoreCase = true) ||
                entry.equals(shortName, ignoreCase = true) ||
                entry.contains(className)
        }
    }

    private fun updatePermissionPrompt() {
        val dismissed = prefs.getBoolean(KEY_PROMPT_DISMISSED, false)
        _permissionPromptVisible.value =
            _musicDisplayEnabled.value && !_listenerEnabled.value && !dismissed
    }

    companion object {
        private const val PREFS = "monica_now_playing"
        private const val KEY_PROMPT_DISMISSED = "listener_prompt_dismissed"
        private const val KEY_MUSIC_DISPLAY = "music_display_enabled"
        private const val TICK_MS = 400L

        @Volatile
        private var instance: ActiveMediaSessionRepository? = null

        fun get(context: Context): ActiveMediaSessionRepository {
            return instance ?: synchronized(this) {
                instance ?: ActiveMediaSessionRepository(context).also { instance = it }
            }
        }
    }
}
