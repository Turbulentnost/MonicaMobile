package com.example.monica.data

import android.content.Context
import com.example.monica.BuildConfig

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val apiBaseUrl: String
        get() = BuildConfig.API_BASE_URL.trimEnd('/')

    val wsBaseUrl: String
        get() = apiBaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var nickname: String?
        get() = prefs.getString(KEY_NICKNAME, null)
        set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun saveLogin(
        access: String,
        refresh: String,
        userId: String,
        nickname: String,
    ) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NICKNAME, nickname)
            .apply()
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_USER_ID)
            .remove(KEY_NICKNAME)
            .apply()
    }

    companion object {
        private const val PREFS = "monica_session"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_DARK_THEME = "dark_theme"
    }
}
