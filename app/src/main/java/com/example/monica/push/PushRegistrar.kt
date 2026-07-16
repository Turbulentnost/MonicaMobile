package com.example.monica.push

import android.content.Context
import android.util.Log
import com.example.monica.data.MonicaApi
import com.example.monica.data.SessionStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

object PushRegistrar {
    private const val TAG = "PushRegistrar"
    private const val FCM_TIMEOUT_MS = 15_000L

    fun refreshTokenIfLoggedIn(context: Context) {
        val session = SessionStore(context)
        if (!session.isLoggedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                registerCurrentToken(context)
                Log.i(TAG, "FCM token registered")
            }.onFailure {
                Log.w(TAG, "FCM register failed: ${it.message}")
            }
        }
    }

    suspend fun registerCurrentToken(context: Context) {
        val session = SessionStore(context)
        val token = withTimeout(FCM_TIMEOUT_MS) {
            FirebaseMessaging.getInstance().token.await()
        }
        MonicaApi(session).registerDevice(token)
    }
}
