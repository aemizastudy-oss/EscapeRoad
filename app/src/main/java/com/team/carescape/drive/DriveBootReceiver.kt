package com.team.carescape.drive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Ignition on boots the head unit: resume the drive chapter if a trip is in progress, without opening the app. */
class DriveBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) DriveService.ensureRunning(context)
    }
}
