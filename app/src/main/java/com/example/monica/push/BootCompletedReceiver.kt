package com.example.monica.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.monica.data.SessionStore

/** После перезагрузки телефона снова поднимаем демон, если пользователь залогинен. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        if (!SessionStore(context).isLoggedIn) return
        MonicaDaemonService.start(context)
        PushRegistrar.refreshTokenIfLoggedIn(context)
    }
}
