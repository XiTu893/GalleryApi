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
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance

class ModelRegistry private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ModelRegistry"

        @Volatile
        private var instance: ModelRegistry? = null

        fun getInstance(context: Context): ModelRegistry {
            return instance ?: synchronized(this) {
                instance ?: ModelRegistry(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _models = mutableListOf<Model>()
    private val _lock = Any()

    fun registerModel(model: Model) {
        synchronized(_lock) {
            _models.removeIf { it.name == model.name }
            _models.add(model)
            Log.d(TAG, "Registered model: ${model.name}, instance=${model.instance != null}")
        }
    }

    fun unregisterModel(modelName: String) {
        synchronized(_lock) {
            _models.removeIf { it.name == modelName }
            Log.d(TAG, "Unregistered model: $modelName")
        }
    }

    fun updateModels(models: List<Model>) {
        synchronized(_lock) {
            _models.clear()
            _models.addAll(models)
            Log.d(TAG, "Updated models: ${models.map { "${it.name}(loaded=${it.instance != null})" }}")
        }
    }

    fun getLoadedModels(): List<Model> {
        synchronized(_lock) {
            return _models.filter { isModelLoaded(it) }.toList()
        }
    }

    fun getAllModels(): List<Model> {
        synchronized(_lock) {
            return _models.toList()
        }
    }

    fun findModel(modelName: String): Model? {
        synchronized(_lock) {
            return _models.find { it.name == modelName }
        }
    }

    private fun isModelLoaded(model: Model): Boolean {
        val instance = model.instance ?: return false
        return when (instance) {
            is LlmModelInstance -> true
            else -> instance != null
        }
    }
}
