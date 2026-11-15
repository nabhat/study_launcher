package com.gatefocus.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusGuardianService : AccessibilityService() {

    companion object {
        // Sensitive keywords that indicate an attempt to disable GateFocus, Device Admin, or Accessibility
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

        fun isSettingsPackage(pkg: String): Boolean {
            val lower = pkg.lowercase()
            return lower == "com.android.settings" ||
                   lower == "com.samsung.android.settings" ||
                   lower == "com.google.android.settings" ||
                   lower.startsWith("com.android.settings.") ||
                   lower.startsWith("com.samsung.android.settings.") ||
                   lower.startsWith("com.google.android.settings.") ||
                   lower.endsWith(".settings") ||
                   lower == "com.samsung.accessibility" ||
                   lower == "com.google.android.marvin.talkback"
        }

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

        // Always allow GateFocus itself
        if (foregroundPackage == packageName) return

        // 1. SETTINGS BLOCKER:
        // Strictly block Android Settings app, subpages, and device options during Focus Mode
        if (isSettingsPackage(foregroundPackage)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            BlockedAppActivity.start(this, "Android Settings", isTamperAttempt = true)
            return
        }

        // 2. TAMPER PROTECTION:
        // Detect if user is inside a package installer or system prompt attempting to disable GateFocus
        if (isTamperAttempt()) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            BlockedAppActivity.start(this, "Tamper Protection", isTamperAttempt = true)
            return
        }

        // 3. APP BLOCKER (Check against allowed whitelist):
        val allowedApps = KioskManager.getAllowedApps(this)
        if (!allowedApps.contains(foregroundPackage)) {
            // Kick user out immediately to Home screen
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
