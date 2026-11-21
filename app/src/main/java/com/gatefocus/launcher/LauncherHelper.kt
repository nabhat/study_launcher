package com.gatefocus.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast

object LauncherHelper {

    fun getAlternateLaunchers(context: Context): List<String> {
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfos
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
    }

    fun isGateFocusDefaultHome(context: Context): Boolean {
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val defaultHome = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return defaultHome?.activityInfo?.packageName == context.packageName
    }

    fun launchSystemHome(context: Context): Boolean {
        val pm = context.packageManager
        val alternateLaunchers = getAlternateLaunchers(context)

        // Prefer Samsung One UI Home if installed, else first available third-party launcher
        val targetPkg = alternateLaunchers.firstOrNull { it == "com.sec.android.app.launcher" }
            ?: alternateLaunchers.firstOrNull()

        if (targetPkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    `package` = targetPkg
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback: open Home Settings chooser
        openHomeSettings(context)
        return false
    }

    fun openHomeSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Open Android Settings > Apps > Default Apps > Home App", Toast.LENGTH_LONG).show()
            }
        }
    }
}
