package com.clipboardsync

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class ClipboardService : AccessibilityService() {

    private var lastText = ""
    private var config: ConfigRepository? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 1500L
    private var started = false
    private var notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        config = ConfigRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()
        try { showNotification("Servicio activo") } catch (_: Exception) {}
        if (!started) {
            started = true
            handler.post(pollRunnable)
        }
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        try { showNotification("Servicio activo") } catch (_: Exception) {}
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "clipboard_sync", "Clipboard Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(text: String) {
        val n = NotificationCompat.Builder(this, "clipboard_sync")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Clipboard Sync")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(notificationId, n)
    }
}
