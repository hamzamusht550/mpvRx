package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.YtdlPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlpManager
import app.gyrolet.mpvrx.ui.theme.spacing
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject
import java.io.File

@Serializable
object YtdlpSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backStack = LocalBackStack.current
        val scope = rememberCoroutineScope()
        var logs by remember { mutableStateOf("") }
        val scrollState = rememberScrollState()
        var isRunning by remember { mutableStateOf(false) }

        val ytdlPreferences = koinInject<YtdlPreferences>()
        val ytdlQuality by ytdlPreferences.ytdlQuality.collectAsState()
        val preferH264 by ytdlPreferences.preferH264.collectAsState()

        val ytdlDir = remember { YtdlpManager.getYtdlDir(context) }
        var hasYtdlp by remember { mutableStateOf(File(ytdlDir, "yt-dlp").exists()) }

        LaunchedEffect(isRunning) {
            if (!isRunning) {
                hasYtdlp = File(ytdlDir, "yt-dlp").exists()
            }
        }

        LaunchedEffect(logs) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "yt-dlp Manager",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { backStack.popSafely() }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            ProvidePreferenceLocals {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    Text(
                        text = "Manage yt-dlp for streaming support. This uses a bypass for SDK 29+ restrictions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
                            Text(
                                text = "Streaming Quality",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraSmall)
                            )
                            
                            var expanded by remember { mutableStateOf(false) }
                            val qualityLevels = remember { arrayOf(-1, 2160, 1440, 1080, 720, 480, 360, 240, 144) }
                            
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                                OutlinedTextField(
                                    value = if (ytdlQuality == -1) "Any" else "${ytdlQuality}p",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    qualityLevels.forEach { level ->
                                        DropdownMenuItem(
                                            text = { Text(if (level == -1) "Any" else "${level}p") },
                                            onClick = {
                                                ytdlPreferences.ytdlQuality.set(level)
                                                updateFormatString(ytdlPreferences, level, preferH264)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            SwitchPreference(
                                value = preferH264,
                                onValueChange = { newValue -> 
                                    ytdlPreferences.preferH264.set(newValue)
                                    updateFormatString(ytdlPreferences, ytdlQuality, newValue)
                                },
                                title = { Text("Prefer H.264 (AVC)") },
                                summary = { Text("Better compatibility, but may limit quality") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isRunning = true
                                    logs = ""
                                    YtdlpManager.runInstall(context) { line ->
                                        logs += line
                                    }
                                    isRunning = false
                                }
                            },
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Install")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isRunning = true
                                    logs = ""
                                    YtdlpManager.runUpdate(context) { line ->
                                        logs += line
                                    }
                                    isRunning = false
                                }
                            },
                            enabled = !isRunning && hasYtdlp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Update")
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(MaterialTheme.spacing.small)
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = logs.ifEmpty { "Ready..." },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }

                    if (isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Text(
                        text = "Bypass active: Using libpython.so from native library directory.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    private fun updateFormatString(prefs: YtdlPreferences, quality: Int, preferH264: Boolean) {
        var qstr = ""
        /* bv = bestvideo, ba = bestaudio, b = best */
        if (quality != -1 && preferH264) {
            qstr = "(bv*[vcodec^=?avc]/bv*[vcodec^=?mp4])[height<=?${quality}]+ba/" +
                    "(b[vcodec^=?avc]/b[vcodec^=?mp4])[height<=?${quality}]"
        } else if (quality != -1) {
            qstr = "bv[height<=?${quality}]+ba/b[height<=?${quality}]"
        } else if (preferH264) {
            qstr = "(bv*[vcodec^=?avc]/bv*[vcodec^=?mp4])+ba/(b[vcodec^=?avc]/b[vcodec^=?mp4])"
        }
        if (qstr.isNotEmpty())
            qstr += "/bv*+ba/b"
        
        prefs.ytdlFormat.set(qstr)
    }
}
