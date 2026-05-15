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

package com.google.ai.edge.gallery.api.response

import com.google.ai.edge.gallery.api.model.ErrorDetail
import com.google.ai.edge.gallery.api.model.OpenAiError
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse

object ResponseBuilder {
    private val gson = Gson()

    fun success(data: Any, status: NanoHTTPD.Response.Status = NanoHTTPD.Response.Status.OK): Response {
        return newFixedLengthResponse(status, "application/json", gson.toJson(data))
    }

    fun error(
        message: String,
        code: Int,
        type: String = "invalid_request_error"
    ): Response {
        val status = when (code) {
            400 -> NanoHTTPD.Response.Status.BAD_REQUEST
            401 -> NanoHTTPD.Response.Status.UNAUTHORIZED
            403 -> NanoHTTPD.Response.Status.FORBIDDEN
            404 -> NanoHTTPD.Response.Status.NOT_FOUND
            429 -> NanoHTTPD.Response.Status.BAD_REQUEST
            500 -> NanoHTTPD.Response.Status.INTERNAL_ERROR
            503 -> NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE
            else -> NanoHTTPD.Response.Status.INTERNAL_ERROR
        }
        val errorResponse = OpenAiError(
            error = ErrorDetail(message = message, type = type, code = code.toString())
        )
        val response = newFixedLengthResponse(status, "application/json", gson.toJson(errorResponse))
        return response
    }

    fun unauthorized(message: String = "Invalid or missing API key"): Response {
        return error(message, 401, "authentication_error")
    }

    fun notFound(message: String = "Not found"): Response {
        return error(message, 404)
    }

    fun badRequest(message: String): Response {
        return error(message, 400)
    }

    fun serverError(message: String): Response {
        return error(message, 500, "server_error")
    }

    fun serviceUnavailable(message: String): Response {
        return error(message, 503, "server_error")
    }

    fun streamChunk(data: String): Response {
        val response = newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "text/event-stream",
            data
        )
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        response.addHeader("X-Accel-Buffering", "no")
        return response
    }
}
