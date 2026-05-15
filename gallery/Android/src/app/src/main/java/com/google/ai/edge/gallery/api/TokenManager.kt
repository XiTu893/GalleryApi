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
import com.google.ai.edge.gallery.api.model.ApiToken
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Manages API tokens using SharedPreferences for lightweight storage.
 * No database needed - perfect for small number of tokens (< 100).
 */
class TokenManager(private val context: Context) {
    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_NAME = "api_tokens"
        private const val KEY_TOKEN_LIST = "token_list"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Generates a new API token.
     * @param name Human-readable name for the token
     * @param expiresInDays Number of days until expiration (null for no expiry)
     * @return The generated ApiToken
     */
    fun generateToken(name: String, expiresInDays: Int? = null): ApiToken {
        val token = "sk-${UUID.randomUUID().toString().replace("-", "")}"
        val now = System.currentTimeMillis()
        val expiresAt = expiresInDays?.let { days ->
            now + (days * 24 * 60 * 60 * 1000L)
        }

        val apiToken = ApiToken(
            token = token,
            name = name,
            createdAt = now,
            expiresAt = expiresAt,
            isActive = true,
            usageCount = 0
        )

        saveToken(apiToken)
        Log.i(TAG, "Generated new token: ${apiToken.mask()}")
        return apiToken
    }

    /**
     * Validates a token.
     * @return true if token exists, is active, and not expired
     */
    fun validateToken(tokenValue: String): Boolean {
        val token = getToken(tokenValue) ?: return false
        
        return token.isActive && !token.isExpired()
    }

    /**
     * Retrieves a token by its value.
     */
    fun getToken(tokenValue: String): ApiToken? {
        return try {
            val allTokens = getAllTokens()
            allTokens.find { it.token == tokenValue }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting token", e)
            null
        }
    }

    /**
     * Revokes (deactivates) a token.
     */
    fun revokeToken(tokenValue: String): Boolean {
        val token = getToken(tokenValue) ?: return false
        
        val updatedToken = token.copy(isActive = false)
        saveToken(updatedToken)
        
        Log.i(TAG, "Revoked token: ${token.mask()}")
        return true
    }

    /**
     * Lists all tokens (returns masked versions for security).
     */
    fun listTokens(): List<ApiToken> {
        return getAllTokens()
    }

    /**
     * Increments the usage count for a token.
     */
    fun incrementUsage(tokenValue: String) {
        val token = getToken(tokenValue) ?: return
        
        val updatedToken = token.copy(usageCount = token.usageCount + 1)
        saveToken(updatedToken)
    }

    /**
     * Deletes a token completely.
     */
    fun deleteToken(tokenValue: String): Boolean {
        val allTokens = getAllTokens().toMutableList()
        val removed = allTokens.removeIf { it.token == tokenValue }
        
        if (removed) {
            saveAllTokens(allTokens)
            Log.i(TAG, "Deleted token: $tokenValue")
        }
        
        return removed
    }

    // ========== Private Helper Methods ==========

    private fun saveToken(token: ApiToken) {
        val allTokens = getAllTokens().toMutableList()
        
        // Remove existing token with same value (if updating)
        allTokens.removeIf { it.token == token.token }
        
        // Add the new/updated token
        allTokens.add(token)
        
        saveAllTokens(allTokens)
    }

    private fun getAllTokens(): List<ApiToken> {
        return try {
            val json = prefs.getString(KEY_TOKEN_LIST, null)
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<ApiToken>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tokens", e)
            emptyList()
        }
    }

    private fun saveAllTokens(tokens: List<ApiToken>) {
        try {
            val json = gson.toJson(tokens)
            prefs.edit().putString(KEY_TOKEN_LIST, json).apply()
            Log.d(TAG, "Saved ${tokens.size} tokens")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tokens", e)
        }
    }
}
