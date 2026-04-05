package com.saheermk.sharefile

import android.graphics.Bitmap
import android.graphics.Color as GColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/**
 * In-app custom folder picker — shows real filesystem tree.
 * No system picker. Uses MANAGE_EXTERNAL_STORAGE to traverse freely.
 */
@Composable
fun FolderPickerDialog(
    initialDir: File,
    onDismiss: () -> Unit,
    onFolderSelected: (File) -> Unit
) {
    var currentDir by remember { mutableStateOf(initialDir) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select Folder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Current path bar
                Text(
                    text = currentDir.absolutePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    maxLines = 1
                )

                // Back button when not at root
                if (currentDir.parentFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentDir = currentDir.parentFile!! }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null,
                            tint = Color(0xFF1a73e8), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("..", fontSize = 14.sp, color = Color(0xFF1a73e8), fontWeight = FontWeight.Medium)
                    }
                    Divider()
                }

                // Folder list
                val dirs = remember(currentDir) {
                    currentDir.listFiles()
                        ?.filter { it.isDirectory && !it.isHidden }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(dirs) { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentDir = dir }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null,
                                tint = Color(0xFFFFA000), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(dir.name, fontSize = 14.sp, color = Color(0xFF333333))
                        }
                        Divider(modifier = Modifier.padding(start = 50.dp))
                    }

                    if (dirs.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No sub-folders", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Bottom action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentDir.name,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onFolderSelected(currentDir); onDismiss() }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Select")
                    }
                }
            }
        }
    }
}

// QR code bitmap generator
fun generateQrBitmap(text: String, size: Int = 512): Bitmap? {
    return try {
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (bits[x, y]) GColor.BLACK else GColor.WHITE)
        }
        bmp
    } catch (e: Exception) { null }
}
