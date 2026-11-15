package com.gatefocus.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FocusGuardianService : AccessibilityService() {

    companion object {
        private val allowedSystemPackages = setOf(
            "com.gatefocus.launcher",
            "com.samsung.android.honeyboard",      // Samsung Keyboard
            "com.google.android.inputmethod.latin", // Gboard
            "android",                              // System Framework Dialogs
            "com.android.systemui"                  // System UI Elements
        )

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${FocusGuardianService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(expectedServiceName)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val foregroundPackage = event.packageName?.toString() ?: return

        // Check if Focus Mode / Lockdown is currently active
        if (KioskManager.isFocusLockActive(this)) {
            // Block all unapproved apps (File Manager, Chrome, Samsung Internet, Webnovel readers, Social media)
            if (!allowedSystemPackages.contains(foregroundPackage)) {
                // Instantly force user back to GateFocus study launcher
                val returnIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(returnIntent)
                Toast.makeText(this, "🚫 Blocked! Focus Mode is active. Get back to GATE prep.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInterrupt() {}
}
