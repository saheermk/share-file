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
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : ComponentActivity() {

    private var fileServer: FileServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShttsApp() }
    }

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
        var isRunning by remember { mutableStateOf(false) }
        var serverUrl by remember { mutableStateOf("") }
        val logs = remember { mutableStateListOf<String>() }
        var showFolderPicker by remember { mutableStateOf(false) }
        var showQr by remember { mutableStateOf(false) }

        // Advanced Settings States
        var enablePassword by remember { mutableStateOf(prefs.getBoolean("enable_password", false)) }
        var passwordValue by remember { mutableStateOf(prefs.getString("server_password", "")!!) }
        var passwordVisible by remember { mutableStateOf(false) }
        var allowModifications by remember { mutableStateOf(prefs.getBoolean("allow_modifications", true)) }
        var allowPreviews by remember { mutableStateOf(prefs.getBoolean("allow_previews", true)) }
        var selectedInterface by remember { mutableStateOf(prefs.getString("selected_interface", "0.0.0.0")!!) }
        val interfaces = remember { mutableStateListOf<Pair<String, String>>() } // Name to IP

        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                if (result.contents.startsWith("http://") || result.contents.startsWith("https://")) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.contents)))
                    logs.add("Opened: ${result.contents}")
                } else {
                    Toast.makeText(context, "Invalid QR content", Toast.LENGTH_SHORT).show()
                }
            }
        }

        LaunchedEffect(Unit) {
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
                            interfaces.add("${netint.displayName}" to inetAddress.hostAddress)
                        }
                    }
                }
            } catch (e: Exception) { logs.add("Iface Discovery Error: ${e.message}") }
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
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Scan Button
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .neumorphicShadow(CircleShape, if(isDark) Color(0xFF2E3238) else Color.White, if(isDark) Color(0xFF0F1113) else Color(0xFFA3B1C6).copy(alpha = 0.6f), isDark)
                                    .background(BgColor, CircleShape)
                                    .clickable { 
                                        scanLauncher.launch(ScanOptions().apply {
                                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            setPrompt("Scan QR Code to Connect")
                                            setBeepEnabled(false)
                                            setOrientationLocked(false)
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
                                Text(if (isDark) "☀️" else "🌙", fontSize = 18.sp)
                            }
                        }
                    }

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
                                            Box(Modifier.size(8.dp).background(if (isRunning) Green else Red, CircleShape))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (isRunning) "Running" else "Stopped", fontWeight = FontWeight.Bold, color = TextColor)
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
                                            enabled = !isRunning,
                                            textStyle = TextStyle(color = TextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }
                                }

                                if (isRunning && serverUrl.isNotEmpty()) {
                                    Spacer(Modifier.height(20.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(TextColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(serverUrl, modifier = Modifier.weight(1f), color = Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            IconButton(onClick = { clipboard.setText(AnnotatedString(serverUrl)); logs.add("Copied URL") }) {
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
                                        if (isRunning) {
                                            fileServer?.stop()
                                            isRunning = false
                                            serverUrl = ""
                                            logs.add("Stopped server")
                                            context.stopService(Intent(context, KeepAliveService::class.java))
                                        } else {
                                            val p = port.toIntOrNull() ?: 8080
                                            val listener = object : FileServer.OnServerListener {
                                                override fun onStarted(ip: String, port: Int) {
                                                    serverUrl = "http://$ip:$port"
                                                    isRunning = true
                                                    logs.add("Started @ $serverUrl")
                                                }
                                                override fun onStopped() { isRunning = false; logs.add("Server down") }
                                                override fun onError(msg: String) { logs.add("ERR: $msg") }
                                                override fun onLog(msg: String) { logs.add(msg) }
                                            }
                                            fileServer = FileServer(p, selectedRoot, context, listener)
                                            fileServer?.setSecurity(enablePassword, passwordValue)
                                            fileServer?.setToggles(allowModifications, allowPreviews)
                                            fileServer?.setInterface(selectedInterface)
                                            fileServer?.start()
                                            prefs.edit().putInt("port", p).apply()
                                            
                                            val serviceIntent = Intent(context, KeepAliveService::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    accentColor = if (isRunning) Red else Green,
                                    isDark = isDark
                                ) {
                                    Icon(if (isRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (isRunning) "STOP SERVER" else "START SERVER", fontWeight = FontWeight.Black)
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
                                        onClick = { if (!isRunning) expanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue, disabledContentColor = Blue.copy(alpha = 0.7f)),
                                        enabled = !isRunning
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
                                if (isRunning) {
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
                                            if (isRunning) fileServer?.setSecurity(it, passwordValue)
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
                                            if (isRunning) fileServer?.setSecurity(enablePassword, it)
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
                                            if (isRunning) fileServer?.setToggles(it, allowPreviews)
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
                                            if (isRunning) fileServer?.setToggles(allowModifications, it)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Blue)
                                    )
                                }
                            }
                        }


                        // ─── Activity Log ─────────────────────────────────────────────────
                        item {
                            NeumorphicCard(bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF1F3F4), isDark = isDark) {
                                Text("REMOTE ACTIVITY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextColor.copy(alpha = 0.5f))
                                Spacer(Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                                    val listState = rememberLazyListState()
                                    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }
                                    LazyColumn(state = listState) {
                                        items(logs) { entry ->
                                            Text("> $entry", color = Green, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                                        }
                                    }
                                }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SocialIcon(ImageVector.vectorResource(id = R.drawable.ic_github), isDark = isDark) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/saheermk/")))
                            }
                            SocialIcon(ImageVector.vectorResource(id = R.drawable.ic_linkedin), isDark = isDark) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://in.linkedin.com/in/saheermk")))
                            }
                        }
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
                            if (isRunning) fileServer?.setRootDir(dir)
                        }
                    )
                }

                if (showQr && serverUrl.isNotEmpty()) {
                    Dialog(onDismissRequest = { showQr = false }) {
                        NeumorphicCard(bgColor = Color.White, isDark = false) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Scan to access", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.Black)
                                Spacer(Modifier.height(16.dp))
                                val qrBmp = remember(serverUrl) { generateQrBitmap(serverUrl) }
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
        hints[com.google.zxing.EncodeHintType.MARGIN] = 1
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
