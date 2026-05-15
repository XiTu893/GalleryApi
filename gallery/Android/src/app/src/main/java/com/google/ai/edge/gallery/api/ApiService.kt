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
import android.util.Log
import com.google.ai.edge.gallery.api.inference.LiteRtAdapter
import com.google.ai.edge.gallery.api.response.ResponseBuilder
import com.google.ai.edge.gallery.api.router.ApiRouter
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class ApiService(
    private val context: Context,
    port: Int = ApiConfig.DEFAULT_PORT
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "ApiService"
    }

    private val tokenManager = TokenManager(context)
    private val apiConfig = ApiConfig(context)
    private val liteRtAdapter = LiteRtAdapter(context)
    private val router = ApiRouter(context, tokenManager, apiConfig, liteRtAdapter)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method
        val headers = session.headers

        Log.d(TAG, "${method.name} $uri")

        if (method == Method.OPTIONS) {
            return corsPreflightResponse()
        }

        val body = parseBody(session)

        return try {
            val response = router.route(uri, method, headers, body)
            addCorsHeaders(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: ${method.name} $uri", e)
            val errorResponse = ResponseBuilder.serverError("Internal server error: ${e.message}")
            addCorsHeaders(errorResponse)
        }
    }

    private fun parseBody(session: IHTTPSession): String? {
        if (session.method != Method.POST && session.method != Method.PUT && session.method != Method.PATCH) {
            return null
        }

        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength == 0L) {
            return null
        }

        val files = mutableMapOf<String, String>()
        try {
            session.parseBody(files)
        } catch (e: IOException) {
            Log.e(TAG, "Error parsing request body", e)
            return null
        }

        return files["postData"]
    }

    private fun corsPreflightResponse(): Response {
        val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "")
        addCorsHeaders(response)
        return response
    }

    private fun addCorsHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        response.addHeader("Access-Control-Max-Age", "86400")
        return response
    }
}
