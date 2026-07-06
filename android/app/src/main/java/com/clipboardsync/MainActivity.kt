package com.clipboardsync

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private lateinit var config: ConfigRepository
    private lateinit var urlInput: EditText
    private lateinit var enableSwitch: Switch
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = ConfigRepository(this)

        urlInput = findViewById(R.id.url_input)
        enableSwitch = findViewById(R.id.enable_switch)
        statusText = findViewById(R.id.status_text)
        val scanBtn = findViewById<Button>(R.id.scan_btn)
        val testBtn = findViewById<Button>(R.id.test_btn)
        val saveBtn = findViewById<Button>(R.id.save_btn)
        val syncBtn = findViewById<Button>(R.id.sync_btn)

        urlInput.setText(config.serverUrl)
        enableSwitch.isChecked = config.enabled
        updateStatus()

        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (config.serverUrl.isBlank()) {
                    Toast.makeText(this, "Primero configurá la URL del servidor", Toast.LENGTH_SHORT).show()
                    enableSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                requestNotificationAndOpenAccessibility()
            } else {
                config.enabled = false
                updateStatus()
            }
        }

        testBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "Ingresá la URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testBtn.isEnabled = false
            testBtn.text = "Probando..."
            Thread {
                val err = ServerApi.testConnection(url)
                runOnUiThread {
                    testBtn.isEnabled = true
                    testBtn.text = "Probar conexión"
                    if (err == null) {
                        Toast.makeText(this, "Conexión exitosa ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        saveBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "Ingresá la URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            config.serverUrl = url
            Toast.makeText(this, "URL guardada", Toast.LENGTH_SHORT).show()
        }

        scanBtn.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Escaneá el QR del servidor")
            options.setCameraId(0)
            options.setBeepEnabled(false)
            options.setBarcodeImageEnabled(false)
            scanContract.launch(options)
        }

        syncBtn.setOnClickListener {
            val url = config.serverUrl
            if (url.isBlank()) {
                Toast.makeText(this, "Configurá la URL primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = "Hola desde el S24! ${System.currentTimeMillis()}"
            syncBtn.isEnabled = false
            syncBtn.text = "Enviando..."
            Thread {
                val ok = ServerApi.sendClipboard(url, text)
                runOnUiThread {
                    syncBtn.isEnabled = true
                    syncBtn.text = "Enviar texto de prueba"
                    if (ok) {
                        Toast.makeText(this, "Enviado a PC ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al enviar", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        openAccessibilityDirect()
    }

    private fun requestNotificationAndOpenAccessibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAccessibilityDirect()
        }
    }

    private val scanContract = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            urlInput.setText(result.contents)
            config.serverUrl = result.contents
            testConnection(result.contents)
        }
    }

    private fun openAccessibilityDirect() {
        val serviceIntent = Intent(this, ClipboardService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        try {
            val comp = "$packageName/.ClipboardService"
            val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, comp)
            }
            startActivity(intent)
            Toast.makeText(this, "Activá el toggle de Clipboard Sync", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Buscá 'Clipboard Sync' en Accesibilidad", Toast.LENGTH_LONG).show()
        }
    }

    private fun testConnection(url: String) {
        Thread {
            val err = ServerApi.testConnection(url)
            runOnUiThread {
                if (err == null) {
                    Toast.makeText(this, "Conexión exitosa ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityEnabled()
    }

    private fun checkAccessibilityEnabled() {
        val enabled = isAccessibilityEnabled()
        enableSwitch.isChecked = enabled && config.serverUrl.isNotBlank()
        config.enabled = enabled && config.serverUrl.isNotBlank()
        updateStatus()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val cn = ComponentName(packageName, "${packageName}.ClipboardService")
        val shortForm = cn.flattenToShortString()
        val longForm = cn.flattenToString()
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(shortForm) || enabledServices.contains(longForm)
        } catch (_: Exception) {
            false
        }
    }

    private fun updateStatus() {
        val enabled = config.enabled
        val url = config.serverUrl
        if (!enabled) {
            statusText.text = "Servicio inactivo"
            statusText.setTextColor(0xff666666.toInt())
        } else {
            statusText.text = "Activo → $url"
            statusText.setTextColor(0xff00ff88.toInt())
        }
    }
}
