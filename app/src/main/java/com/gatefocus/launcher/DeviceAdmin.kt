package com.gatefocus.launcher

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.UserManager
import android.widget.Toast

class DeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "GateFocus Device Admin Activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (KioskManager.isFocusLockActive(context)) {
            // Immediate Tamper Defense (Inspired by Mindful):
            // Lock screen immediately to prevent user from completing deactivation!
            try {
                dpm.lockNow()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "Tamper Protection: Focus Mode is active. Enter Master PIN inside GateFocus before deactivating Device Admin."
        }
        return super.onDisableRequested(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "GateFocus Device Admin Deactivated", Toast.LENGTH_SHORT).show()
    }
}

object KioskManager {
    private const val PREFS_NAME = "gate_focus_prefs"
    private const val KEY_PIN = "master_pin"
    private const val KEY_FOCUS_LOCK = "key_focus_lock_active"
    private const val KEY_ALLOWED_APPS = "key_allowed_apps"
    private const val DEFAULT_PIN = "2026" // Default GATE target year PIN

    private val DEFAULT_ALLOWED_PACKAGES = setOf(
        "com.gatefocus.launcher",
        "com.samsung.android.honeyboard",       // Samsung Keyboard
        "com.google.android.inputmethod.latin",  // Gboard
        "android",                               // Android Framework
        "com.android.systemui",                  // System UI Elements
        "com.samsung.android.app.notes",        // Samsung Notes
        "org.videolan.vlc",                      // VLC Media Player
        "com.sec.android.app.popupcalculator",  // Samsung Calculator
        "com.google.android.calculator"         // Google Calculator
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPin(context: Context): String {
        return getPrefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun setPin(context: Context, newPin: String) {
        getPrefs(context).edit().putString(KEY_PIN, newPin).apply()
    }

    fun isFocusLockActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FOCUS_LOCK, true)
    }

    fun setFocusLockActive(context: Context, active: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOCUS_LOCK, active).apply()
    }

    fun getAllowedApps(context: Context): Set<String> {
        val custom = getPrefs(context).getStringSet(KEY_ALLOWED_APPS, null)
        return (custom ?: DEFAULT_ALLOWED_PACKAGES).toSet()
    }

    fun setAllowedApps(context: Context, apps: Set<String>) {
        val combined = apps.toMutableSet()
        combined.addAll(DEFAULT_ALLOWED_PACKAGES)
        getPrefs(context).edit().putStringSet(KEY_ALLOWED_APPS, combined).apply()
    }

    fun addAllowedApp(context: Context, packageName: String) {
        val current = getAllowedApps(context).toMutableSet()
        current.add(packageName)
        setAllowedApps(context, current)
    }

    fun removeAllowedApp(context: Context, packageName: String) {
        val current = getAllowedApps(context).toMutableSet()
        current.remove(packageName)
        setAllowedApps(context, current)
    }

    fun isDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdmin::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun requestDeviceAdmin(activity: Activity) {
        val adminComponent = ComponentName(activity, DeviceAdmin::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "GateFocus requires Device Admin to enforce Focus Mode and prevent uninstallation during study periods."
            )
        }
        activity.startActivity(intent)
    }

    fun applyDeviceOwnerPolicies(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdmin::class.java)

        try {
            // Whitelist GateFocus and allowed apps for LockTask Mode
            val whitelist = getAllowedApps(context).toTypedArray()
            dpm.setLockTaskPackages(admin, whitelist)

            // Block uninstallation
            dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            // Block safe mode bypass
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startKioskMode(activity: Activity) {
        try {
            setFocusLockActive(activity, true)
            applyDeviceOwnerPolicies(activity)
            activity.startLockTask()
            Toast.makeText(activity, "Focus Lock Active. Distractions blocked.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Lock task failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopKioskMode(activity: Activity) {
        try {
            setFocusLockActive(activity, false)
            activity.stopLockTask()
            Toast.makeText(activity, "Focus Lock Deactivated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to stop lock: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
