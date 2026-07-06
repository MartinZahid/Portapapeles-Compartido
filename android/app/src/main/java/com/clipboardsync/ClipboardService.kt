package com.clipboardsync

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class ClipboardService : AccessibilityService() {

    private var lastText = ""
    private var config: ConfigRepository? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 1500L

    override fun onCreate() {
        super.onCreate()
        config = ConfigRepository(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        checkClipboard()
    }

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkClipboard()
            handler.postDelayed(this, pollInterval)
        }
    }

    private fun checkClipboard() {
        try {
            val cfg = config ?: return
            if (!cfg.enabled || cfg.serverUrl.isBlank()) return
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text.isEmpty() || text == lastText) return
            lastText = text
            sendToPc(text)
        } catch (_: Exception) {}
    }

    private fun sendToPc(text: String) {
        val cfg = config ?: return
        Thread {
            val ok = ServerApi.sendClipboard(cfg.serverUrl, text)
            if (ok) {
                handler.post {
                    Toast.makeText(this, "Enviado a PC ✓", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
