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

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("top_p") val topP: Float? = null,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String? = "stop"
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

data class ChatCompletionChunk(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>
)

data class ChunkChoice(
    val index: Int,
    val delta: DeltaMessage,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class DeltaMessage(
    val role: String? = null,
    val content: String? = null
)

data class OpenAiError(
    val error: ErrorDetail
)

data class ErrorDetail(
    val message: String,
    val type: String = "invalid_request_error",
    val code: String? = null
)

data class ModelListResponse(
    val `object`: String = "list",
    val data: List<ModelInfo>
)

data class ModelInfo(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    @SerializedName("owned_by") val ownedBy: String = "local"
)

data class GenerateTokenRequest(
    val name: String,
    @SerializedName("expires_in_days") val expiresInDays: Int? = null
)

data class GenerateTokenResponse(
    val token: String,
    val name: String,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("expires_at") val expiresAt: Long?
)

data class TokenListResponse(
    val data: List<TokenInfo>
)

data class TokenInfo(
    val name: String,
    val token: String,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("expires_at") val expiresAt: Long?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("usage_count") val usageCount: Long
)
