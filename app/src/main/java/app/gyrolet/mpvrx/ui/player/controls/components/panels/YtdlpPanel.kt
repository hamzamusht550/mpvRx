package app.gyrolet.mpvrx.ui.player.controls.components.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlpManager
import app.gyrolet.mpvrx.ui.theme.spacing
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun YtdlpPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    var isRunning by remember { mutableStateOf(false) }

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

    DraggablePanel(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Text(
                text = "yt-dlp Manager",
                style = MaterialTheme.typography.headlineSmall
            )

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
                text = "Bypass SDK 29+ active (using libpython.so)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
