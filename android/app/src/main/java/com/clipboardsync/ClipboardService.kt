package com.clipboardsync

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
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

    override fun onCreate() {
        super.onCreate()
        config = ConfigRepository(this)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val text = clip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isEmpty() || text == lastText) return@OnPrimaryClipChangedListener
        lastText = text
        sendToPc(text)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.removePrimaryClipChangedListener(clipListener)
    }

    fun sendToPc(text: String) {
        val cfg = config ?: return
        if (!cfg.enabled || cfg.serverUrl.isBlank()) return
        Thread {
            val ok = ServerApi.sendClipboard(cfg.serverUrl, text)
            if (ok) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Enviado a PC ✓", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
