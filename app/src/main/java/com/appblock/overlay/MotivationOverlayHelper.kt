package com.appblock.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

object MotivationOverlayHelper {

    private var overlayView: View? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(
        service: AccessibilityService,
        phrase: String,
        onExit: () -> Unit,
        onEmergency: () -> Unit
    ) {
        if (overlayView != null) return

        val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(service)
        val view = inflater.inflate(R.layout.overlay_motivation, null)

        view.findViewById<TextView>(R.id.tvPhrase).text = phrase

        val btnExit = view.findViewById<Button>(R.id.btnExitApp)
        val btnEmergency = view.findViewById<Button>(R.id.btnEmergency)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        btnExit.setOnClickListener {
            remove(windowManager)
            onExit()
        }

        btnEmergency.setOnClickListener {
            remove(windowManager)
            onEmergency()
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun remove(windowManager: WindowManager) {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
        }
        overlayView = null
    }
}
