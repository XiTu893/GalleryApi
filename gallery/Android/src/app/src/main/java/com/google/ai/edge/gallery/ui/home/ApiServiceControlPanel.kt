package com.google.ai.edge.gallery.ui.home

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.api.ApiConfig

@Composable
fun ApiServiceControlPanel(modifier: Modifier = Modifier) {
    val viewModel: ApiServiceViewModel = hiltViewModel()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val apiConfig by viewModel.apiConfig.collectAsState()
    val serverAddress by viewModel.serverAddress.collectAsState()
    var serverPort by remember { mutableStateOf(apiConfig.serverPort.toString()) }

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
                    if (serverStatus.isRunning) "运行中" else "已停止",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (serverStatus.isRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.startServer() },
                        enabled = !serverStatus.isRunning
                    ) {
                        Text("启动")
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopServer() },
                        enabled = serverStatus.isRunning
                    ) {
                        Text("停止")
                    }
                }
            }

            if (serverStatus.isRunning && serverStatus.port > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        serverAddress,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.copyAddress() }
                    ) {
                        Text("复制")
                    }
                }

                val configuredPort = apiConfig.serverPort
                if (serverStatus.port != configuredPort) {
                    Text(
                        "\u2139\uFE0F 使用端口 ${serverStatus.port}（请求的端口 $configuredPort 不可用）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!serverStatus.isRunning) {
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        serverPort = filtered
                        filtered.toIntOrNull()?.let { port ->
                            if (port in ApiConfig.MIN_PORT..ApiConfig.MAX_PORT) {
                                viewModel.updatePort(port)
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
                        if (apiConfig.isAuthEnabled) "客户端必须提供有效的 Token"
                        else "无需认证（类似 Ollama）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = apiConfig.isAuthEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateAuthEnabled(enabled)
                    }
                )
            }

            if (!apiConfig.isAuthEnabled) {
                Text(
                    "\u26A0\uFE0F 警告：网络中的任何人都可以在无需认证的情况下访问 API",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
