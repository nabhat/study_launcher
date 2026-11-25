package com.gatefocus.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusGuardianService : AccessibilityService() {

    private var lastBlockedPkg = ""
    private var lastBlockTime = 0L

    companion object {
        // Sensitive keywords that indicate an attempt to disable GateFocus, Device Admin, Accessibility, or enter Safe Mode
        private val TAMPER_KEYWORDS = listOf(
            "gatefocus",
            "device admin",
            "admin apps",
            "force stop",
            "uninstall",
            "disable",
            "clear storage",
            "clear data",
            "remove device owner",
            "safe mode",
            "safe boot",
            "restart in safe mode"
        )

        @Volatile
        private var bluetoothPromptGraceUntilMs: Long = 0L

        fun allowTransientBluetoothPrompt(durationMs: Long = 12_000L) {
            bluetoothPromptGraceUntilMs = System.currentTimeMillis() + durationMs
        }

        fun clearTransientBluetoothPrompt() {
            bluetoothPromptGraceUntilMs = 0L
        }

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

        // Only evaluate on window transitions to prevent high-frequency loop glitching
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val foregroundPackage = event.packageName?.toString() ?: return

        // If Focus Mode is not active, do nothing
        if (!KioskManager.isFocusLockActive(this)) return

        // Always allow GateFocus itself
        if (foregroundPackage == packageName) return

        // Allow system Bluetooth enable / pairing consent dialogs triggered explicitly from our in-app Bluetooth manager
        val className = event.className?.toString() ?: ""
        val isTransientBtPrompt = System.currentTimeMillis() <= bluetoothPromptGraceUntilMs &&
            (foregroundPackage == "com.android.bluetooth" || isSettingsPackage(foregroundPackage)) &&
            !className.contains("SubSettings", ignoreCase = true) &&
            !className.endsWith(".Settings", ignoreCase = true)

        if (isTransientBtPrompt && !isTamperAttempt()) {
            return
        }

        // Handle Home Launcher apps:
        // If a 3rd party launcher (e.g. Samsung One UI) opens while Focus Lock is active:
        // Do NOT call GLOBAL_ACTION_HOME (which would create an infinite re-launch loop).
        // Instead, smoothly redirect directly to GateFocus MainActivity.
        if (isHomeLauncherPackage(foregroundPackage)) {
            val now = System.currentTimeMillis()
            if (now - lastBlockTime > 1000L || lastBlockedPkg != foregroundPackage) {
                lastBlockTime = now
                lastBlockedPkg = foregroundPackage
                val gateIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(gateIntent)
            }
            return
        }

        // 1. SETTINGS BLOCKER:
        // Strictly block Android Settings app, subpages, and device options during Focus Mode
        if (isSettingsPackage(foregroundPackage)) {
            val now = System.currentTimeMillis()
            if (now - lastBlockTime > 800L || lastBlockedPkg != foregroundPackage) {
                lastBlockTime = now
                lastBlockedPkg = foregroundPackage
                BlockedAppActivity.start(this, "Android Settings", isTamperAttempt = true)
            }
            return
        }

        // 2. TAMPER PROTECTION:
        // Detect if user is inside a package installer or system prompt attempting to disable GateFocus
        if (isTamperAttempt()) {
            val now = System.currentTimeMillis()
            if (now - lastBlockTime > 800L || lastBlockedPkg != foregroundPackage) {
                lastBlockTime = now
                lastBlockedPkg = foregroundPackage
                BlockedAppActivity.start(this, "Tamper Protection", isTamperAttempt = true)
            }
            return
        }

        // 3. APP BLOCKER (Check against allowed whitelist):
        val allowedApps = KioskManager.getAllowedApps(this)
        if (!allowedApps.contains(foregroundPackage)) {
            val now = System.currentTimeMillis()
            if (now - lastBlockTime > 800L || lastBlockedPkg != foregroundPackage) {
                lastBlockTime = now
                lastBlockedPkg = foregroundPackage

                val appLabel = try {
                    val appInfo = packageManager.getApplicationInfo(foregroundPackage, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    "Restricted App"
                }

                BlockedAppActivity.start(this, appLabel, isTamperAttempt = false)
            }
        }
    }

    private fun isHomeLauncherPackage(pkg: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val homeApps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            homeApps.any { it.activityInfo.packageName == pkg }
        } catch (e: Exception) {
            false
        }
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

    private fun isTamperAttempt(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return checkNodeForKeywords(rootNode)
    }

    override fun onInterrupt() {}
}
