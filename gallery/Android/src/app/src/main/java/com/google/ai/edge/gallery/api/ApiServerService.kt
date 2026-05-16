package com.google.ai.edge.gallery.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.api.inference.LiteRtAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ApiServerService : Service() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "api_service_channel"
        const val NOTIFICATION_ID = 1001

        private const val TAG = "ApiServerService"
    }

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var apiConfig: ApiConfig
    @Inject lateinit var liteRtAdapter: LiteRtAdapter

    private var apiService: ApiService? = null
    private var actualPort: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ApiServerService onCreate")
        createNotificationChannel()
        startForegroundNotification()
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ApiServerService onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "ApiServerService onDestroy")
        stopServer()
        ApiServerController.updateState(false, 0)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServer() {
        val startPort = apiConfig.serverPort

        try {
            actualPort = startServerWithFallback(startPort)
            Log.i(TAG, "API server started on port $actualPort")
            updateNotification(actualPort)
            ApiServerController.updateState(true, actualPort)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start API server", e)
            updateNotificationError()
            ApiServerController.updateState(false, 0)
        }
    }

    private fun startServerWithFallback(startPort: Int): Int {
        var currentPort = startPort

        while (currentPort <= ApiConfig.MAX_PORT) {
            try {
                val service = ApiService(tokenManager, apiConfig, liteRtAdapter, currentPort)
                service.start()
                apiService = service
                return currentPort
            } catch (e: Exception) {
                Log.w(TAG, "Port $currentPort unavailable, trying ${currentPort + 1}")
                currentPort++
            }
        }

        throw RuntimeException("All ports from $startPort to ${ApiConfig.MAX_PORT} are occupied")
    }

    private fun stopServer() {
        apiService?.let {
            try {
                it.stop()
                Log.i(TAG, "API server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping API server", e)
            }
            apiService = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "API 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "本地大模型 API 服务器"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification(0)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(port: Int) {
        val notification = buildNotification(port)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationError() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gallery API 服务")
            .setContentText("启动失败 - 所有端口被占用")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(port: Int): Notification {
        val ip = getLocalIpAddress()
        val contentText = if (port > 0) {
            "运行中 http://$ip:$port"
        } else {
            "正在启动..."
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gallery API 服务")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    wifiIp and 0xff,
                    wifiIp shr 8 and 0xff,
                    wifiIp shr 16 and 0xff,
                    wifiIp shr 24 and 0xff
                )
            }
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}
