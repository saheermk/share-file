package com.saheermk.sharefile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import android.widget.Toast
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.res.vectorResource
import java.net.NetworkInterface
import java.net.InetAddress
import java.util.Collections
import kotlin.collections.ArrayList

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShttsApp() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ShttsApp() {
        val context = LocalContext.current
        val prefs = remember { getSharedPreferences("shttps_prefs", MODE_PRIVATE) }
        val clipboard = LocalClipboardManager.current
        
        // Theme State
        val systemDark = isSystemInDarkTheme()
        var manualDarkTheme by remember { 
            val saved = if (prefs.contains("is_dark")) prefs.getBoolean("is_dark", false) else null
            mutableStateOf(saved) 
        }
        val isDark = manualDarkTheme ?: systemDark

        // Colors
        val Blue = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
        val Red = if (isDark) Color(0xFFF28B82) else Color(0xFFEA4335)
        val Green = if (isDark) Color(0xFF81C995) else Color(0xFF34A853)
        val BgColor = if (isDark) Color(0xFF1A1C20) else Color(0xFFE0E5EC)
        val TextColor = if (isDark) Color(0xFFE1E2E5) else Color(0xFF44475A)

        // State
        var hasPermission by remember { mutableStateOf(checkAllFilesAccess()) }
        var selectedRoot by remember {
            mutableStateOf(
                prefs.getString("root_path", Environment.getExternalStorageDirectory().absolutePath)!!
                    .let { File(it) }
            )
        }
        var port by remember { mutableStateOf(prefs.getInt("port", 8080).toString()) }
        val logs = ServerManager.logs
        var showFolderPicker by remember { mutableStateOf(false) }
        var showQr by remember { mutableStateOf(false) }

        // Advanced Settings States
        var enablePassword by remember { mutableStateOf(prefs.getBoolean("enable_password", false)) }
        var passwordValue by remember { mutableStateOf(prefs.getString("server_password", "")!!) }
        var passwordVisible by remember { mutableStateOf(false) }
        var allowModifications by remember { mutableStateOf(prefs.getBoolean("allow_modifications", true)) }
        var allowPreviews by remember { mutableStateOf(prefs.getBoolean("allow_previews", true)) }
        var currentPage by remember { mutableStateOf("home") }
        var selectedInterface by remember { mutableStateOf(prefs.getString("selected_interface", "0.0.0.0")!!) }
        val interfaces = remember { mutableStateListOf<Pair<String, String>>() } // Name to IP

        // Automation States
        var autoStartBoot by remember { mutableStateOf(prefs.getBoolean("auto_start_boot", false)) }
        var autoShutdownEnabled by remember { mutableStateOf(prefs.getBoolean("auto_shutdown_enabled", false)) }
        var inactivityTimeout by remember { mutableStateOf(prefs.getInt("inactivity_timeout_min", 60).toString()) }
        var showUrlActions by remember { mutableStateOf(false) }
        var selectedClientForDetails by remember { mutableStateOf<ClientInfo?>(null) }

        
        // Periodic Status Refresh
        LaunchedEffect(Unit) {
            while(true) {
                kotlinx.coroutines.delay(2000)
                ServerManager.refreshConnectionStatuses()
            }
        }


        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                val content = result.contents
                if (content.startsWith("http://") || content.startsWith("https://")) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(content)))
                    logs.add("Opened: $content")
                } else if (content.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")) || content.contains(":")) {
                    // Looks like an IP address (IPv4 or IPv6)
                    ServerManager.allowIp(context, content)
                    Toast.makeText(context, "Device Approved: $content", Toast.LENGTH_LONG).show()
                    logs.add("Authorized Device: $content")
                } else {
                    Toast.makeText(context, "Invalid QR content", Toast.LENGTH_SHORT).show()
                }
            }
        }

        LaunchedEffect(Unit) {

            ServerManager.init(context)
            hasPermission = checkAllFilesAccess()
            
            // Discover Interfaces
            interfaces.clear()
            interfaces.add("All Interfaces" to "0.0.0.0")
            try {
                val nets = NetworkInterface.getNetworkInterfaces()
                for (netint in Collections.list(nets)) {
                    if (!netint.isUp) continue
                    val addrs = netint.inetAddresses
                    for (inetAddress in Collections.list(addrs)) {
                        if (inetAddress is java.net.Inet4Address) {
                            interfaces.add("${netint.displayName}" to (inetAddress.hostAddress ?: "Unknown"))
                        }
                    }
                }
            } catch (e: Exception) { logs.add("Iface Discovery Error: ${e.message}") }
        }

        DisposableEffect(Unit) {
            ServerManager.listener = object : FileServer.OnServerListener {
                override fun onStarted(ip: String, actualPort: Int) {
                    port = actualPort.toString()
                }
                override fun onStopped() {}
                override fun onError(msg: String) {}
                override fun onLog(msg: String) {}
                override fun onClientActive(ip: String, ua: String) {}
                override fun onTelemetry(ip: String, bat: Int, ch: Boolean, mod: String, plat: String) {}
            }
            onDispose {
                ServerManager.listener = null
            }
        }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = BgColor) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ─── Header ──────────────────────────────────────────────────────────
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        Column(modifier = Modifier.align(Alignment.CenterStart)) {
                            Text(
                                "Share File",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Blue
                            )
                            Text(
                                "Secure Local Hosting",
                                fontSize = 11.sp,
                                color = TextColor.copy(alpha = 0.6f),
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Clients Button (New)
                            val clientCount = ServerManager.connectedClients.size
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .neumorphicShadow(CircleShape, if(isDark) Color(0xFF2E3238) else Color.White, if(isDark) Color(0xFF0F1113) else Color(0xFFA3B1C6).copy(alpha = 0.6f), isDark)
                                    .background(BgColor, CircleShape)
                                    .clickable { currentPage = if (currentPage == "home") "clients" else "home" },
                                contentAlignment = Alignment.Center
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (clientCount > 0) {
                                            Badge(containerColor = Blue, contentColor = Color.White) {
                                                Text(clientCount.toString(), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (currentPage == "home") Icons.Default.Groups else Icons.Default.Home,
                                        contentDescription = "Clients",
                                        tint = if (currentPage == "clients") Blue else TextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Scan Button
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .neumorphicShadow(CircleShape, if(isDark) Color(0xFF2E3238) else Color.White, if(isDark) Color(0xFF0F1113) else Color(0xFFA3B1C6).copy(alpha = 0.6f), isDark)
                                    .background(BgColor, CircleShape)
                                    .clickable { 
                                        scanLauncher.launch(ScanOptions().apply {
                                            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                            setPrompt("Scan a client's IP to grant access")
                                            setBeepEnabled(true)
                                            setOrientationLocked(true) // Force the orientation defined in Manifest
                                            setCaptureActivity(CustomCaptureActivity::class.java)
                                            setBarcodeImageEnabled(true)
                                        })
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = Blue, modifier = Modifier.size(20.dp))
                            }

                            // Theme Toggle
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .neumorphicShadow(CircleShape, if(isDark) Color(0xFF2E3238) else Color.White, if(isDark) Color(0xFF0F1113) else Color(0xFFA3B1C6).copy(alpha = 0.6f), isDark)
                                    .background(BgColor, CircleShape)
                                    .clickable { 
                                        manualDarkTheme = !isDark 
                                        prefs.edit().putBoolean("is_dark", !isDark).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme",
                                    tint = if (isDark) Color(0xFFFFD600) else Color(0xFF303F9F),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (currentPage == "home") {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                        // ─── Permission ───────────────────────────────────────────────────
                        if (!hasPermission) {
                            item {
                                NeumorphicCard(bgColor = Color(0xFFFFF3E0), isDark = isDark) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Files Access Needed", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                            Text("Required to share your folders.", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.6f))
                                        }
                                        Button(
                                            onClick = { requestAllFilesAccess() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                        ) { Text("Grant", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }

                        // ─── Root Folder ──────────────────────────────────────────────────
                        item {
                            NeumorphicCard(isDark = isDark) {
                                Text("SHARED DIRECTORY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = selectedRoot.absolutePath,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextColor,
                                        maxLines = 2
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    NeumorphicButton(
                                        onClick = { showFolderPicker = true },
                                        modifier = Modifier.weight(1f),
                                        accentColor = Blue,
                                        isDark = isDark
                                    ) {
                                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Browse", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    NeumorphicButton(
                                        onClick = {
                                            selectedRoot = Environment.getExternalStorageDirectory()
                                            prefs.edit().putString("root_path", selectedRoot.absolutePath).apply()
                                            logs.add("Switched to Internal Storage")
                                        },
                                        modifier = Modifier.weight(1f),
                                        accentColor = TextColor,
                                        isDark = isDark
                                    ) {
                                        Icon(Icons.Default.Smartphone, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Internal", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // ─── Server Status ────────────────────────────────────────────────
                        item {
                            NeumorphicCard(isDark = isDark) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("SERVER STATUS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(if (ServerManager.isRunning) Green else Red, CircleShape))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (ServerManager.isRunning) "Running" else "Stopped", fontWeight = FontWeight.Bold, color = TextColor)
                                            if (ServerManager.isRunning) {
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    "Logs",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Blue,
                                                    modifier = Modifier
                                                        .clickable { currentPage = "logs" }
                                                        .border(1.dp, Blue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    // Port Input
                                    Box(
                                        Modifier.width(80.dp).background(BgColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .border(1.dp, TextColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        BasicTextField(
                                            value = port,
                                            onValueChange = { newValue -> 
                                                if (newValue.length <= 5 && newValue.all { it.isDigit() }) port = newValue 
                                            },
                                            enabled = !ServerManager.isRunning,
                                            textStyle = TextStyle(color = TextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }
                                }

                                if (ServerManager.isRunning && ServerManager.serverUrl.isNotEmpty()) {
                                    Spacer(Modifier.height(20.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(TextColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                            .clickable { showUrlActions = true }
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(ServerManager.serverUrl, modifier = Modifier.weight(1f), color = Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            IconButton(onClick = { clipboard.setText(AnnotatedString(ServerManager.serverUrl)); logs.add("Copied URL") }) {
                                                Icon(Icons.Default.ContentCopy, null, tint = Blue, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { showQr = true }) {
                                                Icon(Icons.Default.QrCode, null, tint = TextColor, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))
                                 NeumorphicButton(
                                    onClick = {
                                        if (ServerManager.isRunning) {
                                            context.stopService(Intent(context, KeepAliveService::class.java))
                                            ServerManager.stopServer(context)
                                        } else {
                                            val p = port.toIntOrNull() ?: 8080
                                            prefs.edit().putInt("port", p).apply()
                                            
                                            ServerManager.startServer(context)
                                            
                                            val serviceIntent = Intent(context, KeepAliveService::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    accentColor = if (ServerManager.isRunning) Red else Green,
                                    isDark = isDark
                                ) {
                                    Icon(if (ServerManager.isRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (ServerManager.isRunning) "STOP" else "START", fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        // ─── Network Interface (Reordered here) ───────────────────────────
                        item {
                            NeumorphicCard(isDark = isDark) {
                                Text("NETWORK INTERFACE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f))
                                Spacer(Modifier.height(12.dp))
                                
                                var expanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopStart)) {
                                    OutlinedButton(
                                        onClick = { if (!ServerManager.isRunning) expanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue, disabledContentColor = Blue.copy(alpha = 0.7f)),
                                        enabled = !ServerManager.isRunning
                                    ) {
                                        val currentLabel = interfaces.find { it.second == selectedInterface }?.first ?: "Custom"
                                        Text("${currentLabel} (${selectedInterface})", fontSize = 13.sp)
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.background(if (isDark) Color(0xFF25282D) else Color.White)
                                    ) {
                                        interfaces.forEach { (name, ip) ->
                                            val isSelected = selectedInterface == ip
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        "$name ($ip)", 
                                                        color = if (isSelected) Blue else TextColor,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    ) 
                                                },
                                                onClick = {
                                                    selectedInterface = ip
                                                    prefs.edit().putString("selected_interface", ip).apply()
                                                    expanded = false
                                                },
                                                modifier = Modifier.background(if (isSelected && isDark) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                                            )
                                        }
                                    }
                                }
                                if (ServerManager.isRunning) {
                                    Text("Stop server to change interface", fontSize = 10.sp, color = Red.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }

                        // ─── Security & Settings ──────────────────────────────────────────
                        item {
                            val cardBg = if (isDark) Color(0xFF1E2126) else BgColor
                            NeumorphicCard(isDark = isDark, bgColor = cardBg) {
                                Text("SECURITY & SETTINGS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))

                                // Strict Mode Toggle (Requested as first toggle)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Strict Approval Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextColor)
                                        Text("New devices must be scanned by you", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                    }
                                    Switch(
                                        checked = ServerManager.strictMode,
                                        onCheckedChange = { 
                                            ServerManager.updateStrictMode(context, it)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                                    )
                                }

                                 Spacer(Modifier.height(16.dp))
                                 Divider(color = TextColor.copy(alpha = 0.05f))
                                 Spacer(Modifier.height(16.dp))

                                 // Max Connections
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                     Column(Modifier.weight(1f)) {
                                         Text("Max Connections", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextColor)
                                         Text("Limit simultaneous device access", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                     }
                                     
                                     var connExpanded by remember { mutableStateOf(false) }
                                     Box {
                                         Text(
                                             text = if (ServerManager.maxConnections == 0) "Unlimited" else ServerManager.maxConnections.toString(),
                                             color = Blue,
                                             fontWeight = FontWeight.Bold,
                                             fontSize = 14.sp,
                                             modifier = Modifier
                                                 .clickable { connExpanded = true }
                                                 .border(1.dp, Blue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                 .padding(horizontal = 12.dp, vertical = 6.dp)
                                         )
                                         DropdownMenu(
                                             expanded = connExpanded,
                                             onDismissRequest = { connExpanded = false },
                                             modifier = Modifier.background(if (isDark) Color(0xFF25282D) else Color.White)
                                         ) {
                                             val options = (1..10).map { it.toString() } + "Unlimited"
                                             options.forEach { opt ->
                                                 val value = if (opt == "Unlimited") 0 else opt.toInt()
                                                 DropdownMenuItem(
                                                     text = { Text(opt, color = TextColor) },
                                                     onClick = {
                                                         ServerManager.updateMaxConnections(context, value)
                                                         connExpanded = false
                                                     }
                                                 )
                                             }
                                         }
                                     }
                                 }

                                 Spacer(Modifier.height(16.dp))
                                 Divider(color = TextColor.copy(alpha = 0.05f))
                                 Spacer(Modifier.height(16.dp))
                                
                                // Password Toggle
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Password Protection", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextColor)
                                        Text("Ask for password to access files", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                    }
                                    Switch(
                                        checked = enablePassword,
                                        onCheckedChange = { 
                                            enablePassword = it
                                            prefs.edit().putBoolean("enable_password", it).apply()
                                            if (ServerManager.isRunning) ServerManager.fileServer?.setSecurity(it, passwordValue)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                                    )
                                }
                                
                                if (enablePassword) {
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = passwordValue,
                                        onValueChange = { 
                                            passwordValue = it
                                            prefs.edit().putString("server_password", it).apply()
                                            if (ServerManager.isRunning) ServerManager.fileServer?.setSecurity(enablePassword, it)
                                        },
                                        label = { Text("Server Password", fontSize = 12.sp) },
                                        placeholder = { Text("e.g. 1234") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Blue)
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextColor,
                                            unfocusedTextColor = TextColor,
                                            cursorColor = Blue,
                                            focusedBorderColor = Blue,
                                            unfocusedBorderColor = TextColor.copy(alpha = 0.2f),
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        )
                                    )
                                }

                                Spacer(Modifier.height(20.dp))
                                Divider(color = TextColor.copy(alpha = 0.05f))
                                Spacer(Modifier.height(20.dp))

                                // Modification Toggle
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Allow Modifications", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextColor)
                                        Text("Allow uploads, deletes, and renames", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                    }
                                    Switch(
                                        checked = allowModifications,
                                        onCheckedChange = { 
                                            allowModifications = it
                                            prefs.edit().putBoolean("allow_modifications", it).apply()
                                            if (ServerManager.isRunning) ServerManager.fileServer?.setToggles(it, allowPreviews)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // Previews Toggle
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Enable Previews", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextColor)
                                        Text("Render images/videos in browser", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                    }
                                    Switch(
                                        checked = allowPreviews,
                                        onCheckedChange = { 
                                            allowPreviews = it
                                            prefs.edit().putBoolean("allow_previews", it).apply()
                                            if (ServerManager.isRunning) ServerManager.fileServer?.setToggles(allowModifications, it)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                                    )
                                }
                            }
                        }

                        // ─── Automation Settings ──────────────────────────────────────────
                        item {
                            val cardBg = if (isDark) Color(0xFF1E2126) else BgColor
                            NeumorphicCard(isDark = isDark, bgColor = cardBg) {
                                Text("AUTOMATION", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))

                                // Auto Start on Boot
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Auto Start on Boot", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextColor)
                                        Text("Start server when device restarts", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                    }
                                    Switch(
                                        checked = autoStartBoot,
                                        onCheckedChange = {
                                            autoStartBoot = it
                                            prefs.edit().putBoolean("auto_start_boot", it).apply()
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // Auto Shutdown
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Auto Shutdown", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextColor)
                                        Text("Stop server after inactivity", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                    }
                                    Switch(
                                        checked = autoShutdownEnabled,
                                        onCheckedChange = {
                                            autoShutdownEnabled = it
                                            prefs.edit().putBoolean("auto_shutdown_enabled", it).apply()
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                                    )
                                }

                                if (autoShutdownEnabled) {
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = inactivityTimeout,
                                        onValueChange = {
                                            if (it.all { char -> char.isDigit() }) {
                                                inactivityTimeout = it
                                                it.toIntOrNull()?.let { mins ->
                                                    prefs.edit().putInt("inactivity_timeout_min", mins).apply()
                                                }
                                            }
                                        },
                                        label = { Text("Timeout (minutes)", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextColor,
                                            unfocusedTextColor = TextColor,
                                            cursorColor = Blue,
                                            focusedBorderColor = Blue,
                                            unfocusedBorderColor = TextColor.copy(alpha = 0.2f),
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        )
                                    )
                                }

                                Spacer(Modifier.height(20.dp))
                                // Done removing HTTPS

                            }
                        }


                        item { Spacer(Modifier.height(20.dp)) }
                    }

                    // ─── Footer ──────────────────────────────────────────────────────────
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Developed by saheermk",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextColor.copy(alpha = 0.7f),
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://saheermk.pages.dev")))
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Open Source on GitHub",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Blue,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/saheermk/share-file")))
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        }
                    } else if (currentPage == "clients") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // ─── Active Section ──────────────────────────────────────────
                                item {
                                    Text(
                                        "ACTIVE CONNECTIONS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextColor.copy(alpha = 0.5f),
                                        letterSpacing = 1.sp
                                    )
                                }

                                if (ServerManager.connectedClients.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No active clients", color = TextColor.copy(alpha = 0.4f), fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                     items(ServerManager.connectedClients.values.toList()) { client ->
                                         NeumorphicCard(
                                             isDark = isDark,
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .clickable { selectedClientForDetails = client }
                                         ) {
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 Box(
                                                     Modifier
                                                         .size(44.dp)
                                                         .background(Blue.copy(alpha = 0.1f), CircleShape),
                                                     contentAlignment = Alignment.Center
                                                 ) {
                                                     Icon(
                                                         when {
                                                             client.deviceName.contains("PC") || client.deviceName.contains("Windows") || client.deviceName.contains("Mac") || client.deviceName.contains("Linux") -> Icons.Default.Computer
                                                             else -> Icons.Default.Smartphone
                                                         },
                                                         null,
                                                         tint = Blue,
                                                         modifier = Modifier.size(20.dp)
                                                     )
                                                 }
                                                 Spacer(Modifier.width(16.dp))
                                                 Column(Modifier.weight(1f)) {
                                                     Text(client.deviceName, fontWeight = FontWeight.Bold, color = TextColor, fontSize = 14.sp)
                                                     val lastSeen = if (client.isOnline) "Just now" else {
                                                         val diff = (System.currentTimeMillis() - client.lastActive) / 1000
                                                         when {
                                                             diff < 60 -> "${diff}s ago"
                                                             diff < 3600 -> "${diff / 60}m ago"
                                                             else -> "${diff / 3600}h ago"
                                                         }
                                                     }
                                                     Text("${client.ip} • $lastSeen", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                                                 }
                                                 Box(
                                                     modifier = Modifier
                                                         .background((if (client.isOnline) Green else Red).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                         .padding(horizontal = 8.dp, vertical = 4.dp)
                                                 ) {
                                                     Text(if (client.isOnline) "ACTIVE" else "OFFLINE", color = if (client.isOnline) Green else Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                 }
                                             }
                                         }
                                     }
                                }

                                // ─── Blocked Section ─────────────────────────────────────────
                                if (ServerManager.blockedIps.isNotEmpty()) {
                                    item {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "BLOCKED DEVICES",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Red.copy(alpha = 0.6f),
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    items(ServerManager.blockedIps.keys.toList()) { ip ->
                                        val deviceName = ServerManager.archivedDeviceNames[ip] ?: "Unknown Device"
                                        NeumorphicCard(isDark = isDark, modifier = Modifier.fillMaxWidth()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(deviceName, fontWeight = FontWeight.Bold, color = TextColor, fontSize = 14.sp)
                                                    Text(ip, fontSize = 12.sp, color = TextColor.copy(alpha = 0.6f))
                                                }
                                                Button(
                                                    onClick = { ServerManager.toggleBlock(context, ip) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Red.copy(alpha = 0.1f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("UNBLOCK", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (currentPage == "logs") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "SYSTEM LOGS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextColor.copy(alpha = 0.5f),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "Back",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Blue,
                                    modifier = Modifier.clickable { currentPage = "home" }
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            NeumorphicCard(
                                bgColor = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF1F3F4),
                                isDark = isDark,
                                modifier = Modifier.fillMaxSize().weight(1f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val listState = rememberLazyListState()
                                    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }
                                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                        items(logs) { entry ->
                                            Text(
                                                "> $entry",
                                                color = Green,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                // Dialogs
                if (showFolderPicker) {
                    FolderPickerDialog(
                        initialDir = selectedRoot,
                        onDismiss = { showFolderPicker = false },
                        onFolderSelected = { dir ->
                            selectedRoot = dir
                            prefs.edit().putString("root_path", dir.absolutePath).apply()
                            logs.add("Path changed")
                            if (ServerManager.isRunning) ServerManager.fileServer?.setRootDir(dir)
                        }
                    )
                }

                if (showQr && ServerManager.serverUrl.isNotEmpty()) {
                    Dialog(onDismissRequest = { showQr = false }) {
                        NeumorphicCard(bgColor = Color.White, isDark = false) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Scan to access", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.Black)
                                Spacer(Modifier.height(16.dp))
                                val qrBmp = remember(ServerManager.serverUrl) { generateQrBitmap(ServerManager.serverUrl) }
                                if (qrBmp != null) {
                                    Image(bitmap = qrBmp.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(200.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                NeumorphicButton(onClick = { showQr = false }, accentColor = Blue, isDark = false) {
                                    Text("CLOSE", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (showUrlActions && ServerManager.serverUrl.isNotEmpty()) {
                    Dialog(onDismissRequest = { showUrlActions = false }) {
                        NeumorphicCard(isDark = isDark, bgColor = if (isDark) Color(0xFF1E2126) else BgColor) {
                            Column(Modifier.padding(8.dp)) {
                                Text("URL Actions", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextColor, modifier = Modifier.padding(bottom = 16.dp))
                                
                                val actions = listOf(
                                    Triple("Navigate", Icons.Default.OpenInNew) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ServerManager.serverUrl)))
                                    },
                                    Triple("Copy", Icons.Default.ContentCopy) {
                                        clipboard.setText(AnnotatedString(ServerManager.serverUrl))
                                        logs.add("Copied URL")
                                    },
                                    Triple("Share", Icons.Default.Share) {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, ServerManager.serverUrl)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share URL"))
                                    },
                                    Triple("QR Code", Icons.Default.QrCode) {
                                        showQr = true
                                    }
                                )

                                actions.forEach { (label, icon, action) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { action(); showUrlActions = false }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(16.dp))
                                        Text(label, color = TextColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedClientForDetails != null) {
            val client = selectedClientForDetails!!
            Dialog(onDismissRequest = { selectedClientForDetails = null }) {
                NeumorphicCard(isDark = isDark, bgColor = if (isDark) Color(0xFF1E2126) else BgColor) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (client.deviceName.contains("PC") || client.deviceName.contains("Windows") || client.deviceName.contains("Mac")) Icons.Default.Computer else Icons.Default.Smartphone,
                                null, tint = Blue, modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(client.deviceName, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextColor)
                                Text(client.ip, fontSize = 12.sp, color = TextColor.copy(alpha = 0.6f))
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Text("PERMISSIONS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f), letterSpacing = 1.sp)
                        Spacer(Modifier.height(12.dp))
                        
                        // Per-client permission toggles
                        // Note: Using null means use global. For UI simplicity, I'll show it as a toggle but state it overrides.
                        val clientMod = client.customAllowModifications ?: allowModifications
                        val clientPrev = client.customAllowPreviews ?: allowPreviews

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val batteryText = if (client.batteryLevel != null && client.batteryLevel != -1) {
                                "${client.batteryLevel}%" + (if (client.isCharging == true) " (Charging)" else "")
                            } else "N/A"
                            Text("Battery Status", modifier = Modifier.weight(1f), color = TextColor, fontSize = 13.sp)
                            Text(batteryText, fontWeight = FontWeight.Bold, color = Blue, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Allow Modifications", modifier = Modifier.weight(1f), color = TextColor, fontSize = 13.sp)
                            Switch(
                                checked = clientMod,
                                onCheckedChange = { ServerManager.updateClientPermissions(client.ip, it, client.customAllowPreviews); selectedClientForDetails = ServerManager.connectedClients[client.ip] },
                                colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Allow Previews", modifier = Modifier.weight(1f), color = TextColor, fontSize = 13.sp)
                            Switch(
                                checked = clientPrev,
                                onCheckedChange = { ServerManager.updateClientPermissions(client.ip, client.customAllowModifications, it); selectedClientForDetails = ServerManager.connectedClients[client.ip] },
                                colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        Text("DEVICE INFO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f), letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        if (client.reportedModel != null) {
                            Text("Model: ${client.reportedModel}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextColor)
                            Text("Platform: ${client.platform ?: "Unknown"}", fontSize = 11.sp, color = TextColor.copy(alpha = 0.6f))
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(client.userAgent, fontSize = 10.sp, color = TextColor.copy(alpha = 0.4f), lineHeight = 14.sp)
                        
                        Spacer(Modifier.height(32.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NeumorphicButton(
                                onClick = { 
                                    ServerManager.toggleBlock(context, client.ip)
                                    selectedClientForDetails = null
                                }, 
                                accentColor = Red, 
                                modifier = Modifier.weight(1f),
                                isDark = isDark
                            ) {
                                Text("BLOCK DEVICE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            NeumorphicButton(
                                onClick = { selectedClientForDetails = null }, 
                                accentColor = Blue, 
                                modifier = Modifier.weight(1f),
                                isDark = isDark
                            ) {
                                Text("CLOSE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                         }
                    }
                }
            }
        }
    }
    }

    private fun checkAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")))
        } else {
            requestPermissions(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), 100)
        }
    }
}

// ─── Skeuomorphic (Neumorphic) UI Components ───────────────────────────────

fun Modifier.neumorphicShadow(
    shape: androidx.compose.ui.graphics.Shape,
    lightShadowColor: Color,
    darkShadowColor: Color,
    isDark: Boolean
) = this.drawBehind {
    val shadowDistance = 4.dp.toPx()
    val blurRadius = 8.dp.toPx()

    drawIntoCanvas { canvas ->
        val paint = androidx.compose.ui.graphics.Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        
        // Light shadow (top-left)
        frameworkPaint.color = Color.Transparent.toArgb()
        frameworkPaint.setShadowLayer(blurRadius, -shadowDistance, -shadowDistance, lightShadowColor.toArgb())
        canvas.drawOutline(shape.createOutline(size, layoutDirection, this), paint)

        // Dark shadow (bottom-right)
        frameworkPaint.color = Color.Transparent.toArgb()
        frameworkPaint.setShadowLayer(blurRadius, shadowDistance, shadowDistance, darkShadowColor.toArgb())
        canvas.drawOutline(shape.createOutline(size, layoutDirection, this), paint)
    }
}

@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    bgColor: Color? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = bgColor ?: if (isDark) Color(0xFF1A1C20) else Color(0xFFE0E5EC)
    val shadowLight = if (isDark) Color(0xFF2E3238) else Color.White
    val shadowDark = if (isDark) Color(0xFF0F1113) else Color(0xFFA3B1C6).copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .neumorphicShadow(shape, shadowLight, shadowDark, isDark)
            .background(bg, shape)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = Color(0xFF1A73E8),
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val bg = if (isDark) Color(0xFF1A1C20) else Color(0xFFE0E5EC)
    val shadowLight = if (isDark) Color(0xFF2E3238) else Color.White
    val shadowDark = if (isDark) Color(0xFF0F1113) else Color(0xFFA3B1C6).copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .then(
                if (isPressed && enabled) {
                    Modifier.background(bg.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                } else {
                    Modifier.neumorphicShadow(RoundedCornerShape(16.dp), shadowLight, shadowDark, isDark)
                        .background(bg, RoundedCornerShape(16.dp))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) accentColor else Color.Gray
            ) {
                content()
            }
        }
    }
}

@Composable
fun SocialIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, isDark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .neumorphicShadow(CircleShape, if(isDark) Color(0xFF2E3238) else Color.White, if(isDark) Color(0xFF0F1113) else Color(0xFFA3B1C6).copy(alpha = 0.6f), isDark)
            .background(if(isDark) Color(0xFF1A1C20) else Color(0xFFE0E5EC), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val tintColor = if (isDark) Color(0xFFE1E2E5) else Color(0xFF44475A)
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if(isDark) Color.White.copy(alpha = 0.7f) else tintColor)
    }
}

fun generateQrBitmap(content: String): android.graphics.Bitmap? {
    try {
        val size = 512
        val hints = HashMap<com.google.zxing.EncodeHintType, Any>()
        hints[com.google.zxing.EncodeHintType.MARGIN] = 2 // Increased margin
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints
        )
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    } catch (e: Exception) { return null }
}
