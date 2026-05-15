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

package com.google.ai.edge.gallery.api.router

import android.content.Context
import com.google.ai.edge.gallery.api.ApiConfig
import com.google.ai.edge.gallery.api.TokenManager
import com.google.ai.edge.gallery.api.handler.ChatCompletionHandler
import com.google.ai.edge.gallery.api.handler.TokenHandler
import com.google.ai.edge.gallery.api.inference.LiteRtAdapter
import com.google.ai.edge.gallery.api.middleware.AuthMiddleware
import com.google.ai.edge.gallery.api.response.ResponseBuilder
import fi.iki.elonen.NanoHTTPD

class ApiRouter(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val apiConfig: ApiConfig,
    private val liteRtAdapter: LiteRtAdapter
) {
    private val chatHandler = ChatCompletionHandler(liteRtAdapter, tokenManager, apiConfig)
    private val tokenHandler = TokenHandler(tokenManager, apiConfig)

    fun route(
        uri: String,
        method: NanoHTTPD.Method,
        headers: Map<String, String>,
        body: String?
    ): NanoHTTPD.Response {
        val cleanUri = uri.substringBefore("?")

        return when {
            cleanUri == "/health" && method == NanoHTTPD.Method.GET ->
                ResponseBuilder.success(mapOf("status" to "ok", "timestamp" to System.currentTimeMillis()))

            cleanUri == "/v1/models" && method == NanoHTTPD.Method.GET ->
                handleModelsList(headers)

            cleanUri == "/v1/chat/completions" && method == NanoHTTPD.Method.POST ->
                chatHandler.handle(headers, body)

            cleanUri == "/v1/tokens" && method == NanoHTTPD.Method.POST ->
                tokenHandler.handleGenerate(headers, body)

            cleanUri == "/v1/tokens" && method == NanoHTTPD.Method.GET ->
                tokenHandler.handleList(headers)

            cleanUri.startsWith("/v1/tokens/") && method == NanoHTTPD.Method.DELETE ->
                tokenHandler.handleDelete(headers, cleanUri)

            else ->
                ResponseBuilder.notFound("Endpoint $cleanUri not found")
        }
    }

    private fun handleModelsList(headers: Map<String, String>): NanoHTTPD.Response {
        val authResult = AuthMiddleware.validate(headers, tokenManager, apiConfig.isAuthEnabled)
        if (authResult is AuthMiddleware.AuthResult.Invalid) {
            return ResponseBuilder.unauthorized(authResult.message)
        }

        val response = liteRtAdapter.getModelListResponse()
        return ResponseBuilder.success(response)
    }
}
