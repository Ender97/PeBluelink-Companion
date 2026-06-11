package com.accord.pebluelinkcompanion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.getpebble.android.kit.PebbleKit
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var repo: BluelinkRepository
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var regionSpinner: Spinner
    private lateinit var usernameField: EditText
    private lateinit var passwordField: EditText
    private lateinit var pinField:      EditText
    private lateinit var vinField:      EditText
    private lateinit var saveButton:    Button
    private lateinit var testButton:    Button
    private lateinit var statusText:    TextView
    private lateinit var logText:       TextView
    
    private var dataReceiver: PebbleDataReceiver? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startPebbleService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val ribbon: View = findViewById(R.id.tv_ribbon)
        ViewCompat.setOnApplyWindowInsetsListener(ribbon) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        repo = BluelinkRepository(applicationContext)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        
        ViewCompat.setOnApplyWindowInsetsListener(navView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        val menuButton: ImageButton = findViewById(R.id.btn_menu)
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            updateLogs()
        }

        logText = findViewById(R.id.tv_logs)
        findViewById<Button>(R.id.btn_clear_logs).setOnClickListener {
            AppLogger.clear()
            updateLogs()
        }

        regionSpinner = findViewById(R.id.spinner_region)
        usernameField = findViewById(R.id.et_username)
        passwordField = findViewById(R.id.et_password)
        pinField      = findViewById(R.id.et_pin)
        vinField      = findViewById(R.id.et_vin)
        saveButton    = findViewById(R.id.btn_save)
        testButton    = findViewById(R.id.btn_test)
        statusText    = findViewById(R.id.tv_status)

        val regions = BluelinkRegion.values()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, regions.map { it.label })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        regionSpinner.adapter = adapter
        regionSpinner.setSelection(regions.indexOf(repo.currentRegion))

        usernameField.setText(repo.username)
        vinField.setText(repo.vin)
        if (repo.password.isNotBlank()) passwordField.hint = "Password saved"
        if (repo.pin.isNotBlank())      pinField.hint      = "PIN saved"

        saveButton.setOnClickListener { saveCredentials() }
        testButton.setOnClickListener { testConnection()  }
        
        checkPermissionsAndStartService()
        
        lifecycleScope.launch {
            while(true) {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    updateLogs()
                }
                delay(2000)
            }
        }
    }

    private fun updateLogs() {
        logText.text = AppLogger.getLogs().ifEmpty { "No logs yet..." }
    }

    override fun onResume() {
        super.onResume()
        dataReceiver = buildDataReceiver(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(
                dataReceiver,
                IntentFilter("com.getpebble.action.app.RECEIVE"),
                Context.RECEIVER_EXPORTED
            )
        } else {
            PebbleKit.registerReceivedDataHandler(this, dataReceiver)
        }
    }

    override fun onPause() {
        super.onPause()
        dataReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        dataReceiver = null
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startPebbleService()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startPebbleService()
        }
    }

    private fun startPebbleService() {
        val intent = Intent(this, PebbleService::class.java)
        startForegroundService(intent)
    }

    private fun saveCredentials() {
        val selectedRegion = BluelinkRegion.values()[regionSpinner.selectedItemPosition]
        val username = usernameField.text.toString().trim()
        val password = passwordField.text.toString()
        val pin      = pinField.text.toString().trim()
        val vin      = vinField.text.toString().trim().uppercase()

        if (username.isBlank() || vin.isBlank()) {
            Toast.makeText(this, "Username and VIN are required", Toast.LENGTH_SHORT).show()
            return
        }
        if (pin.isBlank() && repo.pin.isBlank()) {
            Toast.makeText(this, "PIN is required", Toast.LENGTH_SHORT).show()
            return
        }

        repo.region   = selectedRegion.name
        repo.username = username
        repo.vin      = vin
        if (password.isNotBlank()) repo.password = password
        if (pin.isNotBlank())      repo.pin      = pin

        statusText.text = "Credentials saved."
        AppLogger.log("Credentials saved for region: ${selectedRegion.label}")
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        if (!repo.isConfigured) {
            statusText.text = "Save credentials first."
            return
        }
        statusText.text  = "Testing connection…"
        testButton.isEnabled = false
        saveButton.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repo.getStatus(forceUpdate = true) }
            testButton.isEnabled = true
            saveButton.isEnabled = true
            result
                .onSuccess { s ->
                    Toast.makeText(this@SettingsActivity, "Success! You may now close this app.", Toast.LENGTH_SHORT).show()
                    statusText.text = buildString {
                        appendLine("Connection OK!")
                        appendLine("Doors:    ${if (s.locked) "Locked" else "Unlocked"}")
                        appendLine("Range:    ${s.range} mi")
                        appendLine("SoC:      ${s.soc}%")
                        appendLine("Charging: ${if (s.charging) "Yes" else "No"}")
                        appendLine()
                        append(getString(R.string.success_message))
                    }
                }
                .onFailure { e -> 
                    Toast.makeText(this@SettingsActivity, "Failed - verify your login, VIN, and PIN are correct.", Toast.LENGTH_LONG).show()
                    statusText.text = "Error: ${e.message}" 
                }
        }
    }
}
