/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R

class ApiServerService : Service() {
    companion object {
        const val ACTION_PORT_UPDATED = "com.google.ai.edge.gallery.api.PORT_UPDATED"
        const val EXTRA_PORT = "extra_port"
        const val NOTIFICATION_CHANNEL_ID = "api_service_channel"
        const val NOTIFICATION_ID = 1001

        private const val TAG = "ApiServerService"
    }

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
        val apiConfig = ApiConfig(this)
        val startPort = apiConfig.serverPort

        try {
            actualPort = startServerWithFallback(startPort)
            Log.i(TAG, "API server started on port $actualPort")
            updateNotification(actualPort)
            broadcastPort(actualPort)
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
                val service = ApiService(applicationContext, currentPort)
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

    private fun broadcastPort(port: Int) {
        val intent = Intent(ACTION_PORT_UPDATED)
        intent.putExtra(EXTRA_PORT, port)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "API Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Local LLM API Server"
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
            .setContentTitle("Edge Gallery API")
            .setContentText("Failed to start - all ports occupied")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(port: Int): Notification {
        val contentText = if (port > 0) {
            "Running on http://127.0.0.1:$port"
        } else {
            "Starting..."
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Edge Gallery API")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
