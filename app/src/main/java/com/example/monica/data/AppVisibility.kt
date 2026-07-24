package com.example.monica.data

/**
 * Процесс на переднем плане / открытый чат — для presence и подавления пушей.
 */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
        private set

    @Volatile
    var openChatId: String? = null
        private set

    fun setForeground(value: Boolean) {
        isForeground = value
    }

    fun setOpenChatId(chatId: String?) {
        openChatId = chatId?.takeIf { it.isNotBlank() }
    }
}
