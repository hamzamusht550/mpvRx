package app.gyrolet.mpvrx.repository.ai

import android.util.Log
import app.gyrolet.mpvrx.preferences.AiPreferences
import app.gyrolet.mpvrx.preferences.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Locale

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
    val customRenamePrompt = preferences.customRenamePrompt.get()
    val customSubtitleTranslationPrompt = preferences.customSubtitleTranslationPrompt.get()
    val customSubtitleFormatPrompt = preferences.customSubtitleFormatPrompt.get()

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }
    if (model.isBlank()) {
      return@withContext Result.failure(Exception("No AI model selected"))
    }

    var instruction = AiPrompts.resolveInstruction(
      task,
      customPromptEnabled,
      customPrompt,
      customRenamePrompt,
      customSubtitleTranslationPrompt,
      customSubtitleFormatPrompt,
    )
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
    subtitleFormat: String? = null,
    onProgress: (Float) -> Unit = {}
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val normalizedContent = content.replace("\r\n", "\n")
      val chunks = when (subtitleFormat?.lowercase(Locale.ROOT)) {
        "srt", "vtt", "sbv" -> normalizedContent
          .split(Regex("\n{2,}"))
          .map(String::trim)
          .filter { it.isNotBlank() }
        else -> normalizedContent
          .lines()
          .chunked(200)
          .map { it.joinToString("\n").trim() }
          .filter { it.isNotBlank() }
      }

      if (chunks.isEmpty()) return@withContext Result.success(content)

      val chunkSize = 30
      val totalChunks = (chunks.size + chunkSize - 1) / chunkSize
      val translatedChunks = mutableListOf<String>()

      for (i in 0 until totalChunks) {
        val start = i * chunkSize
        val end = minOf(start + chunkSize, chunks.size)
        val chunk = chunks.subList(start, end).joinToString("\n\n")

        val extra = buildString {
          append("TARGET LANGUAGE: $targetLanguage")
          append("\n")
          append("OUTPUT FORMAT: keep the exact subtitle format and structure of the original file.")
          subtitleFormat?.let { append("\nSOURCE FORMAT: .$it") }
        }

        val result = generateWithAi(chunk, AiTask.TRANSLATE, extra)
        result.onSuccess { translatedChunk ->
          translatedChunks.add(translatedChunk.trim())
        }.onFailure { error ->
          return@withContext Result.failure(error)
        }

        onProgress((i + 1).toFloat() / totalChunks)
      }

      Result.success(translatedChunks.joinToString("\n\n"))
    } catch (e: Exception) {
      Log.e(TAG, "Subtitle translation failed", e)
      Result.failure(e)
    }
  }
}
