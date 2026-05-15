package app.gyrolet.mpvrx.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import app.gyrolet.mpvrx.ui.icons.Icon
import me.zhanghai.compose.preference.TextFieldPreference
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.preferences.AiPreferences
import app.gyrolet.mpvrx.preferences.AiProvider
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.repository.ai.AiModelInfo
import app.gyrolet.mpvrx.repository.ai.AiService
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object AiIntegrationScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<AiPreferences>()
    val aiService = koinInject<AiService>()
    val scope = rememberCoroutineScope()

    val enabled by preferences.enabled.collectAsState()
    val provider by preferences.provider.collectAsState()
    val geminiKey by preferences.geminiApiKey.collectAsState()
    val groqKey by preferences.groqApiKey.collectAsState()
    val selectedModel by preferences.selectedModel.collectAsState()
    val customPromptEnabled by preferences.customPromptEnabled.collectAsState()
    val customPrompt by preferences.customPrompt.collectAsState()
    val customRenamePrompt by preferences.customRenamePrompt.collectAsState()
    val customSubtitleTranslationPrompt by preferences.customSubtitleTranslationPrompt.collectAsState()
    val customSubtitleFormatPrompt by preferences.customSubtitleFormatPrompt.collectAsState()
    val renameWithAi by preferences.renameWithAi.collectAsState()
    val subtitleFormatWithAi by preferences.subtitleFormatWithAi.collectAsState()
    val subtitleTranslationEnabled by preferences.subtitleTranslationEnabled.collectAsState()
    val subtitleTranslationFirstTime by preferences.subtitleTranslationFirstTime.collectAsState()

    var models by remember { mutableStateOf<List<AiModelInfo>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<String?>(null) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }
    var showSubtitleTranslationWarning by remember { mutableStateOf(false) }

    val json = koinInject<Json>()

    fun loadModels() {
      scope.launch {
        isLoadingModels = true
        modelLoadError = null
        aiService.fetchModels()
          .onSuccess { fetchedModels ->
            models = fetchedModels
            preferences.availableModels.set(json.encodeToString(
              kotlinx.serialization.builtins.ListSerializer(AiModelInfo.serializer()),
              fetchedModels,
            ))
          }
          .onFailure { e ->
            modelLoadError = e.message
          }
        isLoadingModels = false
      }
    }

    LaunchedEffect(provider) {
      val stored = preferences.availableModels.get()
      if (stored.isNotBlank() && stored != "[]") {
        try {
          models = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AiModelInfo.serializer()),
            stored,
          )
        } catch (_: Exception) {}
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "AI Integration",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backstack.popSafely() }) {
              Icon(
                Icons.Default.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
          item { PreferenceSectionHeader(title = "AI Features") }

          item {
            PreferenceCard {
              SwitchPreference(
                value = enabled,
                onValueChange = { preferences.enabled.set(it) },
                title = { Text("Enable AI Features") },
                summary = {
                  Text(
                    if (enabled) "AI features are active" else "AI features are disabled",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }

          if (enabled) {
            item { PreferenceSectionHeader(title = "Provider") }

            item {
              PreferenceCard {
                val providers = AiProvider.values().toList()
                ListPreference(
                  value = provider,
                  onValueChange = {
                    preferences.provider.set(it)
                    preferences.selectedModel.set("")
                  },
                  values = providers,
                  valueToText = { androidx.compose.ui.text.AnnotatedString(it.displayName) },
                  title = { Text("AI Provider") },
                  summary = {
                    Text(provider.displayName, color = MaterialTheme.colorScheme.outline)
                  },
                )
              }
            }

            item { PreferenceSectionHeader(title = "API Configuration") }

            item {
              PreferenceCard {
                if (provider == AiProvider.GEMINI) {
                  TextFieldPreference(
                    value = geminiKey,
                    onValueChange = preferences.geminiApiKey::set,
                    textToValue = { it.trim() },
                    title = { Text("Gemini API Key") },
                    summary = {
                      if (geminiKey.isBlank()) {
                        Text("Get your key from aistudio.google.com", color = MaterialTheme.colorScheme.error)
                      } else {
                        Text("API key saved on device", color = MaterialTheme.colorScheme.outline)
                      }
                    },
                    textField = { value, onValueChange, _ ->
                      Column {
                        Text("Paste your Gemini API key")
                        TextField(
                          value = value,
                          onValueChange = onValueChange,
                          modifier = Modifier.fillMaxWidth(),
                          placeholder = { Text("AIza...") },
                          singleLine = true,
                          visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        )
                      }
                    },
                  )
                } else {
                  TextFieldPreference(
                    value = groqKey,
                    onValueChange = preferences.groqApiKey::set,
                    textToValue = { it.trim() },
                    title = { Text("Groq API Key") },
                    summary = {
                      if (groqKey.isBlank()) {
                        Text("Get your key from console.groq.com", color = MaterialTheme.colorScheme.error)
                      } else {
                        Text("API key saved on device", color = MaterialTheme.colorScheme.outline)
                      }
                    },
                    textField = { value, onValueChange, _ ->
                      Column {
                        Text("Paste your Groq API key")
                        TextField(
                          value = value,
                          onValueChange = onValueChange,
                          modifier = Modifier.fillMaxWidth(),
                          placeholder = { Text("gsk_...") },
                          singleLine = true,
                          visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        )
                      }
                    },
                  )
                }

                PreferenceDivider()

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Button(
                    onClick = {
                      showApiKey = !showApiKey
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                  ) {
                    Text(if (showApiKey) "Hide Key" else "Show Key")
                  }

                  Button(
                    onClick = {
                      scope.launch {
                        isVerifying = true
                        verifyResult = null
                        aiService.verifyKey()
                          .onSuccess {
                            verifyResult = it
                            preferences.lastVerified.set(System.currentTimeMillis())
                          }
                          .onFailure { e ->
                            verifyResult = "Verification failed: ${e.message}"
                          }
                        isVerifying = false
                      }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isVerifying && (
                      (provider == AiProvider.GEMINI && geminiKey.isNotBlank()) ||
                        (provider == AiProvider.GROQ && groqKey.isNotBlank())
                      ),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                  ) {
                    if (isVerifying) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                      )
                    } else {
                      Text("Verify Key")
                    }
                  }
                }

                if (verifyResult != null) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    val isSuccess = verifyResult!!.contains("successfully")
                    Icon(
                      imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Warning,
                      contentDescription = null,
                      tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                      text = verifyResult!!,
                      style = MaterialTheme.typography.bodySmall,
                      color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }

            item { PreferenceSectionHeader(title = "Model") }

            item {
              PreferenceCard {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Available Models",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                  )
                  Button(
                    onClick = { loadModels() },
                    enabled = !isLoadingModels && (
                      (provider == AiProvider.GEMINI && geminiKey.isNotBlank()) ||
                        (provider == AiProvider.GROQ && groqKey.isNotBlank())
                      ),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                  ) {
                    if (isLoadingModels) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp),
                      )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Fetch Models")
                  }
                }

                if (modelLoadError != null) {
                  Text(
                    text = modelLoadError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                  )
                }

                if (models.isNotEmpty()) {
                  val modelIds = models.map { it.id }
                  val modelDisplayNames = models.associate { it.id to it.displayName }

                  ListPreference(
                    value = selectedModel,
                    onValueChange = { preferences.selectedModel.set(it) },
                    values = modelIds,
                    valueToText = { androidx.compose.ui.text.AnnotatedString(modelDisplayNames[it] ?: it) },
                    title = { Text("Select Model") },
                    summary = {
                      Text(
                        if (selectedModel.isNotBlank()) modelDisplayNames[selectedModel] ?: selectedModel
                        else "No model selected",
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )
                } else {
                  Text(
                    text = "Tap 'Fetch Models' to load available models from ${provider.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                  )
                }
              }
            }

            item { PreferenceSectionHeader(title = "Features") }

            item {
              PreferenceCard {
                SwitchPreference(
                  value = renameWithAi,
                  onValueChange = { preferences.renameWithAi.set(it) },
                  title = { Text("AI-Powered Rename") },
                  summary = {
                    Text(
                      "Use AI to generate clean filenames for bulk rename operations",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )

                PreferenceDivider()

                SwitchPreference(
                  value = subtitleFormatWithAi,
                  onValueChange = { preferences.subtitleFormatWithAi.set(it) },
                  title = { Text("AI Subtitle Search Formatting") },
                  summary = {
                    Text(
                      "Auto-format video titles for Wyzie/SubHub subtitle search",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )

                PreferenceDivider()

                SwitchPreference(
                  value = subtitleTranslationEnabled,
                  onValueChange = { enabled ->
                    preferences.subtitleTranslationEnabled.set(enabled)
                    if (enabled && subtitleTranslationFirstTime) {
                      showSubtitleTranslationWarning = true
                    }
                  },
                  title = { Text("Subtitle Translation") },
                  summary = {
                    Text(
                      "Translate external subtitles using AI",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }

            item { PreferenceSectionHeader(title = "Custom Prompt") }

            item {
              PreferenceCard {
                SwitchPreference(
                  value = customPromptEnabled,
                  onValueChange = { preferences.customPromptEnabled.set(it) },
                  title = { Text("Override Default Instructions") },
                  summary = {
                    Text(
                      if (customPromptEnabled) "Custom prompt will be used instead of built-in instructions"
                      else "Built-in AI instructions will be used",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )

                if (customPromptEnabled) {
                  PreferenceDivider()

                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Text(
                      text = "Custom Prompts",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                    )

                    Text(
                      text = "Leave a field blank to use the built-in instruction for that task. If you have an older global prompt saved, it will be used as a fallback.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                    )

                    TextField(
                      value = customRenamePrompt,
                      onValueChange = { preferences.customRenamePrompt.set(it) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                      label = { Text("Custom rename prompt") },
                      placeholder = { Text("Instructions for AI file renaming...") },
                      maxLines = 6,
                    )

                    TextField(
                      value = customSubtitleTranslationPrompt,
                      onValueChange = { preferences.customSubtitleTranslationPrompt.set(it) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                      label = { Text("Custom subtitle translation prompt") },
                      placeholder = { Text("Instructions for AI subtitle translation...") },
                      maxLines = 6,
                    )

                    TextField(
                      value = customSubtitleFormatPrompt,
                      onValueChange = { preferences.customSubtitleFormatPrompt.set(it) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                      label = { Text("Custom subtitle formatting prompt") },
                      placeholder = { Text("Instructions for formatting subtitle search queries...") },
                      maxLines = 6,
                    )

                    if (customPrompt.isNotBlank()) {
                      Text(
                        text = "Legacy global prompt saved. It will be used whenever a task-specific prompt is empty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  }
                }
              }
        }
      }
    }

    if (showSubtitleTranslationWarning) {
      AlertDialog(
        onDismissRequest = {
          showSubtitleTranslationWarning = false
          preferences.subtitleTranslationFirstTime.set(false)
        },
        title = { Text("Subtitle Translation") },
        text = {
          Text(
            "Subtitle translation can be a bit messy. " +
            "For best results, use better models and don't rant that subs aren't working properly."
          )
        },
        confirmButton = {
          TextButton(onClick = {
            showSubtitleTranslationWarning = false
            preferences.subtitleTranslationFirstTime.set(false)
          }) {
            Text("Got it")
          }
        }
      )
    }
      }
    }
  }
}
