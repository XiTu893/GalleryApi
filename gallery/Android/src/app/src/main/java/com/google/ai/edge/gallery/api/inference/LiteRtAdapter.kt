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

package com.google.ai.edge.gallery.api.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.api.model.ChatCompletionChunk
import com.google.ai.edge.gallery.api.model.ChatCompletionRequest
import com.google.ai.edge.gallery.api.model.ChatCompletionResponse
import com.google.ai.edge.gallery.api.model.ChatMessage
import com.google.ai.edge.gallery.api.model.ChunkChoice
import com.google.ai.edge.gallery.api.model.Choice
import com.google.ai.edge.gallery.api.model.DeltaMessage
import com.google.ai.edge.gallery.api.model.ModelInfo
import com.google.ai.edge.gallery.api.model.ModelListResponse
import com.google.ai.edge.gallery.api.model.Usage
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.ResultListener
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

class LiteRtAdapter(private val context: Context) {
    companion object {
        private const val TAG = "LiteRtAdapter"
        private const val DEFAULT_TIMEOUT_MS = 300_000L
        private const val CHARS_PER_TOKEN = 4
    }

    private val modelRegistry: ModelRegistry = ModelRegistry.getInstance(context)

    fun getLoadedModels(): List<Model> {
        return modelRegistry.getLoadedModels()
    }

    fun getModelListResponse(): ModelListResponse {
        val models = getLoadedModels()
        return ModelListResponse(
            data = models.map { model ->
                ModelInfo(
                    id = model.name,
                    created = System.currentTimeMillis() / 1000,
                    ownedBy = "local"
                )
            }
        )
    }

    fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
        val model = findModel(request.model)
            ?: throw ModelNotFoundException("Model '${request.model}' not found or not loaded. Available models: ${getLoadedModels().map { it.name }}")

        val prompt = buildPrompt(request.messages)
        val promptTokens = estimateTokens(prompt)

        val result = StringBuilder()
        val latch = CountDownLatch(1)
        val errorRef = AtomicReference<String>(null)

        val resultListener: ResultListener = { partialResult, done, _ ->
            if (done) {
                latch.countDown()
            } else {
                result.append(partialResult)
            }
        }

        val cleanUpListener = {
            latch.countDown()
        }

        val onError: (String) -> Unit = { errorMessage ->
            Log.e(TAG, "Inference error: $errorMessage")
            errorRef.set(errorMessage)
            latch.countDown()
        }

        model.runtimeHelper.runInference(
            model = model,
            input = prompt,
            resultListener = resultListener,
            cleanUpListener = cleanUpListener,
            onError = onError,
        )

        val timeout = request.maxTokens?.toLong()?.times(100) ?: DEFAULT_TIMEOUT_MS
        val completed = latch.await(timeout.coerceAtMost(DEFAULT_TIMEOUT_MS), TimeUnit.MILLISECONDS)

        if (!completed) {
            model.runtimeHelper.stopResponse(model)
            throw InferenceTimeoutException("Inference timed out after ${timeout}ms")
        }

        val error = errorRef.get()
        if (error != null) {
            throw InferenceException(error)
        }

        val responseText = result.toString()
        val completionTokens = estimateTokens(responseText)

        return ChatCompletionResponse(
            id = "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(24)}",
            created = System.currentTimeMillis() / 1000,
            model = model.name,
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage(role = "assistant", content = responseText),
                    finishReason = "stop"
                )
            ),
            usage = Usage(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = promptTokens + completionTokens
            )
        )
    }

    fun completeStream(
        request: ChatCompletionRequest,
        onChunk: (ChatCompletionChunk) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val model = try {
            findModel(request.model)
                ?: throw ModelNotFoundException("Model '${request.model}' not found or not loaded")
        } catch (e: ModelNotFoundException) {
            onError(e)
            return
        }

        val prompt = buildPrompt(request.messages)
        val promptTokens = estimateTokens(prompt)
        val completionId = "chatcmpl-${UUID.randomUUID().toString().replace("-", "").take(24)}"
        val created = System.currentTimeMillis() / 1000

        onChunk(
            ChatCompletionChunk(
                id = completionId,
                created = created,
                model = model.name,
                choices = listOf(
                    ChunkChoice(
                        index = 0,
                        delta = DeltaMessage(role = "assistant", content = null),
                        finishReason = null
                    )
                )
            )
        )

        var totalContent = StringBuilder()
        val resultListener: ResultListener = { partialResult, done, _ ->
            if (done) {
                val completionTokens = estimateTokens(totalContent.toString())
                onChunk(
                    ChatCompletionChunk(
                        id = completionId,
                        created = created,
                        model = model.name,
                        choices = listOf(
                            ChunkChoice(
                                index = 0,
                                delta = DeltaMessage(role = null, content = null),
                                finishReason = "stop"
                            )
                        )
                    )
                )
                onDone()
            } else {
                totalContent.append(partialResult)
                onChunk(
                    ChatCompletionChunk(
                        id = completionId,
                        created = created,
                        model = model.name,
                        choices = listOf(
                            ChunkChoice(
                                index = 0,
                                delta = DeltaMessage(role = null, content = partialResult),
                                finishReason = null
                            )
                        )
                    )
                )
            }
        }

        val cleanUpListener = {
            onDone()
        }

        val errorCb: (String) -> Unit = { errorMessage ->
            Log.e(TAG, "Stream inference error: $errorMessage")
            onError(InferenceException(errorMessage))
        }

        model.runtimeHelper.runInference(
            model = model,
            input = prompt,
            resultListener = resultListener,
            cleanUpListener = cleanUpListener,
            onError = errorCb,
        )
    }

    fun findModel(modelName: String): Model? {
        val loadedModels = getLoadedModels()
        val exactMatch = loadedModels.find { it.name == modelName }
        if (exactMatch != null) return exactMatch

        val displayMatch = loadedModels.find { it.displayName == modelName }
        if (displayMatch != null) return displayMatch

        val normalizedQuery = modelName.lowercase().replace("[^a-z0-9]".toRegex(), "")
        val fuzzyMatch = loadedModels.find {
            it.name.lowercase().replace("[^a-z0-9]".toRegex(), "").contains(normalizedQuery)
        }
        if (fuzzyMatch != null) return fuzzyMatch

        return loadedModels.firstOrNull()
    }

    private fun buildPrompt(messages: List<ChatMessage>): String {
        if (messages.size == 1 && messages[0].role == "user") {
            return messages[0].content
        }

        val sb = StringBuilder()
        for (msg in messages) {
            when (msg.role) {
                "system" -> {
                    sb.appendLine("System: ${msg.content}")
                }
                "user" -> {
                    sb.appendLine("User: ${msg.content}")
                }
                "assistant" -> {
                    sb.appendLine("Assistant: ${msg.content}")
                }
                else -> {
                    sb.appendLine("${msg.role}: ${msg.content}")
                }
            }
        }
        sb.appendLine("Assistant:")
        return sb.toString().trimEnd()
    }

    private fun estimateTokens(text: String): Int {
        return ceil(text.length.toDouble() / CHARS_PER_TOKEN).toInt()
    }

    class ModelNotFoundException(message: String) : Exception(message)
    class InferenceTimeoutException(message: String) : Exception(message)
    class InferenceException(message: String) : Exception(message)
}
