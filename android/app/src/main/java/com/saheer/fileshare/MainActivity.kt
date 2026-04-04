package com.saheer.fileshare

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class MainActivity : ComponentActivity() {

    private var fileServer: FileServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShttsApp() }
    }

    override fun onResume() {
        super.onResume()
        // Permission refresh happens inside the composable via LaunchedEffect
    }

    @Composable
    fun ShttsApp() {
        val context = LocalContext.current
        val prefs = remember { getSharedPreferences("shttps_prefs", MODE_PRIVATE) }
        val clipboard = LocalClipboardManager.current

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

        // Refresh on every recompose (catches permission grant from settings)
        LaunchedEffect(Unit) {
            hasPermission = checkAllFilesAccess()
        }

        val Green = Color(0xFF34a853)
        val Blue  = Color(0xFF1a73e8)
        val Red   = Color(0xFFea4335)
        val BgCard = Color(0xFFF8F9FA)

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF1F3F4)) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ─── App Bar ───────────────────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Blue)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Column {
                            Text("Share File", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                color = Color.White, letterSpacing = 2.sp)
                            Text("HTTP File Server", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        // ─── Permission Banner ─────────────────────────────────────────────
                        if (!hasPermission) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, Color(0xFFFF9800))
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Storage Access Required", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Grant 'All Files Access' to browse and share freely.", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Button(
                                            onClick = { requestAllFilesAccess() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                        ) { Text("Grant") }
                                    }
                                }
                            }
                        }

                        // ─── Root Folder ───────────────────────────────────────────────────
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Shared Folder", fontSize = 11.sp, color = Color.Gray,
                                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Folder, contentDescription = null,
                                            tint = Color(0xFFFFA000), modifier = Modifier.size(28.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = selectedRoot.absolutePath,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f),
                                            color = Color(0xFF333333),
                                            maxLines = 2
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showFolderPicker = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Browse...", fontSize = 13.sp)
                                        }
                                        // Quick-select root
                                        if (hasPermission) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedRoot = Environment.getExternalStorageDirectory()
                                                    prefs.edit().putString("root_path", selectedRoot.absolutePath).apply()
                                                    logs.add("Root: ${selectedRoot.absolutePath}")
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Phone, contentDescription = null,
                                                    modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Internal", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ─── Server Control ────────────────────────────────────────────────
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Server", fontSize = 11.sp, color = Color.Gray,
                                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(Modifier.height(12.dp))

                                    // Port field
                                    OutlinedTextField(
                                        value = port,
                                        onValueChange = { v ->
                                            if (v.length <= 5 && v.all { it.isDigit() }) port = v
                                        },
                                        label = { Text("Port", fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isRunning,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    // Start / Stop button
                                    Button(
                                        onClick = {
                                            if (isRunning) {
                                                fileServer?.stop()
                                                isRunning = false
                                                serverUrl = ""
                                                logs.add("Server stopped.")
                                            } else {
                                                val p = port.toIntOrNull() ?: 8080
                                                val listener = object : FileServer.OnServerListener {
                                                    override fun onStarted(ip: String, port: Int) {
                                                        serverUrl = "http://$ip:$port"
                                                        isRunning = true
                                                        logs.add("Started: $serverUrl")
                                                    }
                                                    override fun onStopped() { isRunning = false; logs.add("Stopped.") }
                                                    override fun onError(msg: String) { logs.add("ERROR: $msg") }
                                                    override fun onLog(msg: String) { logs.add(msg) }
                                                }
                                                fileServer = FileServer(p, selectedRoot, listener)
                                                fileServer?.start()
                                                prefs.edit().putInt("port", p).apply()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isRunning) Red else Green
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (isRunning) "STOP SERVER" else "START SERVER",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        // ─── URL + QR (shown when running) ────────────────────────────────
                        if (isRunning && serverUrl.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    border = BorderStroke(1.dp, Green),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Server is Running ●", fontWeight = FontWeight.Bold,
                                            color = Green, fontSize = 14.sp)
                                        Spacer(Modifier.height(10.dp))

                                        // URL row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF1F8E9), RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = serverUrl,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Blue,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // Copy
                                            IconButton(onClick = {
                                                clipboard.setText(AnnotatedString(serverUrl))
                                                logs.add("Copied URL.")
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy",
                                                    tint = Blue, modifier = Modifier.size(20.dp))
                                            }
                                            // Share
                                            IconButton(onClick = {
                                                val i = Intent(Intent.ACTION_SEND)
                                                i.type = "text/plain"
                                                i.putExtra(Intent.EXTRA_TEXT, serverUrl)
                                                context.startActivity(Intent.createChooser(i, "Share via"))
                                            }) {
                                                Icon(Icons.Default.Share, contentDescription = "Share",
                                                    tint = Green, modifier = Modifier.size(20.dp))
                                            }
                                            // QR
                                            IconButton(onClick = { showQr = true }) {
                                                Icon(Icons.Default.QrCode, contentDescription = "QR",
                                                    tint = Color(0xFF333333), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ─── Activity Log ─────────────────────────────────────────────────
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Activity Log", color = Color(0xFF9E9E9E), fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        if (logs.isNotEmpty()) {
                                            TextButton(onClick = { logs.clear() }) {
                                                Text("Clear", color = Color(0xFF666666), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    val listState = rememberLazyListState()
                                    LaunchedEffect(logs.size) {
                                        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
                                    }

                                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                        items(logs) { entry ->
                                            Text(
                                                text = "> $entry",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                        if (logs.isEmpty()) {
                                            item {
                                                Text("Waiting for connections...",
                                                    color = Color(0xFF555555), fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    } // end LazyColumn
                }

                // ─── Folder Picker Dialog ─────────────────────────────────────────────────
                if (showFolderPicker) {
                    FolderPickerDialog(
                        initialDir = selectedRoot,
                        onDismiss = { showFolderPicker = false },
                        onFolderSelected = { dir ->
                            selectedRoot = dir
                            prefs.edit().putString("root_path", dir.absolutePath).apply()
                            logs.add("Folder: ${dir.absolutePath}")
                            if (isRunning) fileServer?.setRootDir(dir)
                        }
                    )
                }

                // ─── QR Code Dialog ───────────────────────────────────────────────────────
                if (showQr && serverUrl.isNotEmpty()) {
                    Dialog(onDismissRequest = { showQr = false }) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Scan to Open", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(16.dp))
                                val qrBmp = remember(serverUrl) { generateQrBitmap(serverUrl) }
                                if (qrBmp != null) {
                                    Image(
                                        bitmap = qrBmp.asImageBitmap(),
                                        contentDescription = "QR Code",
                                        modifier = Modifier.size(240.dp)
                                    )
                                } else {
                                    Text("Failed to generate QR.", color = Color.Red)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(serverUrl, fontSize = 12.sp, color = Color.Gray)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { showQr = false }) { Text("Close") }
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
