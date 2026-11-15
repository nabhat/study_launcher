package com.gatefocus.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusGuardianService : AccessibilityService() {

    companion object {
        // Sensitive keywords in Settings that indicate an attempt to disable GateFocus or uninstall it
        private val TAMPER_KEYWORDS = listOf(
            "gatefocus",
            "device admin",
            "admin apps",
            "force stop",
            "uninstall",
            "disable",
            "clear storage",
            "clear data",
            "remove device owner"
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
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val foregroundPackage = event.packageName?.toString() ?: return

        // If Focus Mode is not active, do nothing
        if (!KioskManager.isFocusLockActive(this)) return

        // 1. TAMPER PROTECTION (Inspired by Mindful):
        // Detect if user is inside Android Settings trying to disable Device Admin or Accessibility
        if (foregroundPackage == "com.android.settings") {
            if (isTamperAttempt()) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                BlockedAppActivity.start(this, "Settings Tamper Protection", isTamperAttempt = true)
                return
            }
            // Allow innocent settings like Wi-Fi or Bluetooth to proceed
            return
        }

        // 2. APP BLOCKER (Check against allowed whitelist):
        val allowedApps = KioskManager.getAllowedApps(this)
        if (!allowedApps.contains(foregroundPackage)) {
            // Kick user out immediately
            performGlobalAction(GLOBAL_ACTION_HOME)

            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(foregroundPackage, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                "Restricted App"
            }

            BlockedAppActivity.start(this, appLabel, isTamperAttempt = false)
        }
    }

    private fun isTamperAttempt(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return checkNodeForKeywords(rootNode)
    }

    private fun checkNodeForKeywords(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()

        for (keyword in TAMPER_KEYWORDS) {
            if (text != null && text.contains(keyword)) return true
            if (desc != null && desc.contains(keyword)) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (checkNodeForKeywords(child)) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {}
}
