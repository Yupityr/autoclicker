package com.example.autoclicker

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayStatus = findViewById(R.id.stepOverlayStatus)
        accessibilityStatus = findViewById(R.id.stepAccessibilityStatus)
        btnStart = findViewById(R.id.btnStartOverlay)

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnEnableAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101
                )
            }
            val intent = Intent(this, OverlayService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        overlayStatus.text = "1. Overlay permission: " + if (overlayGranted) "granted" else "not granted"

        val accessibilityEnabled = isAccessibilityServiceEnabled()
        accessibilityStatus.text = "2. Accessibility service: " + if (accessibilityEnabled) "enabled" else "not enabled"

        btnStart.isEnabled = overlayGranted && accessibilityEnabled
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${ClickAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }
}
