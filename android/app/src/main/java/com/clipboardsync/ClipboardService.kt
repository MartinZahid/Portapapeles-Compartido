package com.clipboardsync

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class ClipboardService : AccessibilityService() {

    private var lastText = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            checkClipboard()
        }
    }

    private fun checkClipboard() {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text.isEmpty() || text == lastText) return
            lastText = text

            val config = ConfigRepository(this)
            if (!config.enabled || config.serverUrl.isBlank()) return

            Thread {
                val ok = ServerApi.sendClipboard(config.serverUrl, text)
                if (ok) {
                    runOnUiThread {
                        Toast.makeText(this, "Enviado a PC ✓", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } catch (_: Exception) {}
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mainExecutor.execute(action)
        } else {
            handler.post(action)
        }
    }

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
