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

package com.google.ai.edge.gallery.api.middleware

import com.google.ai.edge.gallery.api.TokenManager

object AuthMiddleware {
    sealed class AuthResult {
        data class Valid(val token: String) : AuthResult()
        data class Invalid(val message: String) : AuthResult()
    }

    fun validate(
        headers: Map<String, String>,
        tokenManager: TokenManager,
        authEnabled: Boolean
    ): AuthResult {
        if (!authEnabled) {
            return AuthResult.Valid("anonymous")
        }

        val authHeader = headers["authorization"]
            ?: headers["Authorization"]
            ?: return AuthResult.Invalid("Missing Authorization header. Use 'Authorization: Bearer <token>'")

        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) {
            return AuthResult.Invalid("Invalid Authorization format. Expected 'Bearer <token>'")
        }

        val token = authHeader.removePrefix("Bearer ").removePrefix("bearer ").trim()
        if (token.isEmpty()) {
            return AuthResult.Invalid("Empty token in Authorization header")
        }

        return if (tokenManager.validateToken(token)) {
            AuthResult.Valid(token)
        } else {
            AuthResult.Invalid("Invalid or expired API key")
        }
    }
}
