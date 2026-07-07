package com.clipboardsync

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class ClipboardService : AccessibilityService() {

    private var lastText = ""
    private var config: ConfigRepository? = null
    private val handler = Handler(Looper.getMainLooper())
    private val notifId = 1001
    private var overlayView: View? = null

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
        createOverlay()
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
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

    private fun createOverlay() {
        if (overlayView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w("ClipboardSync", "SYSTEM_ALERT_WINDOW not granted — overlay skipped")
            return
        }
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                1, 1, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.START or Gravity.TOP
                x = 0; y = 0
            }

            val view = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
            wm.addView(view, params)
            overlayView = view
            Log.i("ClipboardSync", "Overlay 1x1 created (focusable, not touchable)")
        } catch (e: Exception) {
            Log.e("ClipboardSync", "Overlay creation failed", e)
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {}
            overlayView = null
            Log.i("ClipboardSync", "Overlay removed")
        }
    }

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                try { checkClipboard() } catch (_: Exception) {}
                handler.postDelayed(this, 3000)
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
                Log.i("ClipboardSync", "Read: ${t.take(30)}")
                Thread { ServerApi.sendClipboard(cfg.serverUrl, t) }.start()
            }
        } else {
            val overlayActive = overlayView != null
            Log.i("ClipboardSync", "primaryClip=null (overlayActive=$overlayActive)")
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
            .setContentText("Overlay activo · $notifId")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }
}
