package com.saheermk.sharefile

import android.content.Context
import android.os.Environment
import java.io.File
import androidx.compose.runtime.*
import java.util.concurrent.CopyOnWriteArrayList

data class ClientInfo(
    val ip: String,
    val deviceName: String,
    var lastActive: Long = System.currentTimeMillis(),
    val userAgent: String,
    var isBlocked: Boolean = false,
    var customAllowModifications: Boolean? = null,
    var customAllowPreviews: Boolean? = null,
    var batteryLevel: Int? = null,
    var isCharging: Boolean? = null,
    var reportedModel: String? = null,
    var platform: String? = null
) {
    val isOnline: Boolean get() = (System.currentTimeMillis() - lastActive) < 15000 // 15 seconds threshold
}

/**
 * Singleton to manage the FileServer instance across MainActivity and KeepAliveService.
 */
object ServerManager {
    var fileServer: FileServer? = null
    val logs = mutableStateListOf<String>()
    val connectedClients = mutableStateMapOf<String, ClientInfo>() // IP to ClientInfo
    var isRunning by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var maxConnections by mutableIntStateOf(0)
    var strictMode by mutableStateOf(false)
    val allowedIps = mutableStateListOf<String>()
    val blockedIps = mutableStateMapOf<String, Boolean>() // IP -> true
    val archivedDeviceNames = mutableStateMapOf<String, String>() // IP -> DeviceName (last known)
    val customClientSettings = mutableStateMapOf<String, Pair<Boolean?, Boolean?>>() // IP -> (ModAllowed?, PrevAllowed?)
    var listener: FileServer.OnServerListener? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
        
        // Load Blocked IPs
        val blockedSet = prefs.getStringSet("blocked_ips", emptySet()) ?: emptySet()
        blockedIps.clear()
        blockedSet.forEach { blockedIps[it] = true }

        // Load Allowed IPs (Strict Mode)
        strictMode = prefs.getBoolean("strict_mode", false)
        val allowedSet = prefs.getStringSet("allowed_ips", emptySet()) ?: emptySet()
        allowedIps.clear()
        allowedIps.addAll(allowedSet)

        // Load Max Connections
        maxConnections = prefs.getInt("max_connections", 0)
        
