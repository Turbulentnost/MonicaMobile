package com.example.monica.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Нужен как ComponentName для [android.media.session.MediaSessionManager.getActiveSessions].
 * Само содержимое уведомлений не обрабатываем.
 */
class MonicaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    override fun onListenerConnected() {
        super.onListenerConnected()
        ActiveMediaSessionRepository.get(applicationContext).onListenerConnected()
    }

    override fun onListenerDisconnected() {
        ActiveMediaSessionRepository.get(applicationContext).onListenerDisconnected()
        super.onListenerDisconnected()
    }
}
