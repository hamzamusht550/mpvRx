package app.gyrolet.mpvrx.preferences

import app.gyrolet.mpvrx.preferences.preference.PreferenceStore
import app.gyrolet.mpvrx.preferences.preference.getEnum

enum class AiProvider(val displayName: String) {
  GEMINI("Gemini"),
  GROQ("Groq"),
}

class AiPreferences(
  preferenceStore: PreferenceStore,
) {
  val enabled = preferenceStore.getBoolean("ai_enabled", false)

  val provider = preferenceStore.getEnum("ai_provider", AiProvider.GEMINI)

  val geminiApiKey = preferenceStore.getString("ai_gemini_api_key", "")
  val groqApiKey = preferenceStore.getString("ai_groq_api_key", "")

  val selectedModel = preferenceStore.getString("ai_selected_model", "")

  val availableModels = preferenceStore.getString("ai_available_models", "[]")

  val customPromptEnabled = preferenceStore.getBoolean("ai_custom_prompt_enabled", false)
  val customPrompt = preferenceStore.getString("ai_custom_prompt", "")
  val customRenamePrompt = preferenceStore.getString("ai_custom_rename_prompt", "")
  val customSubtitleTranslationPrompt = preferenceStore.getString("ai_custom_subtitle_translation_prompt", "")
  val customSubtitleFormatPrompt = preferenceStore.getString("ai_custom_subtitle_format_prompt", "")

  val renameWithAi = preferenceStore.getBoolean("ai_rename_enabled", true)
  val subtitleFormatWithAi = preferenceStore.getBoolean("ai_subtitle_format_enabled", true)
  val subtitleTranslationEnabled = preferenceStore.getBoolean("ai_subtitle_translation_enabled", false)
  val subtitleTranslationFirstTime = preferenceStore.getBoolean("ai_subtitle_translation_first_time", true)

  val lastVerified = preferenceStore.getLong("ai_last_verified", 0L)
}
