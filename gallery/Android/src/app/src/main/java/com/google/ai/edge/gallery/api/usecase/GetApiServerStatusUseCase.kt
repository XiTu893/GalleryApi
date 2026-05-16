package com.google.ai.edge.gallery.api.usecase

import com.google.ai.edge.gallery.api.ApiServerController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ServerStatus(
    val isRunning: Boolean = false,
    val port: Int = 0,
)

@Singleton
class GetApiServerStatusUseCase @Inject constructor() {
    fun invoke(): ServerStatus {
        return ServerStatus(
            isRunning = ApiServerController.isServerRunning(),
            port = ApiServerController.getActualPort(),
        )
    }

    fun observeStatus(): StateFlow<ServerStatus> = ApiServerController.serverStatusFlow
}
