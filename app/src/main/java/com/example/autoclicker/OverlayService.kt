package com.example.autoclicker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var targetView: View
    private lateinit var panelView: View
    private lateinit var targetParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var isClicking = false
    private var endTimeMillis: Long = -1L // -1 = no end (forever)

    private val intervalUnits = arrayOf("ms", "sec")
    private val durationUnits = arrayOf("minutes", "hours", "forever")

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!isClicking) return

            if (endTimeMillis > 0 && System.currentTimeMillis() >= endTimeMillis) {
                stopClicking()
                return
            }

            val tx = targetParams.x + (targetView.width / 2f)
            val ty = targetParams.y + (targetView.height / 2f)
            ClickAccessibilityService.instance?.performTap(tx, ty)

            handler.postDelayed(this, currentIntervalMs())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupTargetView()
        setupPanelView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        if (::targetView.isInitialized) runCatching { windowManager.removeView(targetView) }
        if (::panelView.isInitialized) runCatching { windowManager.removeView(panelView) }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---------- Draggable click-target circle ----------

    private fun setupTargetView() {
        targetView = LayoutInflater.from(this).inflate(R.layout.overlay_target, null)

        targetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 600
        }

        windowManager.addView(targetView, targetParams)
        makeDraggable(targetView, targetParams) { /* no tap action needed */ }
    }

    // ---------- Floating control bubble + settings panel ----------

    private fun setupPanelView() {
        panelView = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        windowManager.addView(panelView, panelParams)

        val bubble = panelView.findViewById<View>(R.id.bubble)
        val settingsPanel = panelView.findViewById<View>(R.id.settingsPanel)
        val spinnerIntervalUnit = panelView.findViewById<Spinner>(R.id.spinnerIntervalUnit)
        val spinnerDurationUnit = panelView.findViewById<Spinner>(R.id.spinnerDurationUnit)
        val editInterval = panelView.findViewById<EditText>(R.id.editInterval)
        val editDuration = panelView.findViewById<EditText>(R.id.editDuration)
        val statusText = panelView.findViewById<TextView>(R.id.statusText)
        val btnStart = panelView.findViewById<Button>(R.id.btnStart)
        val btnStop = panelView.findViewById<Button>(R.id.btnStop)
        val btnClose = panelView.findViewById<Button>(R.id.btnClose)

        spinnerIntervalUnit.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, intervalUnits
        )
        spinnerDurationUnit.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, durationUnits
        )

        makeDraggable(bubble, panelParams, viewToMove = panelView) {
            settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnStart.setOnClickListener {
            if (ClickAccessibilityService.instance == null) {
                statusText.text = "Status: enable the Accessibility Service first"
                return@setOnClickListener
            }
            val intervalValue = editInterval.text.toString().toLongOrNull() ?: 100L
            intervalMs = when (spinnerIntervalUnit.selectedItem as String) {
                "sec" -> intervalValue * 1000L
                else -> intervalValue
            }
            if (intervalMs < 10L) intervalMs = 10L

            val durationValue = editDuration.text.toString().toLongOrNull() ?: 1L
            endTimeMillis = when (spinnerDurationUnit.selectedItem as String) {
                "forever" -> -1L
                "hours" -> System.currentTimeMillis() + durationValue * 3_600_000L
                else -> System.currentTimeMillis() + durationValue * 60_000L
            }

            startClicking()
            statusText.text = "Status: running"
        }

        btnStop.setOnClickListener {
            stopClicking()
            statusText.text = "Status: idle"
        }

        btnClose.setOnClickListener {
            stopSelf()
        }
    }

    private var intervalMs: Long = 100L
    private fun currentIntervalMs(): Long = intervalMs

    private fun startClicking() {
        if (isClicking) return
        isClicking = true
        handler.post(clickRunnable)
    }

    private fun stopClicking() {
        isClicking = false
        handler.removeCallbacks(clickRunnable)
    }

    /**
     * Makes [handleView] draggable by updating [params] on the window it belongs to.
     * [viewToMove] defaults to [handleView] itself; pass a different view when the
     * drag handle (e.g. the bubble) is a child of the view that should actually move.
     * [onTap] fires on a simple tap (no meaningful drag distance).
     */
    private fun makeDraggable(
        handleView: View,
        params: WindowManager.LayoutParams,
        viewToMove: View = handleView,
        onTap: () -> Unit
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        handleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX)
                    val dy = (event.rawY - initialTouchY)
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(viewToMove, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Foreground service notification (required while overlay is active) ----------

    private fun startForegroundNotification() {
        val channelId = "autoclicker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "AutoClicker", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoClicker is active")
            .setContentText("Tap the floating bubble to control it")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()

        startForeground(1, notification)
    }
}
