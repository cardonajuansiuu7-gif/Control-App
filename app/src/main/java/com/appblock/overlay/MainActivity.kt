package com.appblock.overlay

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "app_block_prefs"
        const val KEY_SELECTED_APPS = "selected_apps"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var apps: MutableList<AppInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewApps)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val selectedPackages = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()

        apps = loadInstalledApps(selectedPackages).toMutableList()

        val adapter = AppListAdapter(apps) { _, _ ->
            saveSelection(prefs)
        }
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnAccessibilityPermission).setOnClickListener {
            openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSelection(prefs)
            Toast.makeText(this, "Selección guardada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInstalledApps(selectedPackages: Set<String>): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        return resolveInfos
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo ->
                val appInfo: ApplicationInfo = resolveInfo.activityInfo.applicationInfo
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    isSelected = selectedPackages.contains(appInfo.packageName)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    private fun saveSelection(prefs: android.content.SharedPreferences) {
        val selected = apps.filter { it.isSelected }.map { it.packageName }.toSet()
        prefs.edit().putStringSet(KEY_SELECTED_APPS, selected).apply()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Permiso de overlay ya concedido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(
            this,
            "Buscá 'AppBlock' en la lista y activalo",
            Toast.LENGTH_LONG
        ).show()
    }
}
