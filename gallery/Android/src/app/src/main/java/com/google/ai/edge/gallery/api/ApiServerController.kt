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

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.ai.edge.gallery.api.usecase.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ApiServerController {
    private const val TAG = "ApiServerController"

    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatusFlow: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    fun startServer(context: Context) {
        if (_serverStatus.value.isRunning) {
            Log.w(TAG, "API server is already running on port ${_serverStatus.value.port}")
            return
        }
        val intent = Intent(context, ApiServerService::class.java)
        context.startForegroundService(intent)
    }

    fun stopServer(context: Context) {
        if (!_serverStatus.value.isRunning) {
            Log.w(TAG, "API server is not running")
            return
        }
        val intent = Intent(context, ApiServerService::class.java)
        context.stopService(intent)
    }

    fun isServerRunning(): Boolean = _serverStatus.value.isRunning

    fun getActualPort(): Int = _serverStatus.value.port

    fun updateState(running: Boolean, port: Int) {
        _serverStatus.value = ServerStatus(isRunning = running, port = port)
        Log.d(TAG, "State updated: running=$running, port=$port")
    }
}
