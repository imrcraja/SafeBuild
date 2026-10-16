package com.rcempire.safebuild

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.File

class LocalHostService : Service() {
    private var server: NanoHTTPD? = null
    private val prefs by lazy { getSharedPreferences("safebuild_host", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopHosting()
            ACTION_START, null -> startHosting()
        }
        return START_STICKY
    }

    private fun startHosting() {
        if (server != null) return
        val root = File(filesDir, "hosted-site")
        if (!root.exists()) root.mkdirs()
        val http = object : NanoHTTPD("0.0.0.0", PORT) {
            override fun serve(session: IHTTPSession): Response {
                val raw = session.uri.removePrefix("/")
                val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault("")
                val segments = decoded.split("/", "\\\\")
                if (decoded.startsWith("/") || segments.any { it == ".." } || decoded.contains('\\u0000')) {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
                }
                val target = File(root, if (decoded.isBlank()) "index.html" else decoded).canonicalFile
                if (!target.toPath().startsWith(root.canonicalFile.toPath())) {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
                }
                if (!target.isFile) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
                val mime = when (target.extension.lowercase()) {
                    "html", "htm" -> "text/html; charset=utf-8"
                    "css" -> "text/css; charset=utf-8"
                    "js" -> "application/javascript; charset=utf-8"
                    "json" -> "application/json; charset=utf-8"
                    "svg" -> "image/svg+xml"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }
                return newChunkedResponse(Response.Status.OK, mime, target.inputStream())
            }
        }
        try {
            http.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = http
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            startForeground(NOTIFICATION_ID, buildNotification("Local site is running on port $PORT"))
        } catch (_: Exception) {
            server = null
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            stopSelf()
        }
    }

    private fun stopHosting() {
        server?.stop()
        server = null
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SafeBuild local hosting", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeBuild local host")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()

    companion object {
        const val PORT = 8088
        const val ACTION_START = "com.rcempire.safebuild.START_HOST"
        const val ACTION_STOP = "com.rcempire.safebuild.STOP_HOST"
        const val KEY_ENABLED = "host_enabled"
        const val CHANNEL_ID = "safebuild_host"
        const val NOTIFICATION_ID = 9088
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("safebuild_host", Context.MODE_PRIVATE)
        if (prefs.getBoolean(LocalHostService.KEY_ENABLED, false)) {
            val start = Intent(context, LocalHostService::class.java).setAction(LocalHostService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(start) else context.startService(start)
        }
    }
}
