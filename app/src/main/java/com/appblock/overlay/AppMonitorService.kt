package com.appblock.overlay

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppMonitorService : AccessibilityService() {

    companion object {
        private const val TAG = "AppMonitorService"
        private const val THRESHOLD_MILLIS = 10 * 60 * 1000L // 10 minutos
        private const val RESET_AWAY_MILLIS = 30 * 60 * 1000L // 30 minutos
        private const val CHECK_INTERVAL_MILLIS = 5_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var sessionStartTime: Long = 0L
    private var periodicCheckRunnable: Runnable? = null

    private val prefs by lazy {
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPackage = event.packageName?.toString() ?: return
        if (newPackage == currentPackage) return

        val now = System.currentTimeMillis()
        val selectedApps = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()

        val previousPackage = currentPackage
        if (previousPackage != null && selectedApps.contains(previousPackage)) {
            val sessionDuration = now - sessionStartTime
            val accumulated = getAccumulated(previousPackage) + sessionDuration
            saveAccumulated(previousPackage, accumulated)
            saveLastExitTime(previousPackage, now)
            stopPeriodicCheck()
            Log.d(TAG, "Salió de $previousPackage. Acumulado: ${accumulated / 1000}s")
        }

        currentPackage = newPackage
        sessionStartTime = now

        if (selectedApps.contains(newPackage)) {
            val lastExit = getLastExitTime(newPackage)
            val awayTime = if (lastExit > 0) now - lastExit else Long.MAX_VALUE

            if (awayTime >= RESET_AWAY_MILLIS) {
                saveAccumulated(newPackage, 0L)
                saveNotified(newPackage, false)
                Log.d(TAG, "$newPackage: pasaron 30+ min afuera, contador reseteado")
            }

            Log.d(TAG, "Entró a $newPackage. Acumulado previo: ${getAccumulated(newPackage) / 1000}s")
            startPeriodicCheck(newPackage)
        }
    }

    private fun startPeriodicCheck(packageName: String) {
        val runnable = object : Runnable {
            override fun run() {
                checkThreshold(packageName)
                handler.postDelayed(this, CHECK_INTERVAL_MILLIS)
            }
        }
        periodicCheckRunnable = runnable
        handler.post(runnable)
    }

    private fun stopPeriodicCheck() {
        periodicCheckRunnable?.let { handler.removeCallbacks(it) }
        periodicCheckRunnable = null
    }

    private fun checkThreshold(packageName: String) {
        if (packageName != currentPackage) return
        val now = System.currentTimeMillis()
        val currentSession = now - sessionStartTime
        val total = getAccumulated(packageName) + currentSession

        if (total >= THRESHOLD_MILLIS && !getNotified(packageName)) {
            saveNotified(packageName, true)
            Log.d(TAG, "🔔 $packageName llegó a los 10 minutos acumulados — acá va el aviso")
        }
    }

    private fun getAccumulated(pkg: String): Long = prefs.getLong("accum_$pkg", 0L)
    private fun saveAccumulated(pkg: String, value: Long) { prefs.edit().putLong("accum_$pkg", value).apply() }
    private fun getLastExitTime(pkg: String): Long = prefs.getLong("exit_$pkg", 0L)
    private fun saveLastExitTime(pkg: String, value: Long) { prefs.edit().putLong("exit_$pkg", value).apply() }
    private fun getNotified(pkg: String): Boolean = prefs.getBoolean("notified_$pkg", false)
    private fun saveNotified(pkg: String, value: Boolean) { prefs.edit().putBoolean("notified_$pkg", value).apply() }

    override fun onInterrupt() {
        stopPeriodicCheck()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio de accesibilidad conectado")
    }
}
