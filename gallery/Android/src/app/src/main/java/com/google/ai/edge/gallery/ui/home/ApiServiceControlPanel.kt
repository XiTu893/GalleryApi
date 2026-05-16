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

package com.google.ai.edge.gallery.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.api.ApiConfig
import com.google.ai.edge.gallery.api.ApiServerController
import com.google.ai.edge.gallery.api.ApiServerService
import java.net.Inet4Address
import java.net.NetworkInterface

private fun getLocalIpAddress(context: Context): String {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
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

@Composable
fun ApiServiceControlPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apiConfig = remember { ApiConfig(context) }
    var isServerRunning by remember { mutableStateOf(ApiServerController.isServerRunning()) }
    var actualPort by remember { mutableStateOf(ApiServerController.getActualPort()) }
    var serverPort by remember { mutableStateOf(apiConfig.serverPort.toString()) }
    var isAuthEnabled by remember { mutableStateOf(apiConfig.isAuthEnabled) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ApiServerService.ACTION_PORT_UPDATED) {
                    val port = intent.getIntExtra(ApiServerService.EXTRA_PORT, 0)
                    actualPort = port
                    isServerRunning = port > 0
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver, IntentFilter(ApiServerService.ACTION_PORT_UPDATED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver, IntentFilter(ApiServerService.ACTION_PORT_UPDATED)
            )
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    Card(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "API 服务",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isServerRunning) "运行中" else "已停止",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isServerRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            ApiServerController.startServer(context)
                        },
                        enabled = !isServerRunning
                    ) {
                        Text("启动")
                    }
                    OutlinedButton(
                        onClick = {
                            ApiServerController.stopServer(context)
                            isServerRunning = false
                            actualPort = 0
                        },
                        enabled = isServerRunning
                    ) {
                        Text("停止")
                    }
                }
            }

            if (isServerRunning && actualPort > 0) {
                val localIp = remember { getLocalIpAddress(context) }
                val address = "http://$localIp:$actualPort"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        address,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("API Address", address)
                            )
                            Toast.makeText(context, "已复制！", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("复制")
                    }
                }

                val configuredPort = apiConfig.serverPort
                if (actualPort != configuredPort) {
                    Text(
                        "\u2139\uFE0F 使用端口 $actualPort（请求的端口 $configuredPort 不可用）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isServerRunning) {
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        serverPort = filtered
                        filtered.toIntOrNull()?.let { port ->
                            if (port in ApiConfig.MIN_PORT..ApiConfig.MAX_PORT) {
                                apiConfig.serverPort = port
                            }
                        }
                    },
                    label = { Text("服务端口 (${ApiConfig.MIN_PORT}-${ApiConfig.MAX_PORT})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "需要 API Key",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (isAuthEnabled) "客户端必须提供有效的 Token"
                        else "无需认证（类似 Ollama）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAuthEnabled,
                    onCheckedChange = { enabled ->
                        isAuthEnabled = enabled
                        apiConfig.isAuthEnabled = enabled
                    }
                )
            }

            if (!isAuthEnabled) {
                Text(
                    "\u26A0\uFE0F 警告：网络中的任何人都可以在无需认证的情况下访问 API",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
