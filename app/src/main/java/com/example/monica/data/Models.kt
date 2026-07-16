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
)

data class ChatSummary(
    val id: String,
    val partner: UserProfile?,
    val lastMessage: MessageItem?,
    val updatedAt: String? = null,
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
    val sentAt: String,
    val readAt: String? = null,
    /** Корреляция optimistic → server ack */
    val clientId: String? = null,
    /** `sending` пока ждём `message.new` */
    val clientStatus: String? = null,
) {
    val isPending: Boolean
        get() = clientStatus == "sending" || id.startsWith("temp-")

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

fun AppNotification.isPendingPrivateInvite(): Boolean =
    type == "private_invite" &&
        payload["resolved"].isNullOrBlank() &&
        !payload["session_id"].isNullOrBlank()
