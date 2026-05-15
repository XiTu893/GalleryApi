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
import android.content.SharedPreferences
import android.util.Log

class ApiConfig(private val context: Context) {
    companion object {
        private const val TAG = "ApiConfig"
        private const val PREFS_NAME = "api_config"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_AUTH_ENABLED = "auth_enabled"
        const val DEFAULT_PORT = 8080
        const val MIN_PORT = 8080
        const val MAX_PORT = 8099
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverPort: Int
        get() {
            val port = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
            return port.coerceIn(MIN_PORT, MAX_PORT)
        }
        set(value) {
            val clamped = value.coerceIn(MIN_PORT, MAX_PORT)
            prefs.edit().putInt(KEY_SERVER_PORT, clamped).apply()
            Log.d(TAG, "Server port set to $clamped")
        }

    var isAuthEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTH_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTH_ENABLED, value).apply()
            Log.d(TAG, "Auth enabled set to $value")
        }

    fun getPortRange(): Pair<Int, Int> = Pair(MIN_PORT, MAX_PORT)
}
