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

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (KioskManager.isFocusLockActive(context)) {
            // Immediate Tamper Defense (Inspired by Mindful):
            // Lock screen immediately to prevent user from completing deactivation!
            try {
                dpm.lockNow()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val remainingMins = (KioskManager.getRemainingTimeMillis(context) / 60000) + 1
            return "Tamper Protection: Focus Timer is currently active ($remainingMins min remaining). Please wait until your focus session ends."
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
    private const val KEY_FOCUS_END_TIME = "key_focus_end_time"
    private const val KEY_SELECTED_DURATION = "key_selected_duration"
    private const val KEY_ALLOWED_APPS = "key_allowed_apps"
    private const val DEFAULT_DURATION_MINUTES = 45

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

    fun getSelectedDuration(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED_DURATION, DEFAULT_DURATION_MINUTES)
    }

    fun setSelectedDuration(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_SELECTED_DURATION, minutes).apply()
    }

    fun getFocusEndTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_FOCUS_END_TIME, 0L)
    }

    fun getRemainingTimeMillis(context: Context): Long {
        val endTime = getFocusEndTime(context)
        val now = System.currentTimeMillis()
        return if (endTime > now) endTime - now else 0L
    }

    fun isFocusLockActive(context: Context): Boolean {
        return getRemainingTimeMillis(context) > 0L
    }

    fun startFocusSession(activity: Activity, durationMinutes: Int) {
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        getPrefs(activity).edit()
            .putLong(KEY_FOCUS_END_TIME, endTime)
            .putInt(KEY_SELECTED_DURATION, durationMinutes)
            .apply()

        applyDeviceOwnerPolicies(activity)
        try {
            activity.startLockTask()
            Toast.makeText(activity, "Focus Lock Active for $durationMinutes min. Settings & distractions blocked.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Lock task started: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopKioskMode(activity: Activity) {
        try {
            getPrefs(activity).edit().putLong(KEY_FOCUS_END_TIME, 0L).apply()
            activity.stopLockTask()

            if (isDeviceOwner(activity)) {
                val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(activity, DeviceAdmin::class.java)
                try {
                    dpm.setStatusBarDisabled(admin, false)
                    // Keep baseline OS protections active (uninstall block, factory reset block, safe boot block)
                    applyDeviceOwnerPolicies(activity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Toast.makeText(activity, "Focus Lock Deactivated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to stop lock: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun getAllowedApps(context: Context): Set<String> {
        val custom = getPrefs(context).getStringSet(KEY_ALLOWED_APPS, null)
        val base = (custom ?: DEFAULT_ALLOWED_PACKAGES).toSet()
        // Ensure Settings can never be in the allowed apps whitelist
        return base.filterNot { FocusGuardianService.isSettingsPackage(it) }.toSet()
    }

    fun setAllowedApps(context: Context, apps: Set<String>): Boolean {
        if (isFocusLockActive(context)) {
            // Whitelist cannot be edited once the focus lock is active
            return false
        }
        val combined = apps.toMutableSet()
        combined.addAll(DEFAULT_ALLOWED_PACKAGES)
        combined.removeAll { FocusGuardianService.isSettingsPackage(it) }
        getPrefs(context).edit().putStringSet(KEY_ALLOWED_APPS, combined).apply()
        return true
    }

    fun addAllowedApp(context: Context, packageName: String) {
        if (FocusGuardianService.isSettingsPackage(packageName)) return
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

            // Block uninstallation of GateFocus itself permanently (OS package lock),
            // while allowing user to uninstall other third-party apps when Focus is idle
            dpm.setUninstallBlocked(admin, context.packageName, true)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)

            // Permanently block Safe Mode reboot & Factory Reset
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)

            // Lock App Info controls and status bar only during active Focus Sessions
            if (isFocusLockActive(context)) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                dpm.setStatusBarDisabled(admin, true)
            } else {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                dpm.setStatusBarDisabled(admin, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeAllRestrictionsAndDeviceOwner(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdmin::class.java)

        try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            dpm.setUninstallBlocked(admin, context.packageName, false)
            dpm.setLockTaskPackages(admin, arrayOf())
            dpm.setStatusBarDisabled(admin, false)
            android.util.Log.i("DeviceAdmin", "Successfully cleared all device restrictions")
        } catch (e: Exception) {
            android.util.Log.e("DeviceAdmin", "Failed to clear user restrictions", e)
        }

        try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.clearDeviceOwnerApp(context.packageName)
                android.util.Log.i("DeviceAdmin", "Successfully cleared Device Owner app")
            }
        } catch (e: Exception) {
            android.util.Log.e("DeviceAdmin", "Failed to clear device owner app", e)
        }
    }
}
