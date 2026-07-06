package com.clipboardsync

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
                }
                openAccessibilityDirect()
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
                val ok = ServerApi.sendClipboard(url, "test-connection")
                runOnUiThread {
                    testBtn.isEnabled = true
                    testBtn.text = "Probar conexión"
                    if (ok) {
                        Toast.makeText(this, "Conexión exitosa ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error de conexión ✗", Toast.LENGTH_SHORT).show()
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
    }

    private val scanContract = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            urlInput.setText(result.contents)
            config.serverUrl = result.contents
            testConnection(result.contents)
        }
    }

    private fun testConnection(url: String) {
        Thread {
            val ok = ServerApi.sendClipboard(url, "test-connection")
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, "Conexión exitosa ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error de conexión ✗", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityEnabled()
    }

    private fun openAccessibilityDirect() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "Activá 'Clipboard Sync' en Accesibilidad", Toast.LENGTH_LONG).show()
    }

    private fun checkAccessibilityEnabled() {
        val enabled = isAccessibilityEnabled()
        enableSwitch.isChecked = enabled && config.serverUrl.isNotBlank()
        config.enabled = enabled && config.serverUrl.isNotBlank()
        updateStatus()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/.ClipboardService"
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabledServices?.contains(service) == true
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

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 1
    }
}
