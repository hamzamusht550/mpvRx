package app.gyrolet.mpvrx.ui.player

internal data class IntentSubtitleLoadEntry<T>(
  val value: T,
  val metadataIndex: Int,
  val select: Boolean,
)

internal object IntentSubtitleLoadPolicy {
  fun <T> entriesToLoad(
    subtitles: List<T>,
    enabledSubtitles: List<T>,
    hasEnabledSubtitleExtra: Boolean,
  ): List<IntentSubtitleLoadEntry<T>> {
    if (hasEnabledSubtitleExtra) {
      return enabledSubtitles.distinct().map { enabledSubtitle ->
        IntentSubtitleLoadEntry(
          value = enabledSubtitle,
          metadataIndex = subtitles.indexOf(enabledSubtitle),
          select = true,
        )
      }
    }

    return subtitles.mapIndexed { index, subtitle ->
      IntentSubtitleLoadEntry(
        value = subtitle,
        metadataIndex = index,
        select = false,
      )
    }
  }
}
