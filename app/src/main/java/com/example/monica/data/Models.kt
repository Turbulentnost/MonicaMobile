package com.example.monica.data

data class UserProfile(
    val id: String,
    val nickname: String,
    val firstName: String = "",
    val lastName: String = "",
    /** Стабильный путь в MinIO, напр. `user-avatars/{uuid}.jpg` */
    val photo: String? = null,
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null,
    /** Для инвалидации кэша аватара вместе с `photo` */
    val updatedAt: String? = null,
    val email: String = "",
    val city: String = "",
    val birthDate: String? = null,
) {
    /** Фамилия и имя; если пусто — никнейм. */
    val displayName: String
        get() {
            val name = listOf(lastName, firstName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            return name.ifBlank { nickname.ifBlank { "—" } }
        }
}

data class ChatSummary(
    val id: String,
    val partner: UserProfile?,
    val lastMessage: MessageItem?,
    val updatedAt: String? = null,
    val chatType: String = "direct",
    val isGroup: Boolean = false,
    val title: String? = null,
    val photo: String? = null,
    val photoUrl: String? = null,
    /** Персональный фон чата (presigned URL) для текущего пользователя. */
    val backgroundUrl: String? = null,
    val membersCount: Int = 0,
    val members: List<UserProfile> = emptyList(),
) {
    val displayTitle: String
        get() = when {
            isGroup -> title?.takeIf { it.isNotBlank() } ?: "Группа"
            else -> partner?.displayName ?: "—"
        }

    /** Для UserAvatar / AvatarCache — синтетический профиль группы (как на вебе). */
    fun avatarUser(): UserProfile? {
        if (!isGroup) return partner
        return UserProfile(
            id = "group-$id",
            nickname = displayTitle,
            firstName = displayTitle.take(2),
            photo = photo,
            photoUrl = photoUrl,
            updatedAt = updatedAt,
        )
    }
}

data class MessageAttachment(
    val path: String? = null,
    val contentUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
)

data class ForwardBundleItem(
    val originalId: String,
    val originalChatId: String,
    val sender: UserProfile?,
    val messageType: String,
    val content: String,
    val contentUrl: String? = null,
    val caption: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val attachments: List<MessageAttachment> = emptyList(),
    val waveform: List<Float> = emptyList(),
    val voiceDurationMs: Long? = null,
    val sentAt: String,
)

data class PendingForward(
    val sourceChatId: String,
    val targetChatId: String,
    val messageIds: List<String>,
    val preview: MessageItem,
)

data class ReplySummary(
    val id: String,
    val chatId: String,
    val sender: UserProfile?,
    val preview: String,
    val messageType: String,
)

data class MessageItem(
    val id: String,
    val chatId: String? = null,
    val sender: UserProfile?,
    val messageType: String,
    val content: String,
    val contentUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val caption: String? = null,
    val attachments: List<MessageAttachment> = emptyList(),
    val waveform: List<Float> = emptyList(),
    val voiceDurationMs: Long? = null,
    val forwardBundle: List<ForwardBundleItem> = emptyList(),
    val forwardedFromId: String? = null,
    val replyToSummary: ReplySummary? = null,
    val sentAt: String,
    val readAt: String? = null,
    /** Корреляция optimistic → server ack */
    val clientId: String? = null,
    /** `sending` пока ждём `message.new` */
    val clientStatus: String? = null,
    /** 0f…1f во время HTTP-загрузки файла/фото; null — не загружается */
    val uploadProgress: Float? = null,
    /** Локальный путь превью (исходящее фото до ответа сервера) */
    val localPreviewPath: String? = null,
) {
    val isPending: Boolean
        get() = clientStatus == "sending" || id.startsWith("temp-")

    val isUploading: Boolean
        get() = uploadProgress != null

    /** Только для своих: sending / sent / read */
    fun deliveryStatus(isOwn: Boolean): DeliveryStatus? {
        if (!isOwn) return null
        if (isPending) return DeliveryStatus.Sending
        if (!readAt.isNullOrBlank()) return DeliveryStatus.Read
        return DeliveryStatus.Sent
    }
}

enum class DeliveryStatus(val label: String) {
    Sending("отправляется"),
    Sent("отправлено"),
    Read("прочитано"),
}

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val payload: Map<String, String> = emptyMap(),
    val createdAt: String? = null,
)

data class PrivateSessionInfo(
    val id: String,
    val chatId: String?,
    val status: String,
    val handshake: Boolean = false,
)

data class PrivateNavTarget(
    val sessionId: String,
    val chatId: String,
)

enum class CallUiStatus {
    Idle,
    Outgoing,
    Incoming,
    Connecting,
    Active,
    Ended,
}

/** Маршрут вывода звука во время звонка. */
enum class CallAudioRoute {
    Earpiece,
    Speaker,
    Bluetooth,
}

data class CallSession(
    val id: String,
    val chatId: String?,
    val caller: UserProfile?,
    val callee: UserProfile?,
    val status: String,
    val mediaMode: String = "audio",
    val clientInstanceId: String? = null,
    val acceptedClientInstanceId: String? = null,
    val endReason: String? = null,
) {
    val isVideo: Boolean get() = mediaMode == "video"

    fun partner(myUserId: String?): UserProfile? {
        if (myUserId.isNullOrBlank()) return null
        return if (caller?.id == myUserId) callee else caller
    }

    fun isCaller(myUserId: String?): Boolean =
        !myUserId.isNullOrBlank() && caller?.id == myUserId
}

data class IceServerConfig(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

data class CallUiState(
    val status: CallUiStatus = CallUiStatus.Idle,
    val call: CallSession? = null,
    val partner: UserProfile? = null,
    val mediaMode: String = "audio",
    val cameraEnabled: Boolean = false,
    /** true = фронтальная; false = основная (rear). */
    val usingFrontCamera: Boolean = true,
    /** Есть и front, и back — можно переключать. */
    val canSwitchCamera: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    /** Инкремент при смене video track — Compose перепривязывает sink. */
    val videoEpoch: Int = 0,
    val muted: Boolean = false,
    val audioRoute: CallAudioRoute = CallAudioRoute.Earpiece,
    val bluetoothAvailable: Boolean = false,
    val elapsedSeconds: Int = 0,
    val error: String? = null,
) {
    val isVideo: Boolean get() = mediaMode == "video"

    val isInCall: Boolean
        get() = status == CallUiStatus.Outgoing ||
            status == CallUiStatus.Incoming ||
            status == CallUiStatus.Connecting ||
            status == CallUiStatus.Active

    val ringingChatId: String?
        get() = if (status == CallUiStatus.Incoming) call?.chatId else null
}

fun AppNotification.isPendingPrivateInvite(): Boolean =
    type == "private_invite" &&
        payload["resolved"].isNullOrBlank() &&
        !payload["session_id"].isNullOrBlank()

data class AiStyleProfile(
    val enabled: Boolean = true,
    val samplesCount: Int = 0,
)

data class AiCompleteResult(
    val suggestion: String = "",
    val disabled: Boolean = false,
    val rateLimited: Boolean = false,
    val error: Boolean = false,
    val detail: String? = null,
)
