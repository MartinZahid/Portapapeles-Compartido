package com.clipboardsync

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class ClipboardService : AccessibilityService() {

    private var lastText = ""
    private var config: ConfigRepository? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 2000L
    private var started = false
    private val notifId = 1001

    override fun onCreate() {
        super.onCreate()
        Log.i("ClipboardSync", "onCreate")
        config = ConfigRepository(this)
        createNotificationChannel()
        showNotification()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("ClipboardSync", "onServiceConnected")
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
        val t = clip.getItemAt(0).coerceToText(this).toString()
        if (t.isEmpty() || t == lastText) return
        lastText = t
        sendToPc(t)
    }

    private fun sendToPc(text: String) {
        Log.i("ClipboardSync", "Enviando: ${text.take(30)}")
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

    private fun showNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "clipboard_sync")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Clipboard Sync")
            .setContentText("Servicio activo")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }
}
