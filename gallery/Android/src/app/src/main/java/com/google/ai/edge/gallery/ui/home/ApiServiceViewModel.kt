package com.google.ai.edge.gallery.ui.home

import android.content.ClipData
import android.content.Context
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.api.ApiConfig
import com.google.ai.edge.gallery.api.model.ApiToken
import com.google.ai.edge.gallery.api.usecase.GetApiServerStatusUseCase
import com.google.ai.edge.gallery.api.usecase.ManageApiTokensUseCase
import com.google.ai.edge.gallery.api.usecase.ServerStatus
import com.google.ai.edge.gallery.api.usecase.StartApiServerUseCase
import com.google.ai.edge.gallery.api.usecase.StopApiServerUseCase
import com.google.ai.edge.gallery.api.usecase.UpdateApiConfigUseCase
import com.google.ai.edge.gallery.api.usecase.ApiConfigState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@HiltViewModel
class ApiServiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val startApiServerUseCase: StartApiServerUseCase,
    private val stopApiServerUseCase: StopApiServerUseCase,
    private val getApiServerStatusUseCase: GetApiServerStatusUseCase,
    private val updateApiConfigUseCase: UpdateApiConfigUseCase,
    private val manageApiTokensUseCase: ManageApiTokensUseCase,
) : ViewModel() {

    val localIp: String = getLocalIpAddress(context)

    val serverStatus: StateFlow<ServerStatus> =
        getApiServerStatusUseCase.observeStatus()
            .stateIn(viewModelScope, SharingStarted.Eagerly, ServerStatus())

    val serverAddress: StateFlow<String> = serverStatus.map { status ->
        if (status.isRunning && status.port > 0) "http://$localIp:${status.port}" else ""
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _apiConfig = MutableStateFlow(updateApiConfigUseCase.getConfig())
    val apiConfig: StateFlow<ApiConfigState> = _apiConfig.asStateFlow()

    fun startServer() {
        startApiServerUseCase.invoke()
    }

    fun stopServer() {
        stopApiServerUseCase.invoke()
    }

    fun updatePort(port: Int) {
        val clamped = port.coerceIn(ApiConfig.MIN_PORT, ApiConfig.MAX_PORT)
        updateApiConfigUseCase.updatePort(clamped)
        _apiConfig.value = updateApiConfigUseCase.getConfig()
    }

    fun updateAuthEnabled(enabled: Boolean) {
        updateApiConfigUseCase.updateAuthEnabled(enabled)
        _apiConfig.value = updateApiConfigUseCase.getConfig()
    }

    fun copyAddress() {
        val address = serverAddress.value
        if (address.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("API Address", address))
            Toast.makeText(context, "已复制！", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateToken(name: String, expiresInDays: Int? = null): ApiToken {
        return manageApiTokensUseCase.generateToken(name, expiresInDays)
    }

    fun listTokens(): List<ApiToken> {
        return manageApiTokensUseCase.listTokens()
    }

    fun deleteToken(tokenValue: String): Boolean {
        return manageApiTokensUseCase.deleteToken(tokenValue)
    }

    companion object {
        fun getLocalIpAddress(context: Context): String {
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
    }
}
