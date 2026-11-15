package com.gatefocus.launcher

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast

class DeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "GateFocus Device Admin Activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Warning: Disabling Device Admin will remove GATE Focus Mode restrictions."
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
    private const val DEFAULT_PIN = "2026" // Default GATE target year PIN

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
            // Whitelist GateFocus for seamless LockTask Mode
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            // Block uninstallation
            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_UNINSTALL_APPS)
            // Block safe mode bypass
            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
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
