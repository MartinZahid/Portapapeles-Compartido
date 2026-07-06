package com.clipboardsync

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class ClipboardService : AccessibilityService() {

    private var lastText = ""
    private var config: ConfigRepository? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 1500L
    private var started = false

    override fun onCreate() {
        super.onCreate()
        config = ConfigRepository(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!started) {
            started = true
            handler.post(pollRunnable)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        started = false
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            try { checkClipboard() } catch (_: Exception) {}
            handler.postDelayed(this, pollInterval)
        }
    }

    private fun checkClipboard() {
        val cfg = config ?: return
        if (!cfg.enabled || cfg.serverUrl.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this).toString()
        if (text.isEmpty() || text == lastText) return
        lastText = text
        sendToPc(text)
    }

    private fun sendToPc(text: String) {
        val cfg = config ?: return
        Thread {
            ServerApi.sendClipboard(cfg.serverUrl, text)
        }.start()
    }
}
