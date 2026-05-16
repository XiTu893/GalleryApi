package com.google.ai.edge.gallery.api.usecase

import android.content.Context
import android.content.Intent
import com.google.ai.edge.gallery.api.ApiConfig
import com.google.ai.edge.gallery.api.ApiServerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartApiServerUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiConfig: ApiConfig,
) {
    fun invoke() {
        val intent = Intent(context, ApiServerService::class.java)
        context.startForegroundService(intent)
    }
}
