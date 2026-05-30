package app.gyrolet.mpvrx.ui.player

import android.util.Log
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class ScriptCurlBridge(
    private val scope: CoroutineScope,
) {

    companion object {
        private const val TAG = "ScriptCurlBridge"
        private const val RESPONSE_PROPERTY = "user-data/mpvrx/curl_response"

        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val MAX_TIMEOUT_SECONDS = 120L

        init {
            System.loadLibrary("curl_bridge")
        }

        @JvmStatic
        private external fun nativeExecute(
            url: String,
            method: String,
            headerKeys: Array<String>?,
            headerValues: Array<String>?,
            body: String?,
            contentType: String?,
            timeout: Int,
        ): String
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Serializable
    private data class CurlRequest(
        val id: String? = null,
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val content_type: String = "text/plain; charset=utf-8",
        val timeout: Int = DEFAULT_TIMEOUT_SECONDS.toInt(),
    )

    @Serializable
    private data class CurlResponse(
        val id: String,
        val status: Int,
        val body: String,
        val headers: Map<String, String>,
        val error: String?,
    )

    fun handleRequest(rawJson: String) {
        Log.d(TAG, "Received curl_request: $rawJson")

        val request = try {
            parseRequest(rawJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse request", e)

            writeErrorResponse(
                id = "unknown",
                error = "Invalid request JSON: ${e.message}"
            )
            return
        }

        val requestId =
            request.id ?: UUID.randomUUID().toString()

        val finalRequest = request.copy(
            id = requestId
        )

        if (finalRequest.url.isBlank()) {
            writeErrorResponse(
                id = requestId,
                error = "URL must not be blank"
            )
            return
        }

        val timeoutSec =
            finalRequest.timeout.coerceIn(
                1,
                MAX_TIMEOUT_SECONDS.toInt()
            )

        scope.launch(Dispatchers.IO) {
            val response =
                executeRequest(
                    finalRequest,
                    timeoutSec
                )

            writeResponse(response)
        }
    }

    private fun parseRequest(
        rawJson: String
    ): CurlRequest {
        val current = rawJson.trim()

        // 1. Try to parse directly
        try {
            return json.decodeFromString<CurlRequest>(current)
        } catch (_: Exception) {}

        // 2. If it is wrapped in double quotes, it's a JSON-encoded string.
        // Try to decode it as a string first, then parse.
        if (current.startsWith("\"") && current.endsWith("\"")) {
            try {
                val decoded = json.decodeFromString<String>(current)
                return json.decodeFromString<CurlRequest>(decoded.trim())
            } catch (_: Exception) {}
        }

        // 3. If it starts with '{' but contains escaped quotes (like \" or \\\" or \\\"),
        // it is a raw JSON object string with corrupted/escaped quotes.
        // We can manually sanitize it by replacing all backslash-quote sequences with a single double quote.
        if (current.startsWith("{")) {
            try {
                var sanitized = current
                // Replace any sequence of backslashes followed by a quote with a single double quote
                // e.g. \\\" -> ", \\" -> ", \" -> "
                sanitized = sanitized.replace(Regex("""\\+""""), "\"")
                return json.decodeFromString<CurlRequest>(sanitized)
            } catch (_: Exception) {}
        }

        // 4. Fallback: recursively try to decode as a string if it's still double-encoded
        var temp = current
        for (i in 0 until 5) {
            try {
                val decoded = json.decodeFromString<String>(temp)
                if (decoded == temp) break
                temp = decoded.trim()
                try {
                    return json.decodeFromString<CurlRequest>(temp)
                } catch (_: Exception) {}
            } catch (_: Exception) {
                break
            }
        }

        // Final try with the original string to throw the proper exception if it still fails
        return json.decodeFromString(current)
    }

    private fun executeRequest(
        request: CurlRequest,
        timeoutSec: Int,
    ): CurlResponse {

        val nativeJson = try {

            nativeExecute(
                url = request.url,
                method = request.method.uppercase(),
                headerKeys =
                    if (request.headers.isNotEmpty())
                        request.headers.keys.toTypedArray()
                    else
                        null,
                headerValues =
                    if (request.headers.isNotEmpty())
                        request.headers.values.toTypedArray()
                    else
                        null,
                body = request.body,
                contentType =
                    request.content_type.ifBlank {
                        null
                    },
                timeout = timeoutSec,
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Request failed",
                e
            )

            return CurlResponse(
                id = request.id ?: "unknown",
                status = 0,
                body = "",
                headers = emptyMap(),
                error = e.message
                    ?: "Native curl error",
            )
        }

        return runCatching {

            val obj =
                json.parseToJsonElement(
                    nativeJson
                ).jsonObject

            CurlResponse(
                id = request.id ?: "unknown",
                status =
                    obj["status"]
                        ?.jsonPrimitive
                        ?.content
                        ?.toIntOrNull()
                        ?: 0,
                body =
                    obj["body"]
                        ?.jsonPrimitive
                        ?.content
                        ?: "",
                headers =
                    obj["headers"]
                        ?.jsonObject
                        ?.mapValues { (_, v) ->
                            v.jsonPrimitive.content
                        }
                        ?: emptyMap(),
                error =
                    when (
                        val e = obj["error"]
                    ) {
                        null,
                        is JsonNull -> null

                        else ->
                            e.jsonPrimitive.content
                    },
            )

        }.getOrElse { e ->

            CurlResponse(
                id = request.id ?: "unknown",
                status = 0,
                body = "",
                headers = emptyMap(),
                error =
                    "Failed to parse native response: ${e.message}",
            )
        }
    }

    private fun writeResponse(
        response: CurlResponse
    ) {

        val responseJson =
            json.encodeToString(response)

        MPVLib.setPropertyString(
            RESPONSE_PROPERTY,
            responseJson
        )
    }

    private fun writeErrorResponse(
        id: String,
        error: String,
    ) {

        writeResponse(
            CurlResponse(
                id = id,
                status = 0,
                body = "",
                headers = emptyMap(),
                error = error,
            )
        )
    }
}