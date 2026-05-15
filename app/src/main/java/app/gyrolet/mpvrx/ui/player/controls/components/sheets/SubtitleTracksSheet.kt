package app.gyrolet.mpvrx.ui.player.controls.components.sheets

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.presentation.components.PlayerSheet
import app.gyrolet.mpvrx.ui.player.TrackNode
import app.gyrolet.mpvrx.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

sealed class SubtitleItem {
  data class Track(val node: TrackNode) : SubtitleItem()
  data class Header(val title: String) : SubtitleItem()
  object Divider : SubtitleItem()
}

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>,
  onToggleSubtitle: (Int) -> Unit,
  isSubtitleSelected: (Int) -> Boolean,
  onAddSubtitle: () -> Unit,
  onOpenSubtitleSettings: () -> Unit,
  onOpenSubtitleDelay: () -> Unit,
  onRemoveSubtitle: (Int) -> Unit,
  onOpenOnlineSearch: () -> Unit,
  onDismissRequest: () -> Unit,
  onTranslateSubtitle: (TrackNode, String) -> Unit,
  isTranslating: Boolean,
  translationProgress: Float,
  translationEnabled: Boolean,
  translatingTrackId: Int? = null,
  translatingTrackName: String = "",
  modifier: Modifier = Modifier,
) {
  val items = remember(tracks) {
    val list = mutableListOf<SubtitleItem>()
    val internal = tracks.filter { it.external != true }
    val external = tracks.filter { it.external == true }

    if (internal.isNotEmpty() || external.isNotEmpty()) {
      list.add(SubtitleItem.Header(if (internal.isNotEmpty()) "Embedded Subtitles" else "Local Subtitles"))
      list.addAll(internal.map { SubtitleItem.Track(it) })
      if (internal.isNotEmpty() && external.isNotEmpty()) {
        list.add(SubtitleItem.Header("External Subtitles"))
      }
      list.addAll(external.map { SubtitleItem.Track(it) })
    }

    list.toImmutableList()
  }

  val languages = remember {
    listOf(
      "English", "Spanish", "French", "German", "Japanese", 
      "Korean", "Chinese", "Arabic", "Hindi", "Portuguese"
    )
  }
  var showLanguagePicker by remember { androidx.compose.runtime.mutableStateOf<TrackNode?>(null) }

  if (showLanguagePicker != null) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showLanguagePicker = null },
      title = { Text("Translate to...") },
      text = {
        LazyColumn {
          items(languages) { lang ->
            Text(
              text = lang,
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onTranslateSubtitle(showLanguagePicker!!, lang)
                  showLanguagePicker = null
                }
                .padding(MaterialTheme.spacing.medium)
            )
          }
        }
      },
      confirmButton = {
        androidx.compose.material3.TextButton(onClick = { showLanguagePicker = null }) {
          Text("Cancel")
        }
      }
    )
  }

  PlayerSheet(onDismissRequest) {
    Column(modifier) {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_sub),
        onAddSubtitle,
        actions = {
          IconButton(onClick = onOpenOnlineSearch) {
            Icon(Icons.Default.Search, null)
          }
          IconButton(onClick = onOpenSubtitleSettings) {
            Icon(Icons.Default.Palette, null)
          }
          IconButton(onClick = onOpenSubtitleDelay) {
            Icon(Icons.Default.AvTimer, null)
          }
        },
      )

      if (isTranslating) {
        androidx.compose.foundation.layout.Column(
          modifier = Modifier.padding(MaterialTheme.spacing.medium),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
          Text(
            "Translating ${translatingTrackName}... ${(translationProgress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
          )
          androidx.compose.material3.LinearProgressIndicator(
            progress = { translationProgress },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      LazyColumn {
        items(items) { item ->
          when (item) {
            is SubtitleItem.Track -> {
              val track = item.node
              SubtitleTrackRow(
                title = getTrackTitle(track),
                isSelected = isSubtitleSelected(track.id),
                isExternal = track.external == true,
                onToggle = { onToggleSubtitle(track.id) },
                onRemove = { onRemoveSubtitle(track.id) },
                onTranslate = { if (translationEnabled) showLanguagePicker = track },
                translationEnabled = translationEnabled,
                isCurrentlyTranslating = track.id == translatingTrackId,
              )
            }
            is SubtitleItem.Header -> {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            SubtitleItem.Divider -> {
              HorizontalDivider(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              )
            }
          }
        }
        item {
          Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        }
      }
    }
  }
}

@Composable
fun SubtitleTrackRow(
  title: String,
  isSelected: Boolean,
  isExternal: Boolean,
  onToggle: () -> Unit,
  onRemove: () -> Unit,
  onTranslate: () -> Unit,
  translationEnabled: Boolean,
  isCurrentlyTranslating: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onToggle)
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
    
    if (isCurrentlyTranslating) {
      androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(MaterialTheme.spacing.large),
        strokeWidth = MaterialTheme.spacing.smaller,
      )
    }
    
    if (isExternal) {
      if (translationEnabled) {
        IconButton(onClick = onTranslate) { Icon(Icons.Default.Translate, contentDescription = "Translate") }
      }
      IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = null) }
    }
  }
}
