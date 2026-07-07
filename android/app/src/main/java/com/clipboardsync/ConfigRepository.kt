package com.clipboardsync

import android.content.Context

class ConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("clipboard_sync", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit().putString("server_url", value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var lastSeenClipboard: String
        get() = prefs.getString("last_seen", "") ?: ""
        set(value) = prefs.edit().putString("last_seen", value).apply()
}
