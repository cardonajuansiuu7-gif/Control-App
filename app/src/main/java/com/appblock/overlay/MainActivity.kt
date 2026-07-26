package com.appblock.overlay

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAccessibilityPermission: Button

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

        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)

        btnOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }

        btnAccessibilityPermission.setOnClickListener {
            openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSelection(prefs)
            Toast.makeText(this, "Selección guardada", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnManagePhrases).setOnClickListener {
            showManagePhrasesDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionButtons()
    }

    private fun refreshPermissionButtons() {
        val overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(this)
        if (overlayGranted) {
            btnOverlayPermission.text = "Permiso de overlay concedido ✓"
            btnOverlayPermission.isEnabled = false
        } else {
            btnOverlayPermission.text = "Activar permiso de overlay"
            btnOverlayPermission.isEnabled = true
        }

        if (isAccessibilityServiceEnabled()) {
            btnAccessibilityPermission.text = "Servicio de accesibilidad activado ✓"
            btnAccessibilityPermission.isEnabled = false
        } else {
            btnAccessibilityPermission.text = "Activar servicio de accesibilidad"
            btnAccessibilityPermission.isEnabled = true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, AppMonitorService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
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
        Toast.makeText(this, "Buscá 'AppBlock' en la lista y activalo", Toast.LENGTH_LONG).show()
    }

    private fun showManagePhrasesDialog() {
        val phrases = MotivationalPhrasesManager.getCustomPhrases(this).toMutableList()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_phrases, null)
        val listView = dialogView.findViewById<ListView>(R.id.lvPhrases)
        val editText = dialogView.findViewById<EditText>(R.id.etNewPhrase)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAddPhrase)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, phrases)
        listView.adapter = adapter

        btnAdd.setOnClickListener {
            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                MotivationalPhrasesManager.addPhrase(this, text)
                phrases.add(text)
                adapter.notifyDataSetChanged()
                editText.text.clear()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val phrase = phrases[position]
            MotivationalPhrasesManager.removePhrase(this, phrase)
            phrases.removeAt(position)
            adapter.notifyDataSetChanged()
            true
        }

        AlertDialog.Builder(this)
            .setTitle("Mis frases motivadoras")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}
