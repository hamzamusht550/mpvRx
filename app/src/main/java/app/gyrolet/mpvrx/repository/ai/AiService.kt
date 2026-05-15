package app.gyrolet.mpvrx.repository.ai

import android.util.Log
import app.gyrolet.mpvrx.preferences.AiPreferences
import app.gyrolet.mpvrx.preferences.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AiService(
  private val preferences: AiPreferences,
  private val geminiClient: GeminiClient,
  private val groqClient: GroqClient,
  private val json: Json,
) {
  companion object {
    private const val TAG = "AiService"
  }

  suspend fun fetchModels(): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }

    when (provider) {
      AiProvider.GEMINI -> geminiClient.fetchModels(apiKey)
      AiProvider.GROQ -> groqClient.fetchModels(apiKey)
    }
  }

  suspend fun verifyKey(): Result<String> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }

    when (provider) {
      AiProvider.GEMINI -> geminiClient.verifyKey(apiKey)
      AiProvider.GROQ -> groqClient.verifyKey(apiKey)
    }
  }

  suspend fun generateWithAi(
    userInput: String,
    task: AiTask,
    extraInstruction: String? = null,
  ): Result<String> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)
    val model = preferences.selectedModel.get()

    if (userInput.isBlank()) {
      return@withContext Result.failure(Exception("Empty input provided to AI"))
    }
    val customPromptEnabled = preferences.customPromptEnabled.get()
    val customPrompt = preferences.customPrompt.get()

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }
    if (model.isBlank()) {
      return@withContext Result.failure(Exception("No AI model selected"))
    }

    var instruction = AiPrompts.resolveInstruction(task, customPromptEnabled, customPrompt)
    if (extraInstruction != null) {
      instruction = "$instruction\n\n$extraInstruction"
    }

    when (provider) {
      AiProvider.GEMINI -> geminiClient.generateContent(apiKey, model, instruction, userInput)
      AiProvider.GROQ -> groqClient.generateContent(apiKey, model, instruction, userInput)
    }
  }

  suspend fun renameWithAi(
    currentName: String,
    extension: String?,
  ): Result<String> = withContext(Dispatchers.IO) {
    val result = generateWithAi(currentName, AiTask.RENAME)
    result.map { aiName ->
      val clean = aiName.trim().removeSurrounding("\"").removeSurrounding("'")
      if (extension != null && !clean.endsWith(extension)) {
        "$clean$extension"
      } else clean
    }
  }

  suspend fun formatTitleForSubtitleSearch(
    fileTitle: String,
  ): Result<String> = withContext(Dispatchers.IO) {
    val result = generateWithAi(fileTitle, AiTask.SUBTITLE_FORMAT)
    result.map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
  }

  suspend fun isConfigured(): Boolean {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)
    return preferences.enabled.get() && apiKey.isNotBlank() && preferences.selectedModel.get().isNotBlank()
  }

  private fun getApiKey(provider: AiProvider): String = when (provider) {
    AiProvider.GEMINI -> preferences.geminiApiKey.get()
    AiProvider.GROQ -> preferences.groqApiKey.get()
  }

  /**
   * Translates a subtitle string (SRT format) in chunks to avoid context limits.
   */
  suspend fun translateSubtitle(
    content: String,
    targetLanguage: String,
    onProgress: (Float) -> Unit = {}
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      // Split by empty lines to get subtitle blocks
      // Handle both CRLF and LF
      val blocks = content.split(Regex("(\\r?\\n){2,}")).filter { it.isNotBlank() }
      if (blocks.isEmpty()) return@withContext Result.success(content)

      val chunkSize = 30 // Translate 30 blocks at a time
      val totalChunks = (blocks.size + chunkSize - 1) / chunkSize
      val translatedBlocks = mutableListOf<String>()

      for (i in 0 until totalChunks) {
        val start = i * chunkSize
        val end = minOf(start + chunkSize, blocks.size)
        val chunk = blocks.subList(start, end).joinToString("\n\n")

        val extra = "TARGET LANGUAGE: $targetLanguage"
        val result = generateWithAi(chunk, AiTask.TRANSLATE, extra)
        
        result.onSuccess { translatedChunk ->
          translatedBlocks.add(translatedChunk.trim())
        }.onFailure { error ->
          return@withContext Result.failure(error)
        }

        onProgress((i + 1).toFloat() / totalChunks)
      }

      Result.success(translatedBlocks.joinToString("\n\n"))
    } catch (e: Exception) {
      Log.e(TAG, "Subtitle translation failed", e)
      Result.failure(e)
    }
  }
}
