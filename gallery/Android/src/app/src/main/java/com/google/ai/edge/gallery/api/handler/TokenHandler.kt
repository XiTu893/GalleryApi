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

package com.google.ai.edge.gallery.api.handler

import com.google.ai.edge.gallery.api.ApiConfig
import com.google.ai.edge.gallery.api.TokenManager
import com.google.ai.edge.gallery.api.middleware.AuthMiddleware
import com.google.ai.edge.gallery.api.model.GenerateTokenRequest
import com.google.ai.edge.gallery.api.model.GenerateTokenResponse
import com.google.ai.edge.gallery.api.model.TokenInfo
import com.google.ai.edge.gallery.api.model.TokenListResponse
import com.google.ai.edge.gallery.api.response.ResponseBuilder
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import fi.iki.elonen.NanoHTTPD

class TokenHandler(
    private val tokenManager: TokenManager,
    private val apiConfig: ApiConfig
) {
    private val gson = Gson()

    fun handleGenerate(headers: Map<String, String>, body: String?): NanoHTTPD.Response {
        val authResult = AuthMiddleware.validate(headers, tokenManager, authEnabled = true)
        if (authResult is AuthMiddleware.AuthResult.Invalid) {
            return ResponseBuilder.unauthorized(authResult.message)
        }

        if (body.isNullOrBlank()) {
            return ResponseBuilder.badRequest("Request body is required")
        }

        val request = try {
            gson.fromJson(body, GenerateTokenRequest::class.java)
        } catch (e: JsonSyntaxException) {
            return ResponseBuilder.badRequest("Invalid JSON: ${e.message}")
        }

        if (request.name.isBlank()) {
            return ResponseBuilder.badRequest("Token name is required")
        }

        val apiToken = tokenManager.generateToken(request.name, request.expiresInDays)

        return ResponseBuilder.success(
            GenerateTokenResponse(
                token = apiToken.token,
                name = apiToken.name,
                createdAt = apiToken.createdAt,
                expiresAt = apiToken.expiresAt
            )
        )
    }

    fun handleList(headers: Map<String, String>): NanoHTTPD.Response {
        val authResult = AuthMiddleware.validate(headers, tokenManager, authEnabled = true)
        if (authResult is AuthMiddleware.AuthResult.Invalid) {
            return ResponseBuilder.unauthorized(authResult.message)
        }

        val tokens = tokenManager.listTokens().map { token ->
            TokenInfo(
                name = token.name,
                token = token.mask(),
                createdAt = token.createdAt,
                expiresAt = token.expiresAt,
                isActive = token.isActive,
                usageCount = token.usageCount
            )
        }

        return ResponseBuilder.success(TokenListResponse(data = tokens))
    }

    fun handleDelete(headers: Map<String, String>, uri: String): NanoHTTPD.Response {
        val authResult = AuthMiddleware.validate(headers, tokenManager, authEnabled = true)
        if (authResult is AuthMiddleware.AuthResult.Invalid) {
            return ResponseBuilder.unauthorized(authResult.message)
        }

        val tokenValue = uri.removePrefix("/v1/tokens/").trim()
        if (tokenValue.isEmpty()) {
            return ResponseBuilder.badRequest("Token value is required")
        }

        val deleted = tokenManager.deleteToken(tokenValue)
        return if (deleted) {
            ResponseBuilder.success(mapOf("message" to "Token deleted successfully"))
        } else {
            ResponseBuilder.notFound("Token not found")
        }
    }
}
