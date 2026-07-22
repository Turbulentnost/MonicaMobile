package com.example.monica.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.example.monica.data.CallAudioRoute
import com.example.monica.data.CallSession
import com.example.monica.data.CallUiState
import com.example.monica.data.CallUiStatus
import com.example.monica.data.IceServerConfig
import com.example.monica.data.MonicaApi
import com.example.monica.data.SessionStore
import com.example.monica.data.UserProfile
import com.example.monica.data.ws.CallSocket
import com.example.monica.push.CallNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.isActive

private val TERMINAL_ACTIONS = setOf(
    "call.rejected",
    "call.cancelled",
    "call.ended",
    "call.missed",
    "call.failed",
)

/**
 * Аудио/видеозвонок 1:1 — зеркало web `useCall`:
 * REST lifecycle + presence + `/ws/call/` + WebRTC (в т.ч. upgrade audio→video).
 */
class CallController(
    private val appContext: Context,
    private val api: MonicaApi,
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {
    private val callSocket = CallSocket(session, scope)
    private val peerMutex = Mutex()
    private val factoryLock = Any()
    private val sinkLock = Any()

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null

    private val localSinks = mutableSetOf<VideoSink>()
    private val remoteSinks = mutableSetOf<VideoSink>()

    private var accepted = false
    private var makingOffer = false
    private var hasRemoteDescription = false
    private val pendingIce = mutableListOf<IceCandidate>()
    private var mediaMode: String = "audio"

    private var timerJob: Job? = null
    private var disconnectJob: Job? = null
    private var remoteVideoWatchJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentRoute: CallAudioRoute = CallAudioRoute.Earpiece

    private val factoryReady = AtomicBoolean(false)

    init {
        scope.launch {
            callSocket.signals.collect { handleSignal(it) }
        }
    }

    fun eglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    fun hasCameraDevice(): Boolean = CameraHelper.hasCamera(appContext)

    fun attachLocalSink(sink: VideoSink) {
        synchronized(sinkLock) {
            localSinks.add(sink)
            localVideoTrack?.addSink(sink)
        }
    }

    fun detachLocalSink(sink: VideoSink) {
        synchronized(sinkLock) {
            localSinks.remove(sink)
            localVideoTrack?.removeSink(sink)
        }
    }

    fun attachRemoteSink(sink: VideoSink) {
        synchronized(sinkLock) {
            remoteSinks.add(sink)
            remoteVideoTrack?.addSink(sink)
        }
    }

    fun detachRemoteSink(sink: VideoSink) {
        synchronized(sinkLock) {
            remoteSinks.remove(sink)
            remoteVideoTrack?.removeSink(sink)
        }
    }

    fun onCallEvent(event: JSONObject) {
        val action = event.optString("action")
        val callJson = event.optJSONObject("call") ?: return
        val nextCall = runCatching { MonicaApi.parseCall(callJson) }.getOrNull() ?: return
        val myId = session.userId
        val clientId = session.callClientInstanceId

        if (
            nextCall.isCaller(myId) &&
            !nextCall.clientInstanceId.isNullOrBlank() &&
            nextCall.clientInstanceId != clientId
        ) {
            return
        }

        val current = _state.value.call
        if (action == "call.incoming") {
            if (
                current != null &&
                current.id != nextCall.id &&
                _state.value.status !in listOf(CallUiStatus.Idle, CallUiStatus.Ended)
            ) {
                return
            }
            closeMedia()
            mediaMode = nextCall.mediaMode
            updateCall(nextCall, CallUiStatus.Incoming, clearError = true)
            CallNotificationHelper.showIncoming(
                context = appContext,
                callId = nextCall.id,
                chatId = nextCall.chatId.orEmpty(),
                mediaMode = nextCall.mediaMode,
                callerNickname = nextCall.caller?.nickname.orEmpty(),
                callerId = nextCall.caller?.id.orEmpty(),
            )
            return
        }

        if (current != null && current.id != nextCall.id && action != "call.media_mode") return
        updateCall(nextCall)

        when (action) {
            "call.ringing" -> updateStatus(CallUiStatus.Outgoing)
            "call.accepted" -> {
                if (
                    nextCall.callee?.id == myId &&
                    !nextCall.acceptedClientInstanceId.isNullOrBlank() &&
                    nextCall.acceptedClientInstanceId != clientId
                ) {
                    finish("Звонок принят на другом устройстве.")
                    return
                }
                accepted = true
                CallNotificationHelper.cancel(appContext)
                updateStatus(CallUiStatus.Connecting)
                scope.launch {
                    try {
                        callSocket.connect(nextCall.id)
                        if (nextCall.isCaller(myId)) makeOffer()
                    } catch (e: Exception) {
                        finish(e.message ?: "Не удалось открыть канал звонка.")
                    }
                }
            }
            "call.media_mode" -> {
                mediaMode = "video"
                _state.update { it.copy(mediaMode = "video", call = nextCall.copy(mediaMode = "video")) }
                if (currentRoute != CallAudioRoute.Bluetooth) {
                    setAudioRoute(CallAudioRoute.Speaker)
                }
            }
            in TERMINAL_ACTIONS -> finish()
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Показать входящий из FCM, пока presence WS ещё не подключился.
     * Не трогает уже активный другой звонок.
     */
    fun presentIncomingFromPush(
        callId: String,
        chatId: String,
        mediaMode: String,
        callerId: String,
        callerNickname: String,
    ) {
        if (callId.isBlank()) return
        val status = _state.value.status
        if (status !in listOf(CallUiStatus.Idle, CallUiStatus.Ended, CallUiStatus.Incoming)) {
            return
        }
        val mode = if (mediaMode == "video") "video" else "audio"
        this.mediaMode = mode
        val caller = UserProfile(
            id = callerId.ifBlank { "unknown" },
            nickname = callerNickname.ifBlank { "пользователь" },
        )
        if (status == CallUiStatus.Incoming && _state.value.call?.id == callId) {
            CallNotificationHelper.showIncoming(
                context = appContext,
                callId = callId,
                chatId = chatId,
                mediaMode = mode,
                callerNickname = caller.nickname,
                callerId = caller.id,
            )
            return
        }
        val call = CallSession(
            id = callId,
            chatId = chatId.takeIf { it.isNotBlank() },
            caller = caller,
            callee = session.userId?.let { UserProfile(id = it, nickname = session.nickname.orEmpty()) },
            status = "ringing",
            mediaMode = mode,
        )
        updateCall(call, CallUiStatus.Incoming, clearError = true)
        CallNotificationHelper.showIncoming(
            context = appContext,
            callId = callId,
            chatId = chatId,
            mediaMode = mode,
            callerNickname = caller.nickname,
            callerId = caller.id,
        )
    }

    private fun emitError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun startCall(chatId: String, mode: String = "audio") {
        if (chatId.isBlank()) return
        if (_state.value.status !in listOf(CallUiStatus.Idle, CallUiStatus.Ended)) return
        val wantVideo = mode == "video"
        if (wantVideo && !hasCameraDevice()) {
            // Не создаём сессию на сервере — собеседник не должен видеть «входящий».
            emitError("Камера не найдена. Видеозвонок недоступен.")
            return
        }
        clearError()
        mediaMode = if (wantVideo) "video" else "audio"
        updateStatus(CallUiStatus.Outgoing)
        _state.update { it.copy(mediaMode = mediaMode) }
        scope.launch {
            var createdCallId: String? = null
            try {
                ensureFactory()
                ensureLocalMedia(wantVideo = wantVideo, requiredVideo = wantVideo)
                val initialRoute = if (wantVideo) CallAudioRoute.Speaker else CallAudioRoute.Earpiece
                setAudioRoute(initialRoute)
                val call = withContext(Dispatchers.IO) {
                    api.startCall(chatId, session.callClientInstanceId, mediaMode)
                }
                createdCallId = call.id
                mediaMode = call.mediaMode
                updateCall(call)
                callSocket.connect(call.id)
            } catch (e: Exception) {
                val id = createdCallId ?: _state.value.call?.id
                finish(humanizeError(e))
                // Если сессия уже создана — отменяем, иначе у собеседника «висит» входящий.
                if (!id.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        runCatching { api.cancelCall(id) }
                    }
                }
            }
        }
    }

    fun acceptCall() {
        val current = _state.value.call ?: return
        if (_state.value.status != CallUiStatus.Incoming) return
        val wantVideo = current.mediaMode == "video"
        clearError()
        mediaMode = current.mediaMode
        // Входящий экран остаётся видимым до успешного accept; при ошибке медиа
        // возвращаемся в Incoming, а не завершаем звонок локально.
        CallNotificationHelper.cancel(appContext)
        updateStatus(CallUiStatus.Connecting)
        scope.launch {
            try {
                ensureFactory()
                // Видео без камеры — принимаем звонок (режим video), локальная камера выкл.
                ensureLocalMedia(wantVideo = wantVideo, requiredVideo = false)
                val initialRoute = if (wantVideo) CallAudioRoute.Speaker else CallAudioRoute.Earpiece
                setAudioRoute(initialRoute)
                callSocket.connect(current.id)
                val call = withContext(Dispatchers.IO) {
                    api.acceptCall(current.id, session.callClientInstanceId)
                }
                updateCall(call)
            } catch (e: Exception) {
                // Не делаем finish(): входящий должен продолжать отображаться.
                closeMediaSoft()
                updateStatus(CallUiStatus.Incoming)
                updateCall(current)
                CallNotificationHelper.showIncoming(
                    context = appContext,
                    callId = current.id,
                    chatId = current.chatId.orEmpty(),
                    mediaMode = current.mediaMode,
                    callerNickname = current.caller?.nickname.orEmpty(),
                    callerId = current.caller?.id.orEmpty(),
                )
                emitError(humanizeError(e))
            }
        }
    }

    fun rejectCall() {
        val id = _state.value.call?.id ?: return
        finish()
        scope.launch(Dispatchers.IO) { runCatching { api.rejectCall(id) } }
    }

    fun cancelCall() {
        val id = _state.value.call?.id ?: return
        finish()
        scope.launch(Dispatchers.IO) { runCatching { api.cancelCall(id) } }
    }

    fun hangup() {
        val id = _state.value.call?.id ?: return
        finish()
        scope.launch(Dispatchers.IO) { runCatching { api.hangupCall(id, "hangup") } }
    }

    fun toggleMute() {
        val track = localAudioTrack ?: return
        val nextMuted = !_state.value.muted
        track.setEnabled(!nextMuted)
        _state.update { it.copy(muted = nextMuted) }
    }

    /** Цикл: earpiece → speaker → bluetooth(если есть) → earpiece. */
    fun cycleAudioRoute() {
        val bt = isBluetoothAvailable()
        val next = when (currentRoute) {
            CallAudioRoute.Earpiece -> CallAudioRoute.Speaker
            CallAudioRoute.Speaker -> if (bt) CallAudioRoute.Bluetooth else CallAudioRoute.Earpiece
            CallAudioRoute.Bluetooth -> CallAudioRoute.Earpiece
        }
        setAudioRoute(next)
    }

    fun setAudioRoute(route: CallAudioRoute) {
        if (route == CallAudioRoute.Bluetooth && !isBluetoothAvailable()) {
            setAudioRoute(CallAudioRoute.Speaker)
            return
        }
        currentRoute = route
        applyAudioRoute(route)
        _state.update {
            it.copy(
                audioRoute = route,
                bluetoothAvailable = isBluetoothAvailable(),
            )
        }
    }

    fun toggleCamera() {
        scope.launch {
            try {
                if (_state.value.cameraEnabled) {
                    disableCamera(renegotiate = accepted)
                } else {
                    enableCamera()
                }
            } catch (e: Exception) {
                emitError(humanizeError(e))
            }
        }
    }

    fun upgradeToVideo() {
        if (mediaMode == "video" && _state.value.cameraEnabled) return
        toggleCamera()
    }

    fun restoreActiveCallIfAny() {
        if (!session.isLoggedIn) return
        scope.launch {
            val active = withContext(Dispatchers.IO) {
                runCatching { api.activeCall() }.getOrNull()
            } ?: return@launch
            val myId = session.userId
            val clientId = session.callClientInstanceId
            val isCaller = active.isCaller(myId)
            if (isCaller && active.clientInstanceId != null && active.clientInstanceId != clientId) {
                return@launch
            }
            if (
                !isCaller &&
                !active.acceptedClientInstanceId.isNullOrBlank() &&
                active.acceptedClientInstanceId != clientId
            ) {
                return@launch
            }
            mediaMode = active.mediaMode
            updateCall(active)
            val isIncoming = active.callee?.id == myId &&
                active.status in listOf("pending", "ringing")
            if (isIncoming) {
                updateStatus(CallUiStatus.Incoming)
                CallNotificationHelper.showIncoming(
                    context = appContext,
                    callId = active.id,
                    chatId = active.chatId.orEmpty(),
                    mediaMode = active.mediaMode,
                    callerNickname = active.caller?.nickname.orEmpty(),
                    callerId = active.caller?.id.orEmpty(),
                )
                return@launch
            }
            updateStatus(CallUiStatus.Connecting)
            accepted = active.status == "active"
            try {
                ensureFactory()
                ensureLocalMedia(wantVideo = active.isVideo, requiredVideo = false)
                setAudioRoute(if (active.isVideo) CallAudioRoute.Speaker else CallAudioRoute.Earpiece)
                callSocket.connect(active.id)
                if (isCaller) makeOffer()
            } catch (e: Exception) {
                finish(humanizeError(e))
            }
        }
    }

    fun dispose() {
        closeMedia()
        releaseFactory()
    }

    private suspend fun enableCamera() {
        if (!hasCameraDevice()) error("Камера не найдена.")
        ensureFactory()
        if (mediaMode != "video") {
            val callId = _state.value.call?.id ?: error("Нет активного звонка")
            val updated = withContext(Dispatchers.IO) {
                api.setCallMediaMode(callId, "video")
            }
            mediaMode = "video"
            updateCall(updated.copy(mediaMode = "video"))
            _state.update { it.copy(mediaMode = "video") }
            if (currentRoute != CallAudioRoute.Bluetooth) {
                setAudioRoute(CallAudioRoute.Speaker)
            }
        }
        ensureLocalVideo()
        replaceOrAddVideoTrack(localVideoTrack)
        _state.update {
            it.copy(
                cameraEnabled = true,
                mediaMode = "video",
                videoEpoch = it.videoEpoch + 1,
            )
        }
        if (accepted) makeOffer()
    }

    private suspend fun disableCamera(renegotiate: Boolean) {
        val pc = peerConnection
        val sender = pc?.senders?.find { it.track()?.kind() == "video" }
        sender?.setTrack(null, false)
        stopVideoCapture()
        _state.update {
            it.copy(
                cameraEnabled = false,
                videoEpoch = it.videoEpoch + 1,
            )
        }
        if (renegotiate && accepted) makeOffer()
    }

    private suspend fun replaceOrAddVideoTrack(videoTrack: VideoTrack?) {
        val pc = createPeer(wantVideo = true)
        if (videoTrack == null) return
        val existingVideo = pc.senders.find { it.track()?.kind() == "video" }
        if (existingVideo != null) {
            existingVideo.setTrack(videoTrack, false)
        } else {
            pc.addTrack(videoTrack, listOf("monica_stream"))
        }
    }

    private fun updateStatus(status: CallUiStatus) {
        _state.update { it.copy(status = status) }
    }

    private fun updateCall(
        call: CallSession,
        status: CallUiStatus? = null,
        clearError: Boolean = false,
    ) {
        mediaMode = call.mediaMode
        val partner = call.partner(session.userId)
        _state.update {
            it.copy(
                call = call,
                partner = partner,
                mediaMode = call.mediaMode,
                status = status ?: it.status,
                error = if (clearError) null else it.error,
                bluetoothAvailable = isBluetoothAvailable(),
            )
        }
    }

    private fun finish(message: String? = null) {
        closeMedia()
        mediaMode = "audio"
        currentRoute = CallAudioRoute.Earpiece
        CallNotificationHelper.cancel(appContext)
        _state.update {
            it.copy(
                status = CallUiStatus.Ended,
                mediaMode = "audio",
                cameraEnabled = false,
                hasRemoteVideo = false,
                muted = false,
                audioRoute = CallAudioRoute.Earpiece,
                bluetoothAvailable = false,
                elapsedSeconds = 0,
                error = message,
            )
        }
    }

    private fun failCall(message: String, reason: String) {
        val callId = _state.value.call?.id
        finish(message)
        if (!callId.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                runCatching { api.hangupCall(callId, reason) }
            }
        }
    }

    private fun closeMedia() {
        timerJob?.cancel()
        timerJob = null
        disconnectJob?.cancel()
        disconnectJob = null
        remoteVideoWatchJob?.cancel()
        remoteVideoWatchJob = null
        accepted = false
        makingOffer = false
        hasRemoteDescription = false
        pendingIce.clear()
        callSocket.disconnect()
        stopVideoCapture()
        synchronized(sinkLock) {
            remoteVideoTrack?.let { track ->
                remoteSinks.forEach { track.removeSink(it) }
            }
            remoteVideoTrack = null
            localSinks.clear()
            remoteSinks.clear()
        }
        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
        }
        peerConnection = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        releaseAudioFocus()
    }

    private fun stopVideoCapture() {
        synchronized(sinkLock) {
            localVideoTrack?.let { track ->
                localSinks.forEach { track.removeSink(it) }
            }
            runCatching { localVideoTrack?.dispose() }
            localVideoTrack = null
        }
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { surfaceHelper?.dispose() }
        surfaceHelper = null
    }

    private suspend fun ensureFactory() {
        if (factoryReady.get() && factory != null && eglBase != null) return
        withContext(Dispatchers.Main) {
            synchronized(factoryLock) {
                if (factoryReady.get() && factory != null && eglBase != null) return@withContext
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions(),
                )
                val egl = EglBase.create()
                eglBase = egl
                val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
                val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
                factory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory()
                factoryReady.set(true)
            }
        }
    }

    private fun releaseFactory() {
        synchronized(factoryLock) {
            factory?.dispose()
            factory = null
            eglBase?.release()
            eglBase = null
            factoryReady.set(false)
        }
    }

    /**
     * @param wantVideo желаем ли видеодорожку
     * @param requiredVideo если true — отсутствие камеры = ошибка (исходящий видеозвонок);
     *   если false — просто идём без локального видео (входящий / accept без камеры).
     */
    private suspend fun ensureLocalMedia(wantVideo: Boolean, requiredVideo: Boolean = wantVideo) {
        ensureLocalAudio()
        if (wantVideo) {
            if (hasCameraDevice()) {
                ensureLocalVideo()
                _state.update { it.copy(cameraEnabled = true, mediaMode = "video") }
            } else if (requiredVideo) {
                error("Камера не найдена.")
            } else {
                _state.update { it.copy(cameraEnabled = false, mediaMode = "video") }
            }
        } else {
            _state.update { it.copy(cameraEnabled = false) }
        }
    }

    private fun ensureLocalAudio() {
        val f = factory ?: error("PeerConnectionFactory не инициализирован")
        if (localAudioTrack != null) return
        val constraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        audioSource = f.createAudioSource(constraints)
        localAudioTrack = f.createAudioTrack("monica_audio", audioSource).also {
            it.setEnabled(true)
        }
    }

    private fun ensureLocalVideo() {
        if (localVideoTrack != null) {
            localVideoTrack?.setEnabled(true)
            return
        }
        val f = factory ?: error("PeerConnectionFactory не инициализирован")
        val egl = eglBase ?: error("EglBase не инициализирован")
        val capturer = CameraHelper.createCapturer(appContext)
            ?: error("Камера не найдена.")
        videoCapturer = capturer
        surfaceHelper = SurfaceTextureHelper.create("MonicaCapture", egl.eglBaseContext)
        videoSource = f.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceHelper, appContext, videoSource!!.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        localVideoTrack = f.createVideoTrack("monica_video", videoSource).also { track ->
            track.setEnabled(true)
            synchronized(sinkLock) {
                localSinks.forEach { track.addSink(it) }
            }
        }
        _state.update { it.copy(videoEpoch = it.videoEpoch + 1) }
    }

    /** Сброс локальных WebRTC-ресурсов без смены статуса звонка (для повторного Incoming). */
    private fun closeMediaSoft() {
        timerJob?.cancel()
        timerJob = null
        disconnectJob?.cancel()
        disconnectJob = null
        remoteVideoWatchJob?.cancel()
        remoteVideoWatchJob = null
        accepted = false
        makingOffer = false
        hasRemoteDescription = false
        pendingIce.clear()
        callSocket.disconnect()
        stopVideoCapture()
        synchronized(sinkLock) {
            remoteVideoTrack?.let { track ->
                remoteSinks.forEach { track.removeSink(it) }
            }
            remoteVideoTrack = null
            localSinks.clear()
            remoteSinks.clear()
        }
        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
        }
        peerConnection = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        releaseAudioFocus()
    }

    private suspend fun createPeer(wantVideo: Boolean? = null): PeerConnection = peerMutex.withLock {
        peerConnection?.let { return it }
        ensureFactory()
        val needVideo = wantVideo ?: (mediaMode == "video")
        ensureLocalMedia(wantVideo = needVideo, requiredVideo = false)
        val iceConfigs = withContext(Dispatchers.IO) { api.iceServers() }
        val iceServers = iceConfigs.map { it.toRtcIceServer() }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val f = factory ?: error("PeerConnectionFactory не инициализирован")
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dc: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val data = JSONObject()
                    .put(
                        "candidate",
                        JSONObject()
                            .put("candidate", candidate.sdp)
                            .put("sdpMid", candidate.sdpMid)
                            .put("sdpMLineIndex", candidate.sdpMLineIndex),
                    )
                scope.launch { callSocket.sendSignal("call.ice", data) }
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                scope.launch {
                    if (track is VideoTrack) {
                        synchronized(sinkLock) {
                            remoteVideoTrack?.let { old ->
                                remoteSinks.forEach { old.removeSink(it) }
                            }
                            remoteVideoTrack = track
                            remoteSinks.forEach { track.addSink(it) }
                        }
                        mediaMode = "video"
                        _state.update {
                            it.copy(
                                mediaMode = "video",
                                videoEpoch = it.videoEpoch + 1,
                            )
                        }
                        watchRemoteVideo(track)
                    }
                    markActive()
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                scope.launch {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            disconnectJob?.cancel()
                            disconnectJob = null
                            markActive()
                            callSocket.send(JSONObject().put("action", "call.connected"))
                            applyAudioRoute(currentRoute)
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            disconnectJob?.cancel()
                            disconnectJob = scope.launch {
                                delay(8_000)
                                val pc = peerConnection
                                if (
                                    pc != null &&
                                    pc.connectionState() in listOf(
                                        PeerConnection.PeerConnectionState.DISCONNECTED,
                                        PeerConnection.PeerConnectionState.FAILED,
                                    )
                                ) {
                                    failCall("Соединение звонка прервано.", "ice_disconnected")
                                }
                            }
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            if (_state.value.status != CallUiStatus.Ended) {
                                failCall("Соединение звонка прервано.", "peer_connection_failed")
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
        val pc = f.createPeerConnection(rtcConfig, observer)
            ?: error("Не удалось создать PeerConnection")
        localAudioTrack?.let { pc.addTrack(it, listOf("monica_stream")) }
        if (needVideo) {
            val localVideo = localVideoTrack
            if (localVideo != null) {
                pc.addTrack(localVideo, listOf("monica_stream"))
            } else {
                // Видеозвонок без своей камеры: принимаем видео собеседника (recvonly).
                pc.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,
                    ),
                )
            }
        }
        peerConnection = pc
        return pc
    }

    private fun watchRemoteVideo(track: VideoTrack) {
        remoteVideoWatchJob?.cancel()
        remoteVideoWatchJob = scope.launch {
            while (isActive && remoteVideoTrack === track) {
                val live = track.state() == MediaStreamTrack.State.LIVE && track.enabled()
                _state.update { prev ->
                    if (prev.hasRemoteVideo == live) prev else prev.copy(hasRemoteVideo = live)
                }
                delay(700)
            }
        }
    }

    private suspend fun makeOffer() {
        if (makingOffer || !accepted) return
        makingOffer = true
        try {
            val pc = createPeer()
            val offer = pc.createOfferAwait(MediaConstraints())
            pc.setLocalDescriptionAwait(offer)
            val sdpJson = JSONObject()
                .put("type", offer.type.canonicalForm())
                .put("sdp", offer.description)
            callSocket.sendSignal("call.offer", JSONObject().put("sdp", sdpJson))
            if (_state.value.status != CallUiStatus.Active) {
                updateStatus(CallUiStatus.Connecting)
            }
        } catch (e: Exception) {
            failCall(humanizeError(e), "offer_failed")
        } finally {
            makingOffer = false
        }
    }

    private suspend fun handleSignal(raw: JSONObject) {
        try {
            val action = raw.optString("action")
            val data = raw.optJSONObject("data")
            val flat = if (data != null) {
                JSONObject(raw.toString()).also { merged ->
                    data.keys().forEach { key -> merged.put(key, data.get(key)) }
                }
            } else {
                raw
            }

            when (action) {
                "call.offer" -> {
                    val pc = createPeer()
                    val offer = parseSdp(flat.opt("sdp"), SessionDescription.Type.OFFER)
                    pc.setRemoteDescriptionAwait(offer)
                    hasRemoteDescription = true
                    flushIce(pc)
                    val answer = pc.createAnswerAwait(MediaConstraints())
                    pc.setLocalDescriptionAwait(answer)
                    val sdpJson = JSONObject()
                        .put("type", answer.type.canonicalForm())
                        .put("sdp", answer.description)
                    callSocket.sendSignal("call.answer", JSONObject().put("sdp", sdpJson))
                    if (_state.value.status != CallUiStatus.Active) {
                        updateStatus(CallUiStatus.Connecting)
                    }
                }
                "call.answer" -> {
                    val pc = createPeer()
                    val answer = parseSdp(flat.opt("sdp"), SessionDescription.Type.ANSWER)
                    pc.setRemoteDescriptionAwait(answer)
                    hasRemoteDescription = true
                    flushIce(pc)
                }
                "call.ice" -> {
                    val candidate = parseIceCandidate(flat.opt("candidate")) ?: return
                    val pc = createPeer()
                    if (hasRemoteDescription) {
                        pc.addIceCandidate(candidate)
                    } else {
                        pendingIce.add(candidate)
                    }
                }
                "call.connected" -> markActive()
                "call.media_mode" -> {
                    mediaMode = "video"
                    val callJson = raw.optJSONObject("call")
                    if (callJson != null) {
                        runCatching { MonicaApi.parseCall(callJson) }.getOrNull()?.let { updateCall(it) }
                    }
                    _state.update { it.copy(mediaMode = "video") }
                    if (currentRoute != CallAudioRoute.Bluetooth) {
                        setAudioRoute(CallAudioRoute.Speaker)
                    }
                }
                "call.rejoin" -> {
                    val call = _state.value.call
                    if (call?.status == "active" && call.isCaller(session.userId)) {
                        accepted = true
                        makeOffer()
                    }
                }
                in TERMINAL_ACTIONS -> {
                    if (flat.optString("detail") == "signaling_lost") {
                        failCall("Соединение с сервером звонка потеряно.", "signaling_lost")
                    } else {
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            failCall(humanizeError(e), "signaling_failed")
        }
    }

    private fun flushIce(pc: PeerConnection) {
        val queued = pendingIce.toList()
        pendingIce.clear()
        queued.forEach { candidate ->
            runCatching { pc.addIceCandidate(candidate) }
        }
    }

    private fun markActive() {
        if (_state.value.status != CallUiStatus.Active) {
            updateStatus(CallUiStatus.Active)
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        val startedAt = System.currentTimeMillis()
        timerJob = scope.launch {
            while (true) {
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                _state.update { it.copy(elapsedSeconds = elapsed) }
                delay(1_000)
            }
        }
    }

    private fun isBluetoothAvailable(): Boolean {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            if (devices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
            ) {
                return true
            }
        }
        @Suppress("DEPRECATION")
        return am.isBluetoothScoAvailableOffCall
    }

    private fun applyAudioRoute(route: CallAudioRoute) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        requestAudioFocus(am)
        when (route) {
            CallAudioRoute.Earpiece -> {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                runCatching {
                    am.stopBluetoothSco()
                    @Suppress("DEPRECATION")
                    am.isBluetoothScoOn = false
                }
            }
            CallAudioRoute.Speaker -> {
                runCatching {
                    am.stopBluetoothSco()
                    @Suppress("DEPRECATION")
                    am.isBluetoothScoOn = false
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }
            CallAudioRoute.Bluetooth -> {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                runCatching {
                    am.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    am.isBluetoothScoOn = true
                }
            }
        }
    }

    private fun requestAudioFocus(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
    }

    private fun releaseAudioFocus() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        runCatching {
            am.stopBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = false
        }
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun parseSdp(raw: Any?, fallbackType: SessionDescription.Type): SessionDescription {
        return when (raw) {
            is String -> SessionDescription(fallbackType, raw)
            is JSONObject -> {
                val type = when (raw.optString("type").lowercase()) {
                    "offer" -> SessionDescription.Type.OFFER
                    "answer" -> SessionDescription.Type.ANSWER
                    else -> fallbackType
                }
                SessionDescription(type, raw.optString("sdp"))
            }
            else -> error("Некорректный SDP")
        }
    }

    private fun parseIceCandidate(raw: Any?): IceCandidate? {
        return when (raw) {
            is JSONObject -> {
                val sdp = raw.optString("candidate")
                if (sdp.isBlank()) return null
                IceCandidate(
                    raw.optString("sdpMid").takeIf { it.isNotBlank() },
                    raw.optInt("sdpMLineIndex", 0),
                    sdp,
                )
            }
            is String -> {
                if (raw.isBlank()) null else IceCandidate(null, 0, raw)
            }
            else -> null
        }
    }

    private fun IceServerConfig.toRtcIceServer(): PeerConnection.IceServer {
        val builder = PeerConnection.IceServer.builder(urls)
        if (!username.isNullOrBlank() && !credential.isNullOrBlank()) {
            builder.setUsername(username).setPassword(credential)
        }
        return builder.createIceServer()
    }

    private fun humanizeError(error: Throwable): String {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("Permission", ignoreCase = true) ||
                msg.contains("CAMERA", ignoreCase = true) ->
                "Разрешите доступ к камере и микрофону и попробуйте снова."
            msg.contains("RECORD_AUDIO", ignoreCase = true) ->
                "Разрешите доступ к микрофону и попробуйте снова."
            msg.contains("камер", ignoreCase = true) ||
                msg.contains("camera", ignoreCase = true) ->
                "Камера не найдена или недоступна."
            msg.contains("offline", ignoreCase = true) ->
                "Собеседник не в сети."
            msg.contains("busy", ignoreCase = true) ->
                "Собеседник сейчас занят."
            msg.isNotBlank() -> msg
            else -> "Не удалось начать звонок."
        }
    }
}

object CameraHelper {
    fun hasCamera(context: Context): Boolean {
        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(false)
        }
        return enumerator.deviceNames.isNotEmpty()
    }

    fun createCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(false)
        }
        val names = enumerator.deviceNames
        val front = names.firstOrNull { enumerator.isFrontFacing(it) }
        val chosen = front ?: names.firstOrNull() ?: return null
        return enumerator.createCapturer(chosen, null)
    }
}

private suspend fun PeerConnection.createOfferAwait(constraints: MediaConstraints): SessionDescription =
    suspendCoroutine { cont ->
        createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) cont.resume(desc)
                else cont.resumeWithException(IllegalStateException("Пустой offer"))
            }

            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "createOffer failed"))
            }
        }, constraints)
    }

private suspend fun PeerConnection.createAnswerAwait(constraints: MediaConstraints): SessionDescription =
    suspendCoroutine { cont ->
        createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) cont.resume(desc)
                else cont.resumeWithException(IllegalStateException("Пустой answer"))
            }

            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "createAnswer failed"))
            }
        }, constraints)
    }

private suspend fun PeerConnection.setLocalDescriptionAwait(desc: SessionDescription) =
    suspendCoroutine { cont ->
        setLocalDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "setLocalDescription failed"))
            }
        }, desc)
    }

private suspend fun PeerConnection.setRemoteDescriptionAwait(desc: SessionDescription) =
    suspendCoroutine { cont ->
        setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "setRemoteDescription failed"))
            }
        }, desc)
    }

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}
