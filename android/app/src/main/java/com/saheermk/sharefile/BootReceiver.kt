package com.saheermk.sharefile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Automatically starts the KeepAliveService (which runs the server) 
 * on device boot if the feature is enabled in settings.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start_boot", false)
            
            Log.d("BootReceiver", "Boot completed. Auto-start enabled: $autoStart")

            if (autoStart) {
                // We logic to start the service, and the service will start the server via ServerManager
                val serviceIntent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
