package com.saheermk.sharefile

import android.app.*
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var inactivityCheckRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("shttps_prefs", MODE_PRIVATE)
        
        // Start server if not running
        if (!ServerManager.isRunning) {
            ServerManager.startServer(this)
        }

        val channelId = "fileshare_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Share File Server", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Share File Server Running")
            .setContentText("Share File is running • ${ServerManager.serverUrl}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(1, notification)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FileShare::ServerWakeLock")
        wakeLock?.acquire(300 * 60 * 1000L /* 5 hours */)

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FileShare::ServerWifiLock")
        wifiLock?.acquire()

        // Inactivity Shutdown Logic
        startInactivityCheck()

        return START_STICKY
    }

    private fun startInactivityCheck() {
        inactivityCheckRunnable?.let { handler.removeCallbacks(it) }
        
        inactivityCheckRunnable = object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("shttps_prefs", MODE_PRIVATE)
                val autoShutdown = prefs.getBoolean("auto_shutdown_enabled", false)
                val timeoutMin = prefs.getInt("inactivity_timeout_min", 60)
                
                if (autoShutdown && ServerManager.isRunning) {
                    val lastActivity = ServerManager.fileServer?.lastActivityTime ?: 0L
                    val inactiveMillis = System.currentTimeMillis() - lastActivity
                    val timeoutMillis = timeoutMin * 60 * 1000L
                    
                    if (inactiveMillis >= timeoutMillis) {
                        ServerManager.stopServer(this@KeepAliveService)
                        stopForeground(true)
                        stopSelf()
                        return
                    }
                }
                // Check again in 1 minute
                handler.postDelayed(this, 60 * 1000L)
            }
        }
        handler.postDelayed(inactivityCheckRunnable!!, 60 * 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        inactivityCheckRunnable?.let { handler.removeCallbacks(it) }
        ServerManager.stopServer(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
