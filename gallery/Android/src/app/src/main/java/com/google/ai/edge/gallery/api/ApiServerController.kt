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

object ApiServerController {
    private const val TAG = "ApiServerController"

    @Volatile
    private var isRunning: Boolean = false

    @Volatile
    private var actualPort: Int = 0

    fun startServer(context: Context) {
        if (isRunning) {
            Log.w(TAG, "API server is already running on port $actualPort")
            return
        }
        val intent = Intent(context, ApiServerService::class.java)
        context.startForegroundService(intent)
    }

    fun stopServer(context: Context) {
        if (!isRunning) {
            Log.w(TAG, "API server is not running")
            return
        }
        val intent = Intent(context, ApiServerService::class.java)
        context.stopService(intent)
    }

    fun isServerRunning(): Boolean = isRunning

    fun getActualPort(): Int = actualPort

    fun getServerAddress(): String {
        return if (isRunning && actualPort > 0) {
            "http://127.0.0.1:$actualPort"
        } else {
            ""
        }
    }

    fun updateState(running: Boolean, port: Int) {
        isRunning = running
        actualPort = port
        Log.d(TAG, "State updated: running=$running, port=$port")
    }
}
