package app.gyrolet.mpvrx.utils.media

import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.media.model.VideoFolder

// Made private since these are only used internally by the engine
private data class VideoIndex(
  val video: Video,
  val nameLower: String,
  val tokens: List<String>
)

private data class FolderIndex(
  val folder: VideoFolder,
  val nameLower: String,
  val tokens: List<String>
)

object MediaSearchEngine {
  private val videoMap = HashMap<String, VideoIndex>()
  private val folderMap = HashMap<String, FolderIndex>()

  // -------------------------
  // INDEXING
  // -------------------------
  fun buildIndex(
    folders: List<VideoFolder>,
    videosByFolder: Map<String, List<Video>>
  ) {
    videoMap.clear()
    folderMap.clear()
    
    // Optimization: Pre-allocate capacity to avoid internal array resizing
    videoMap.ensureCapacity(videosByFolder.values.sumOf { it.size })
    folderMap.ensureCapacity(folders.size)

    for (folder in folders) {
      val name = folder.name
      folderMap[folder.path] = FolderIndex(
        folder = folder,
        nameLower = name.lowercase(),
        tokens = tokenize(name)
      )

      // Optimization: Use 'continue' to skip folders with no videos
      val videos = videosByFolder[folder.bucketId] ?: continue
      for (video in videos) {
        val vName = video.displayName
        videoMap[video.path] = VideoIndex(
          video = video,          nameLower = vName.lowercase(),
          tokens = tokenize(vName)
        )
      }
    }
  }

  // -------------------------
  // SEARCH
  // -------------------------
  fun search(query: String, limit: Int = 50): List<Any> {
    if (query.isBlank()) return emptyList()

    val q = query.lowercase()
    val qTokens = tokenize(q)
    val results = ArrayList<Pair<Any, Int>>()

    for (folder in folderMap.values) {
      val score = score(folder.nameLower, folder.tokens, q, qTokens)
      if (score > 0) results.add(folder.folder to score)
    }

    for (video in videoMap.values) {
      val score = score(video.nameLower, video.tokens, q, qTokens)
      if (score > 0) results.add(video.video to score)
    }

    return results
      .sortedByDescending { it.second }
      .take(limit)
      .map { it.first }
  }

  // -------------------------
  // SCORING & HELPERS
  // -------------------------
  private fun score(
    text: String,
    tokens: List<String>,
    query: String,
    qTokens: List<String>
  ): Int {
    var score = 0

    if (text == query) return 1000
    if (text.startsWith(query)) score += 200
    if (text.contains(query)) score += 120

    for (qt in qTokens) {
      for (t in tokens) {        if (t == qt) score += 80
        else if (t.startsWith(qt)) score += 40
      }
    }

    if (isSequentialMatch(text, query)) score += 60

    return score
  }

  // Optimization: split() with vararg chars is significantly faster than Regex or multiple string splits
  private fun tokenize(text: String): List<String> {
    return text.lowercase().split(' ', '_', '-', '.', '/').filter { it.isNotEmpty() }
  }

  // Optimization: Cached query.length to avoid repeated property access in the loop
  private fun isSequentialMatch(text: String, query: String): Boolean {
    var i = 0
    val qLen = query.length
    for (c in text) {
      if (i < qLen && c == query[i]) {
        i++
      }
    }
    return i == qLen
  }
}

