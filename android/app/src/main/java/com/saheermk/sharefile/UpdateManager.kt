package com.saheermk.sharefile

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.FileProvider
import java.io.File

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/saheermk/share-file/releases/latest"

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val downloadUrl: String
    )

    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_RELEASES_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val tagName = json.getString("tag_name").replace("v", "") // handle "v1.0" or "1.0"
                val currentVersion = getAppVersion(context).replace("v", "")

                val assets = json.getJSONArray("assets")
                var downloadUrl = ""

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (isNewerVersion(currentVersion, tagName) && downloadUrl.isNotEmpty()) {
                    return@withContext UpdateInfo(true, tagName, downloadUrl)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
        return@withContext UpdateInfo(false, "", "")
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val c = if (i < currentParts.size) currentParts[i] else 0
            val l = if (i < latestParts.size) latestParts[i] else 0
            if (l > c) return true
            if (c > l) return false
        }
        return false
    }

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String, version: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(downloadUrl)
        
        // Remove previous downloaded apk if exists
        val destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(destination, "share-file-update-$version.apk")
        if (file.exists()) {
            file.delete()
        }

        val request = DownloadManager.Request(uri)
            .setTitle("Share File Update")
            .setDescription("Downloading version $version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "share-file-update-$version.apk")
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(ctxt, downloadManager, downloadId)
                    ctxt.unregisterReceiver(this)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, downloadManager: DownloadManager, downloadId: Long) {
        try {
            val install = Intent(Intent.ACTION_VIEW)
            install.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            
            val uri = downloadManager.getUriForDownloadedFile(downloadId)
            
            if (uri != null) {
                install.setDataAndType(uri, "application/vnd.android.package-archive")
                context.startActivity(install)
            } else {
                Log.e(TAG, "Downloaded file URI is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }
}
