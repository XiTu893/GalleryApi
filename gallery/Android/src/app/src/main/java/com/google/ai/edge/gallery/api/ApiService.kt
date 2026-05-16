package com.google.ai.edge.gallery.api

import android.util.Log
import com.google.ai.edge.gallery.api.inference.LiteRtAdapter
import com.google.ai.edge.gallery.api.response.ResponseBuilder
import com.google.ai.edge.gallery.api.router.ApiRouter
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import javax.inject.Inject

class ApiService @Inject constructor(
    private val tokenManager: TokenManager,
    private val apiConfig: ApiConfig,
    private val liteRtAdapter: LiteRtAdapter,
) : NanoHTTPD(apiConfig.serverPort) {

    companion object {
        private const val TAG = "ApiService"
    }

    private val router = ApiRouter(tokenManager, apiConfig, liteRtAdapter)

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
        } catch (e: NanoHTTPD.ResponseException) {
            Log.e(TAG, "Response exception parsing body", e)
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
