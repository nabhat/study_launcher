package com.gatefocus.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AdminRemovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("AdminRemovalReceiver", "Received request to remove all restrictions and Device Owner")
        KioskManager.removeAllRestrictionsAndDeviceOwner(context)
    }
}
