package app.gyrolet.mpvrx.repository.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class OcModel(
  val id: String,
  val owned_by: String? = null,
  val pricing: JsonObject? = null,
)

@Serializable
private data class OcModelListResponse(
  val data: List<OcModel> = emptyList(),
)

@Serializable
private data class OcMessage(
  val role: String,
  val content: String,
)

@Serializable
private data class OcChoice(
  val message: OcMessage? = null,
)

@Serializable
private data class OcResponse(
  val choices: List<OcChoice>? = null,
)

@Serializable
private data class OcErrorBody(val error: OcErrorDetail? = null)

@Serializable
private data class OcErrorDetail(val message: String? = null)

@Serializable
private data class OcChatRequest(
  val model: String,
  val messages: List<OcMessage>,
  val temperature: Double = 0.3,
  @SerialName("max_tokens") val maxTokens: Int = 200,
)

class OpenCodeClient(
  private val client: OkHttpClient,
  private val json: Json,
) : AiClient {
  companion object {
    private const val TAG = "OpenCodeClient"
    private const val BASE_URL = "https://opencode.ai/zen/v1"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
  }

  private val apiClient: OkHttpClient =
    client.newBuilder()
      .connectTimeout(60, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()

  override suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$BASE_URL/models")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) throw Exception("OpenCode API error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<OcModelListResponse>(body)
      parsed.data.map { model ->
        val displayName = if (model.owned_by != null) "${model.id} (${model.owned_by})" else model.id
        AiModelInfo(
          id = model.id,
          displayName = displayName,
          isFree = AiModelPricing.isZeroCost(model.pricing) ||
            model.id.endsWith("-free", ignoreCase = true),
        )
      }
    }
  }

  override suspend fun verifyKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$BASE_URL/models")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      if (!response.isSuccessful) throw Exception("Invalid API key: ${response.code}")
      "API key verified successfully"
    }
  }

  override suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions,
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val requestBody = json.encodeToString(
        OcChatRequest.serializer(),
        OcChatRequest(
          model = model,
          messages = listOf(
            OcMessage(role = "system", content = instruction),
            OcMessage(role = "user", content = userInput),
          ),
          temperature = options.temperature,
          maxTokens = options.maxTokens,
        ),
      )

      val request = Request.Builder()
        .url("$BASE_URL/chat/completions")
        .header("Authorization", "Bearer $apiKey")
        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) throw Exception("OpenCode generate error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<OcResponse>(body)
      parsed.choices?.firstOrNull()?.message?.content?.trim()
        ?: throw Exception("No response from OpenCode")
    }
  }

  private fun parseError(body: String): String = try {
    val error = json.decodeFromString<OcErrorBody>(body)
    error.error?.message ?: body
  } catch (_: Exception) {
    body.take(200)
  }
}
