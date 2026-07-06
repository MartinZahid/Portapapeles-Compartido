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
import android.widget.Toast
import androidx.core.app.NotificationCompat

class ClipboardService : AccessibilityService() {

    private var lastText = ""
    private var config: ConfigRepository? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 1500L
    private var notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        config = ConfigRepository(this)
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        checkClipboard()
    }

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(pollRunnable)
        showNotification("Servicio iniciado")
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
            val clip = cm.primaryClip
            if (clip == null) {
                showNotification("Clipboard: null")
                return
            }
            if (clip.itemCount == 0) {
                showNotification("Clipboard: vacío")
                return
            }
            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isEmpty()) {
                showNotification("Clipboard: texto vacío")
                return
            }
            if (text == lastText) return
            lastText = text
            showNotification("Enviando: \"${text.take(30)}...\"")
            sendToPc(text)
        } catch (e: Exception) {
            showNotification("Error: ${e.message?.take(40)}")
        }
    }

    private fun sendToPc(text: String) {
        val cfg = config ?: return
        Thread {
            val ok = ServerApi.sendClipboard(cfg.serverUrl, text)
            if (ok) {
                handler.post {
                    Toast.makeText(this, "Enviado a PC ✓", Toast.LENGTH_SHORT).show()
                    showNotification("✓ Enviado: \"${text.take(30)}...\"")
                }
            } else {
                handler.post {
                    showNotification("✗ Error al enviar")
                }
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "clipboard_sync",
                "Clipboard Sync",
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
