package app.gyrolet.mpvrx.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.YtdlPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlCodecPreference
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlContainerPreference
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlHdrPreference
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlPlaylistMode
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlpManager
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlpOptionSettings
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlpOptionsBuilder
import app.gyrolet.mpvrx.ui.theme.spacing
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference

@Serializable
object YtdlpSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backStack = LocalBackStack.current
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        var logs by remember { mutableStateOf("") }
        val scrollState = rememberScrollState()
        var isRunning by remember { mutableStateOf(false) }

        val ytdlPreferences = koinInject<YtdlPreferences>()
        val ytdlQuality by ytdlPreferences.ytdlQuality.collectAsState()
        val preferH264 by ytdlPreferences.preferH264.collectAsState()
        val codecPreference by ytdlPreferences.codecPreference.collectAsState()
        val maxFps by ytdlPreferences.maxFps.collectAsState()
        val hdrPreference by ytdlPreferences.hdrPreference.collectAsState()
        val containerPreference by ytdlPreferences.containerPreference.collectAsState()
        val playlistMode by ytdlPreferences.playlistMode.collectAsState()
        val geoBypass by ytdlPreferences.geoBypass.collectAsState()
        val liveFromStart by ytdlPreferences.liveFromStart.collectAsState()
        val writeSubs by ytdlPreferences.writeSubs.collectAsState()
        val writeAutoSubs by ytdlPreferences.writeAutoSubs.collectAsState()
        
        var userAgentText by remember { mutableStateOf(ytdlPreferences.customUserAgent.get()) }
        var subtitleLanguagesText by remember { mutableStateOf(ytdlPreferences.subtitleLanguages.get()) }
        var formatSortText by remember { mutableStateOf(ytdlPreferences.formatSort.get()) }
        var mergeOutputFormatText by remember { mutableStateOf(ytdlPreferences.mergeOutputFormat.get()) }
        var refererText by remember { mutableStateOf(ytdlPreferences.referer.get()) }
        var cookiesFileText by remember { mutableStateOf(ytdlPreferences.cookiesFile.get()) }
        var proxyText by remember { mutableStateOf(ytdlPreferences.proxy.get()) }
        var extractorArgsText by remember { mutableStateOf(ytdlPreferences.extractorArgs.get()) }
        var sponsorBlockMarkText by remember { mutableStateOf(ytdlPreferences.sponsorBlockMark.get()) }
        var sponsorBlockRemoveText by remember { mutableStateOf(ytdlPreferences.sponsorBlockRemove.get()) }
        var rawOptionsText by remember { mutableStateOf(ytdlPreferences.customRawOptions.get()) }

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

        val ytdlpInfo = remember(hasYtdlp) {
            if (hasYtdlp) {
                val size = try {
                    val f = File(ytdlDir, "yt-dlp")
                    if (f.exists()) " (${f.length() / 1024 / 1024} MB)" else ""
                } catch (_: Exception) { "" }
                "Installed$size"
            } else {
                "Not Configured"
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "yt-dlp Streaming",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { backStack.popSafely() }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            ProvidePreferenceLocals {
                Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                // Expressive Installation Status Card
                PreferenceCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasYtdlp) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (hasYtdlp) Icons.Default.Check else Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = if (hasYtdlp) MaterialTheme.colorScheme.onPrimaryContainer 
                                           else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "yt-dlp Core Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (hasYtdlp) "Subprocess active and ready for streaming" 
                                       else "Engine missing. Please run installation below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasYtdlp) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = ytdlpInfo,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (hasYtdlp) MaterialTheme.colorScheme.onPrimary 
                                        else MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }

                PreferenceSectionHeader(title = "Quality & Format")

                PreferenceCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        Text(
                            text = "Streaming Quality",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val qualityLevels = remember { arrayOf(-1, 2160, 1440, 1080, 720, 480, 360, 240, 144) }
                        val qualityLabels = remember { qualityLevels.map { if (it == -1) "Any" else "${it}p" } }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            qualityLevels.forEachIndexed { index, level ->
                                FilterChip(
                                    selected = ytdlQuality == level,
                                    onClick = {
                                        ytdlPreferences.ytdlQuality.set(level)
                                        updateFormatString(ytdlPreferences)
                                    },
                                    label = { Text(qualityLabels[index]) },
                                    leadingIcon = if (ytdlQuality == level) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        PreferenceDivider()

                        OptionDropdown(
                            title = "Video Codec",
                            value = codecPreference,
                            values = YtdlCodecPreference.entries,
                            valueLabel = { it.title },
                            onValueChange = { selected ->
                                ytdlPreferences.codecPreference.set(selected)
                                ytdlPreferences.preferH264.set(selected == YtdlCodecPreference.H264)
                                updateFormatString(ytdlPreferences)
                            },
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(0 to "Any FPS", 30 to "30 FPS", 60 to "60 FPS", 120 to "120 FPS").forEach { (fps, label) ->
                                FilterChip(
                                    selected = maxFps == fps,
                                    onClick = {
                                        ytdlPreferences.maxFps.set(fps)
                                        updateFormatString(ytdlPreferences)
                                    },
                                    label = { Text(label) },
                                    leadingIcon = if (maxFps == fps) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        OptionDropdown(
                            title = "HDR Preference",
                            value = hdrPreference,
                            values = YtdlHdrPreference.entries,
                            valueLabel = { it.title },
                            onValueChange = {
                                ytdlPreferences.hdrPreference.set(it)
                                updateFormatString(ytdlPreferences)
                            },
                        )

                        OptionDropdown(
                            title = "Container",
                            value = containerPreference,
                            values = YtdlContainerPreference.entries,
                            valueLabel = { it.title },
                            onValueChange = {
                                ytdlPreferences.containerPreference.set(it)
                                updateFormatString(ytdlPreferences)
                            },
                        )

                        OutlinedTextField(
                            value = formatSortText,
                            onValueChange = {
                                formatSortText = it
                                ytdlPreferences.formatSort.set(it)
                            },
                            label = { Text("Format Sort") },
                            placeholder = { Text("res,fps,hdr:12,vcodec:vp9.2") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Passed to yt-dlp as format-sort for advanced ranking.") }
                        )

                        OutlinedTextField(
                            value = mergeOutputFormatText,
                            onValueChange = {
                                mergeOutputFormatText = it
                                ytdlPreferences.mergeOutputFormat.set(it)
                            },
                            label = { Text("Merge Output Format") },
                            placeholder = { Text("mp4, mkv, webm") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PreferenceDivider()

                        val currentFormat = remember(ytdlQuality, preferH264, codecPreference, maxFps, hdrPreference, containerPreference) {
                            YtdlpOptionsBuilder.buildFormat(YtdlpOptionSettings.fromYtdlPreferences(ytdlPreferences))
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Generated Format String",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = currentFormat.ifBlank { "(default)" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                PreferenceSectionHeader(title = "Subtitles & Language")

                PreferenceCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        SwitchPreference(
                            value = writeSubs,
                            onValueChange = { ytdlPreferences.writeSubs.set(it) },
                            title = { Text("Download Media Subtitles", fontWeight = FontWeight.Medium) },
                            summary = { Text("Automatically extract and load physical subtitle tracks from supported URLs.") }
                        )

                        PreferenceDivider()

                        SwitchPreference(
                            value = writeAutoSubs,
                            onValueChange = { ytdlPreferences.writeAutoSubs.set(it) },
                            title = { Text("Include Auto-Generated Subtitles", fontWeight = FontWeight.Medium) },
                            summary = { Text("Fetch auto-caption tracks (e.g. YouTube Speech-to-Text) when regular subs are absent.") }
                        )

                        PreferenceDivider()

                        OutlinedTextField(
                            value = subtitleLanguagesText,
                            onValueChange = {
                                subtitleLanguagesText = it
                                ytdlPreferences.subtitleLanguages.set(it)
                            },
                            label = { Text("Subtitle Languages") },
                            placeholder = { Text("all or en.*,ja") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Overrides app subtitle languages only for yt-dlp downloads.") }
                        )
                    }
                }

                PreferenceSectionHeader(title = "Advanced Networking")

                PreferenceCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        OutlinedTextField(
                            value = userAgentText,
                            onValueChange = { 
                                userAgentText = it
                                ytdlPreferences.customUserAgent.set(it)
                            },
                            label = { Text("Custom User-Agent Override") },
                            placeholder = { Text("Mozilla/5.0 ...") },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            supportingText = {
                                Text("Leave blank to use default browser User-Agent. Helps bypass anti-bot scrapers.")
                            }
                        )

                        PreferenceDivider()

                        OutlinedTextField(
                            value = refererText,
                            onValueChange = {
                                refererText = it
                                ytdlPreferences.referer.set(it)
                            },
                            label = { Text("Referer") },
                            placeholder = { Text("https://www.youtube.com/") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = cookiesFileText,
                            onValueChange = {
                                cookiesFileText = it
                                ytdlPreferences.cookiesFile.set(it)
                            },
                            label = { Text("Cookies File") },
                            placeholder = { Text("/storage/emulated/0/Download/cookies.txt") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = proxyText,
                            onValueChange = {
                                proxyText = it
                                ytdlPreferences.proxy.set(it)
                            },
                            label = { Text("Proxy") },
                            placeholder = { Text("socks5://127.0.0.1:1080") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = extractorArgsText,
                            onValueChange = {
                                extractorArgsText = it
                                ytdlPreferences.extractorArgs.set(it)
                            },
                            label = { Text("Extractor Args") },
                            placeholder = { Text("youtube:player_client=android,web") },
                            singleLine = false,
                            maxLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OptionDropdown(
                            title = "Playlist Behavior",
                            value = playlistMode,
                            values = YtdlPlaylistMode.entries,
                            valueLabel = { it.title },
                            onValueChange = { ytdlPreferences.playlistMode.set(it) },
                        )

                        SwitchPreference(
                            value = geoBypass,
                            onValueChange = { ytdlPreferences.geoBypass.set(it) },
                            title = { Text("Geo Bypass", fontWeight = FontWeight.Medium) },
                            summary = { Text("Ask yt-dlp to use its extractor-level region bypass logic.") }
                        )

                        SwitchPreference(
                            value = liveFromStart,
                            onValueChange = { ytdlPreferences.liveFromStart.set(it) },
                            title = { Text("Live From Start", fontWeight = FontWeight.Medium) },
                            summary = { Text("Start live streams from the beginning when the extractor supports it.") }
                        )

                        OutlinedTextField(
                            value = sponsorBlockMarkText,
                            onValueChange = {
                                sponsorBlockMarkText = it
                                ytdlPreferences.sponsorBlockMark.set(it)
                            },
                            label = { Text("SponsorBlock Mark") },
                            placeholder = { Text("sponsor,selfpromo") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = sponsorBlockRemoveText,
                            onValueChange = {
                                sponsorBlockRemoveText = it
                                ytdlPreferences.sponsorBlockRemove.set(it)
                            },
                            label = { Text("SponsorBlock Remove") },
                            placeholder = { Text("sponsor") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PreferenceDivider()

                        OutlinedTextField(
                            value = rawOptionsText,
                            onValueChange = { 
                                rawOptionsText = it
                                ytdlPreferences.customRawOptions.set(it)
                            },
                            label = { Text("Raw yt-dlp Options") },
                            placeholder = { Text("extractor-args=\"youtube:player_client=android,web\"\ngeo-bypass=") },
                            singleLine = false,
                            maxLines = 6,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            supportingText = {
                                Text("Anything not exposed above. Separate options with new lines or commas; quote values that contain commas.")
                            }
                        )
                    }
                }

                PreferenceSectionHeader(title = "Engine Installer")

                PreferenceCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Manage yt-dlp Environment",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Download the latest wrapper modules and compile python-friendly native binaries inside local sandboxed folders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Install Core")
                            }

                            OutlinedButton(
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
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.Update, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Update Core")
                            }
                        }
                    }
                }

                // High-Tech Console Terminal
                AnimatedVisibility(
                    visible = isRunning || logs.isNotEmpty(),
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Terminal Header
                        Surface(
                            color = Color(0xFF0F1419),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Simulated Terminal status dots
                                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF5F56)))
                                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFBD2E)))
                                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF27C93F)))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "engine-terminal.sh",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE6B450)
                                        )
                                    )
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF00FF99)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(logs))
                                            Toast.makeText(context, "Terminal logs copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy logs",
                                            tint = Color(0xFFB3B3B3),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { logs = "" },
                                        modifier = Modifier.size(24.dp),
                                        enabled = !isRunning
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clear logs",
                                            tint = if (isRunning) Color(0xFF555555) else Color(0xFFB3B3B3),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Terminal logs content
                        Surface(
                            color = Color(0xFF070B0E),
                            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            val terminalScrollState = rememberScrollState()
                            
                            LaunchedEffect(logs) {
                                terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(terminalScrollState)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = logs.ifEmpty { "Ready..." },
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFF00FF99)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    private fun updateFormatString(prefs: YtdlPreferences) {
        prefs.ytdlFormat.set(YtdlpOptionsBuilder.buildFormat(YtdlpOptionSettings.fromYtdlPreferences(prefs)))
    }
}

private fun YtdlpOptionSettings.Companion.fromYtdlPreferences(prefs: YtdlPreferences): YtdlpOptionSettings =
    YtdlpOptionSettings(
        codecPreference = prefs.codecPreference.get(),
        legacyPreferH264 = prefs.preferH264.get(),
        maxHeight = prefs.ytdlQuality.get(),
        maxFps = prefs.maxFps.get(),
        hdrPreference = prefs.hdrPreference.get(),
        containerPreference = prefs.containerPreference.get(),
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> OptionDropdown(
    title: String,
    value: T,
    values: List<T>,
    valueLabel: (T) -> String,
    onValueChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = valueLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(valueLabel(item)) },
                    onClick = {
                        expanded = false
                        onValueChange(item)
                    },
                )
            }
        }
    }
}
