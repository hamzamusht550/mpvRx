package app.gyrolet.mpvrx.ui.player

object M3uPlaybackPolicy {
  private val networkSchemes =
    setOf("http", "https", "ftp", "ftps", "rtmp", "rtmps", "rtsp", "rtsps", "mms", "mmsh")

  fun shouldExpandInApp(
    playableUri: String,
    originalUri: String?,
    fileName: String,
    mimeType: String?,
    hasExistingPlaylist: Boolean,
    hasPlaylistId: Boolean,
  ): Boolean {
    if (hasExistingPlaylist || hasPlaylistId) return false
    if (!looksLikeM3u(playableUri, originalUri, fileName, mimeType)) return false

    // Remote M3U/HLS URLs often need mpv's own HTTP stack, ytdl hook, cookies,
    // headers, redirects, and stream-specific playlist handling.
    return !isNetworkUri(originalUri) && !isNetworkUri(playableUri)
  }

  private fun looksLikeM3u(
    playableUri: String,
    originalUri: String?,
    fileName: String,
    mimeType: String?,
  ): Boolean {
    val candidates = listOfNotNull(playableUri, originalUri, fileName).map { it.lowercase() }
    return candidates.any(::hasM3uMarker) ||
      mimeType?.lowercase()?.let { type ->
        type.contains("mpegurl") || type.contains("x-mpegurl") || type.contains("vnd.apple.mpegurl")
      } == true
  }

  private fun hasM3uMarker(value: String): Boolean =
    value.endsWith(".m3u") ||
      value.endsWith(".m3u8") ||
      value.contains(".m3u?") ||
      value.contains(".m3u8?") ||
      value.contains(".m3u#") ||
      value.contains(".m3u8#")

  private fun isNetworkUri(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val scheme = value.substringBefore(":", missingDelimiterValue = "").lowercase()
    return scheme in networkSchemes
  }
}
