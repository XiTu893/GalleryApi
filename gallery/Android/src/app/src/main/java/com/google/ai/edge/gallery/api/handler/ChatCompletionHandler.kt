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

import android.util.Log
import com.google.ai.edge.gallery.api.ApiConfig
import com.google.ai.edge.gallery.api.TokenManager
import com.google.ai.edge.gallery.api.inference.LiteRtAdapter
import com.google.ai.edge.gallery.api.middleware.AuthMiddleware
import com.google.ai.edge.gallery.api.model.ChatCompletionRequest
import com.google.ai.edge.gallery.api.response.ResponseBuilder
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatCompletionHandler(
    private val liteRtAdapter: LiteRtAdapter,
    private val tokenManager: TokenManager,
    private val apiConfig: ApiConfig
) {
    companion object {
        private const val TAG = "ChatCompletionHandler"
    }

    private val gson = Gson()

    fun handle(headers: Map<String, String>, body: String?): NanoHTTPD.Response {
        val authResult = AuthMiddleware.validate(headers, tokenManager, apiConfig.isAuthEnabled)
        if (authResult is AuthMiddleware.AuthResult.Invalid) {
            return ResponseBuilder.unauthorized(authResult.message)
        }

        if (body.isNullOrBlank()) {
            return ResponseBuilder.badRequest("Request body is required")
        }

        val request = try {
            gson.fromJson(body, ChatCompletionRequest::class.java)
                ?: return ResponseBuilder.badRequest("Request body cannot be null")
        } catch (e: JsonSyntaxException) {
            return ResponseBuilder.badRequest("Invalid JSON: ${e.message}")
        }

        val validationError = validateRequest(request)
        if (validationError != null) {
            return ResponseBuilder.badRequest(validationError)
        }

        val loadedModels = liteRtAdapter.getLoadedModels()
        if (loadedModels.isEmpty()) {
            return ResponseBuilder.serviceUnavailable(
                "No models are currently loaded. Please load a model in the Gallery app first."
            )
        }

        val matchedModel = liteRtAdapter.findModel(request.model)
        if (matchedModel == null) {
            return ResponseBuilder.badRequest(
                "Model '${request.model}' not found. Available models: ${loadedModels.map { it.name }}"
            )
        }

        return try {
            val response = if (request.stream) {
                handleStream(request, authResult)
            } else {
                handleNonStream(request, authResult)
            }
            response
        } catch (e: LiteRtAdapter.ModelNotFoundException) {
            ResponseBuilder.badRequest(e.message ?: "Model not found")
        } catch (e: LiteRtAdapter.InferenceTimeoutException) {
            ResponseBuilder.serverError("Inference timed out: ${e.message}")
        } catch (e: LiteRtAdapter.InferenceException) {
            ResponseBuilder.serverError("Inference failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during chat completion", e)
            ResponseBuilder.serverError("Internal error: ${e.message}")
        }
    }

    private fun handleNonStream(
        request: ChatCompletionRequest,
        authResult: AuthMiddleware.AuthResult
    ): NanoHTTPD.Response {
        val response = liteRtAdapter.complete(request)

        if (authResult is AuthMiddleware.AuthResult.Valid && authResult.token != "anonymous") {
            tokenManager.incrementUsage(authResult.token)
        }

        return ResponseBuilder.success(response)
    }

    private fun handleStream(
        request: ChatCompletionRequest,
        authResult: AuthMiddleware.AuthResult
    ): NanoHTTPD.Response {
        val pipedInput = PipedInputStream(8192)
        val pipedOutput = PipedOutputStream(pipedInput)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                liteRtAdapter.completeStream(
                    request = request,
                    onChunk = { chunk ->
                        val data = "data: ${gson.toJson(chunk)}\n\n"
                        pipedOutput.write(data.toByteArray())
                        pipedOutput.flush()
                    },
                    onDone = {
                        pipedOutput.write("data: [DONE]\n\n".toByteArray())
                        pipedOutput.flush()
                        try {
                            pipedOutput.close()
                        } catch (e: Exception) {
                            Log.d(TAG, "Stream output already closed")
                        }
                    },
                    onError = { throwable ->
                        val errorData = "data: ${gson.toJson(mapOf("error" to throwable.message))}\n\n"
                        try {
                            pipedOutput.write(errorData.toByteArray())
                            pipedOutput.flush()
                            pipedOutput.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to write error to stream", e)
                        }
                    }
                )

                if (authResult is AuthMiddleware.AuthResult.Valid && authResult.token != "anonymous") {
                    tokenManager.incrementUsage(authResult.token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream coroutine error", e)
                try {
                    pipedOutput.close()
                } catch (_: Exception) {}
            }
        }

        val response = NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK,
            "text/event-stream",
            pipedInput
        )
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        response.addHeader("X-Accel-Buffering", "no")
        return response
    }

    private fun validateRequest(request: ChatCompletionRequest): String? {
        if (request.messages.isEmpty()) {
            return "messages array cannot be empty"
        }
        for (msg in request.messages) {
            if (msg.role !in listOf("system", "user", "assistant")) {
                return "Invalid message role: '${msg.role}'. Must be 'system', 'user', or 'assistant'"
            }
            if (msg.content.isBlank() && msg.role != "assistant") {
                return "Message content cannot be empty for role '${msg.role}'"
            }
        }
        if (request.model.isBlank()) {
            return "model field is required"
        }
        return null
    }
}
