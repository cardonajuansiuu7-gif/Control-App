package com.appblock.overlay

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AppMonitorService : AccessibilityService() {

    companion object {
        private const val TAG = "AppMonitorService"

        // ⚠️ MODO PRUEBA: 20 segundos en vez de 10 minutos.
        // Cuando confirmes que funciona, cambiá esto de nuevo a:
        // private const val THRESHOLD_MILLIS = 10 * 60 * 1000L
        private const val THRESHOLD_MILLIS = 20 * 1000L

        private const val RESET_AWAY_MILLIS = 30 * 60 * 1000L
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
                saveMilestonesShown(newPackage, 0)
            }

            toast("Monitoreando $newPackage — acumulado: ${getAccumulated(newPackage) / 1000}s")
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
        if (MotivationOverlayHelper.isShowing()) return

        val now = System.currentTimeMillis()
        val currentSession = now - sessionStartTime
        val total = getAccumulated(packageName) + currentSession

        val milestonesReached = (total / THRESHOLD_MILLIS).toInt()
        val milestonesShown = getMilestonesShown(packageName)

        // Toast cada chequeo, para que veas que el service SÍ está vivo y contando.
        toast("Total: ${total / 1000}s / umbral: ${THRESHOLD_MILLIS / 1000}s")

        if (milestonesReached > milestonesShown) {
            saveMilestonesShown(packageName, milestonesReached)
            toast("🔔 Umbral alcanzado, mostrando overlay...")
            showMotivationOverlay(packageName)
        }
    }

    private fun showMotivationOverlay(packageName: String) {
        try {
            val phrase = MotivationalPhrasesManager.getRandomPhrase(this)
            MotivationOverlayHelper.show(
                service = this,
                phrase = phrase,
                onExit = { performGlobalAction(GLOBAL_ACTION_HOME) },
                onEmergency = {
                    Log.d(TAG, "Emergencia usada en $packageName, se sigue usando la app")
                }
            )
        } catch (e: Exception) {
            toast("❌ ERROR al mostrar overlay: ${e.message}")
            Log.e(TAG, "Error mostrando overlay", e)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    private fun getAccumulated(pkg: String): Long = prefs.getLong("accum_$pkg", 0L)
    private fun saveAccumulated(pkg: String, value: Long) { prefs.edit().putLong("accum_$pkg", value).apply() }
    private fun getLastExitTime(pkg: String): Long = prefs.getLong("exit_$pkg", 0L)
    private fun saveLastExitTime(pkg: String, value: Long) { prefs.edit().putLong("exit_$pkg", value).apply() }
    private fun getMilestonesShown(pkg: String): Int = prefs.getInt("milestones_$pkg", 0)
    private fun saveMilestonesShown(pkg: String, value: Int) { prefs.edit().putInt("milestones_$pkg", value).apply() }

    override fun onInterrupt() {
        stopPeriodicCheck()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio de accesibilidad conectado")
    }
}
