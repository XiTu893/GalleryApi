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

package com.google.ai.edge.gallery.api.model

/**
 * Represents an API token for authentication.
 */
data class ApiToken(
    val token: String,
    val name: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val isActive: Boolean = true,
    val usageCount: Long = 0
) {
    /**
     * Returns a masked version of the token for display.
     */
    fun mask(): String {
        return if (token.length > 8) {
            "${token.take(4)}...${token.takeLast(4)}"
        } else {
            "***"
        }
    }
    
    /**
     * Checks if the token is expired.
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { System.currentTimeMillis() > it } ?: false
    }
}
