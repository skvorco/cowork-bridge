package ru.papam.eyeguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            if (Prefs(context).monitoringEnabled) {
                // Note: on Android 12+ starting a camera foreground service from
                // the background may be blocked by the OS; in that case the app
                // must be opened once after a reboot to resume monitoring.
                try {
                    FaceMonitorService.start(context)
                } catch (_: Exception) {
                }
            }
        }
    }
}