        // Load Archived Device Names
        val archivedJson = prefs.getString("archived_devices", "{}") ?: "{}"
        try {
            val obj = org.json.JSONObject(archivedJson)
            archivedDeviceNames.clear()
            obj.keys().forEach { ip ->
                archivedDeviceNames[ip] = obj.getString(ip)
            }
        } catch (e: Exception) {}
    }

    private fun parseDeviceName(userAgent: String): String {
        return try {
            val browser = when {
                userAgent.contains("Edg/") -> "Edge"
                userAgent.contains("OPR/") || userAgent.contains("Opera/") -> "Opera"
                userAgent.contains("Chrome/") -> "Chrome"
                userAgent.contains("Firefox/") -> "Firefox"
                userAgent.contains("Safari/") && !userAgent.contains("Chrome") -> "Safari"
                else -> ""
            }

            val os = when {
                userAgent.contains("Android") -> {
                    val parts = userAgent.substringAfter("(").substringBefore(")").split(";")
                    // Fallback logic for Android model
                    val modelCandidate = if (parts.size >= 3) {
                        parts[2].trim()
                    } else if (parts.size >= 2) {
                        parts[1].trim().removePrefix("Android").trim()
                    } else {
                        "Android"
                    }
                    if (modelCandidate.isEmpty() || modelCandidate == "K" || modelCandidate.length <= 1) "Android Device" else modelCandidate
                }
                userAgent.contains("iPhone") -> "iPhone"
                userAgent.contains("iPad") -> "iPad"
                userAgent.contains("Windows") -> {
                    if (userAgent.contains("Windows NT 10.0")) "Windows 10/11 PC"
                    else if (userAgent.contains("Windows NT 6.1")) "Windows 7 PC"
                    else "Windows PC"
                }
                userAgent.contains("Macintosh") -> "Mac"
                userAgent.contains("Linux") -> "Linux PC"
                else -> "Generic Device"
            }
            
            if (browser.isNotEmpty()) "$os ($browser)" else os
        } catch (e: Exception) { "Unknown Device" }
    }

    fun startServer(context: Context, listener: FileServer.OnServerListener? = null) {
        if (isRunning) return

        val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
        val port = prefs.getInt("port", 8080)
        val rootPath = prefs.getString("root_path", Environment.getExternalStorageDirectory().absolutePath)!!
        val enablePassword = prefs.getBoolean("enable_password", false)
        val password = prefs.getString("password", "") ?: ""
        val allowModifications = prefs.getBoolean("allow_modifications", true)
        val allowPreviews = prefs.getBoolean("allow_previews", true)
        val selectedInterface = prefs.getString("selected_interface", "0.0.0.0") ?: "0.0.0.0"
        val maxConn = prefs.getInt("max_connections", 0)
        

        val internalListener = object : FileServer.OnServerListener {
            override fun onStarted(ip: String, port: Int) {
                // We no longer permanently save the auto-shifted port here.
                // The UI will still show the active port via the GlobalListener.

                serverUrl = "http://$ip:$port"
                isRunning = true
                logs.add("Started @ $serverUrl")
                listener?.onStarted(ip, port)
                ServerManager.listener?.onStarted(ip, port)
            }

            override fun onStopped() {
                isRunning = false
                serverUrl = ""
                logs.add("Server down")
                listener?.onStopped()
                ServerManager.listener?.onStopped()
            }

            override fun onError(msg: String) {
                logs.add("ERR: $msg")
                listener?.onError(msg)
                ServerManager.listener?.onError(msg)
            }

            override fun onLog(msg: String) {
                logs.add(msg)
                listener?.onLog(msg)
                ServerManager.listener?.onLog(msg)
            }

            override fun onClientActive(ip: String, userAgent: String) {
                if (blockedIps.containsKey(ip)) return

                val existing = connectedClients[ip]
                val custom = customClientSettings[ip]
                val deviceName = if (existing == null) parseDeviceName(userAgent) else existing.deviceName
                
                // Archive the name
                archivedDeviceNames[ip] = deviceName
                
                if (existing == null) {
                    val device = parseDeviceName(userAgent)
                    connectedClients[ip] = ClientInfo(
                        ip, device, 
                        userAgent = userAgent,
                        customAllowModifications = custom?.first,
                        customAllowPreviews = custom?.second
                    )
                    logs.add("Client connected: $device ($ip)")
                } else {
                    existing.lastActive = System.currentTimeMillis()
                    // Map update to trigger recomposition
                    connectedClients[ip] = existing.copy()
                }
                ServerManager.listener?.onClientActive(ip, userAgent)
            }

            override fun onTelemetry(ip: String, batteryLevel: Int, isCharging: Boolean, model: String, platform: String) {
                connectedClients[ip]?.let {
                    connectedClients[ip] = it.copy(
                        lastActive = System.currentTimeMillis(),
                        batteryLevel = batteryLevel,
                        isCharging = isCharging,
                        reportedModel = if (model != "Unknown") model else it.reportedModel,
                        platform = if (platform != "Unknown") platform else it.platform
                    )
                }
                ServerManager.listener?.onTelemetry(ip, batteryLevel, isCharging, model, platform)
            }
        }

        logs.clear()
        connectedClients.clear()
        fileServer = FileServer(port, File(rootPath), context, internalListener).apply {
            setSecurity(enablePassword, password)
            setToggles(allowModifications, allowPreviews)
            setInterface(selectedInterface)
            setMaxConnections(maxConn)
            logs.add("Start: Strict=${strictMode}, Allowed Count=${allowedIps.size}, MaxConn=${if(maxConn==0) "Unlimited" else maxConn}")
            setStrictMode(strictMode, allowedIps.toSet())
            start()
        }
    }

    fun stopServer(context: Context) {
        fileServer?.stop()
        fileServer = null
        isRunning = false
        serverUrl = ""
        
        // Reset Strict Mode Authentication on Stop
        logs.add("Resetting allowlist (Count was ${allowedIps.size})")
        allowedIps.clear()
        val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("allowed_ips").apply()
    }

    fun toggleBlock(context: Context, ip: String) {
        val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
        val blockedSet = prefs.getStringSet("blocked_ips", emptySet())?.toMutableSet() ?: mutableSetOf()

        if (blockedIps.containsKey(ip)) {
            blockedIps.remove(ip)
            blockedSet.remove(ip)
            fileServer?.setBlocked(ip, false)
        } else {
            blockedIps[ip] = true
            blockedSet.add(ip)
            connectedClients.remove(ip)
            fileServer?.setBlocked(ip, true)
        }
        
        prefs.edit().putStringSet("blocked_ips", blockedSet).apply()

        // Also save archived names
        val obj = org.json.JSONObject()
        archivedDeviceNames.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("archived_devices", obj.toString()).apply()
    }

    fun updateClientPermissions(ip: String, mod: Boolean?, prev: Boolean?) {
        customClientSettings[ip] = mod to prev
        connectedClients[ip]?.let {
            connectedClients[ip] = it.copy(customAllowModifications = mod, customAllowPreviews = prev)
        }
        fileServer?.setClientPermission(ip, "mod", mod)
        fileServer?.setClientPermission(ip, "prev", prev)
    }

    fun allowIp(context: Context, ip: String) {
        if (!allowedIps.contains(ip)) {
            allowedIps.add(ip)
            val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("allowed_ips", allowedIps.toSet()).apply()
            fileServer?.setStrictMode(strictMode, allowedIps.toSet())
        }
    }

    fun updateStrictMode(context: Context, enabled: Boolean) {
        strictMode = enabled
        val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("strict_mode", enabled).apply()
        fileServer?.setStrictMode(enabled, allowedIps.toSet())
    }

    fun updateMaxConnections(context: Context, count: Int) {
        maxConnections = count
        val prefs = context.getSharedPreferences("shttps_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("max_connections", count).apply()
        fileServer?.setMaxConnections(count)
    }

    /**
     * Called by UI to force-refresh online status of clients.
     */
    fun refreshConnectionStatuses() {
        val now = System.currentTimeMillis()
        val toPrune = mutableListOf<String>()
        
        connectedClients.forEach { (ip, info) ->
            // If offline for more than 5 minutes, we might want to prune
            // but for now, just toggling isOnline is enough for UI.
            // To trigger recomposition of the map entry, we re-set it.
            connectedClients[ip] = info.copy() 
        }
    }
}
