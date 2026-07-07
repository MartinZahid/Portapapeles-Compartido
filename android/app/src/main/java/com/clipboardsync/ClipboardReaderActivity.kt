package com.clipboardsync

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class ClipboardReaderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("ClipboardSync", "ReaderActivity created")
    }

    override fun onResume() {
        super.onResume()
        Log.i("ClipboardSync", "ReaderActivity onResume")
        val cfg = ConfigRepository(this)
        Log.i("ClipboardSync", "cfg: enabled=${cfg.enabled} url=${cfg.serverUrl}")
        if (!cfg.enabled || cfg.serverUrl.isBlank()) { finish(); return }
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            Log.i("ClipboardSync", "primaryClip: ${clip?.let { "count=${it.itemCount} text=${it.getItemAt(0).coerceToText(this)}" } ?: "null"}")
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString()
                Log.i("ClipboardSync", "read text: '${text.take(30)}' lastSeen: '${cfg.lastSeenClipboard.take(30)}'")
                if (text.isNotEmpty() && text != cfg.lastSeenClipboard) {
                    cfg.lastSeenClipboard = text
                    Log.i("ClipboardSync", "Sending: ${text.take(30)}")
                    Thread { ServerApi.sendClipboard(cfg.serverUrl, text) }.start()
                }
            }
        } catch (e: Exception) {
            Log.e("ClipboardSync", "ReaderActivity error", e)
        }
        finish()
    }
}
