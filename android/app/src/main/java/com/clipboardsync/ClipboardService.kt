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
    private val notifId = 1001
    private var lastActivityStart = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i("ClipboardSync", "onCreate")
        config = ConfigRepository(this)
        createNotificationChannel()
        showNotification()
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.addPrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("ClipboardSync", "onServiceConnected")
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        Log.i("ClipboardSync", "AccessibilityEvent: type=0x${event.eventType.toString(16)} pkg=${event.packageName}")
        if (event.eventType and 0x10000 != 0) {
            Log.i("ClipboardSync", "TYPE_CLIPBOARD_CHANGED event")
            checkClipboard()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {}
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.i("ClipboardSync", "OnPrimaryClipChangedListener fired")
        if (config?.enabled == true) checkClipboard()
    }

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                try { checkClipboard() } catch (_: Exception) {}
                handler.postDelayed(this, 5000)
            }
        })
    }

    private fun stopPolling() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun checkClipboard() {
        val cfg = config ?: return
        if (!cfg.enabled || cfg.serverUrl.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val t = clip.getItemAt(0).coerceToText(this).toString()
            if (t.isNotEmpty() && t != lastText) {
                lastText = t
                Log.i("ClipboardSync", "Direct read: ${t.take(30)}")
                Thread { ServerApi.sendClipboard(cfg.serverUrl, t) }.start()
            }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastActivityStart > 30000) {
                readClipboardViaActivity()
            }
        }
    }

    private fun readClipboardViaActivity() {
        lastActivityStart = System.currentTimeMillis()
        Log.i("ClipboardSync", "Starting reader activity fallback")
        try {
            startActivity(
                Intent(this, ClipboardReaderActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.e("ClipboardSync", "Failed to start reader activity", e)
        }
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
