package com.google.ai.edge.gallery.api.usecase

import com.google.ai.edge.gallery.api.ApiConfig
import javax.inject.Inject
import javax.inject.Singleton

data class ApiConfigState(
    val serverPort: Int = ApiConfig.DEFAULT_PORT,
    val isAuthEnabled: Boolean = true,
)

@Singleton
class UpdateApiConfigUseCase @Inject constructor(
    private val apiConfig: ApiConfig,
) {
    fun getConfig(): ApiConfigState {
        return ApiConfigState(
            serverPort = apiConfig.serverPort,
            isAuthEnabled = apiConfig.isAuthEnabled,
        )
    }

    fun updatePort(port: Int) {
        apiConfig.serverPort = port
    }

    fun updateAuthEnabled(enabled: Boolean) {
        apiConfig.isAuthEnabled = enabled
    }
}
