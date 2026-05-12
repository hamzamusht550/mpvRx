package app.gyrolet.mpvrx.domain.thumbnail

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.os.Build
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import app.gyrolet.mpvrx.utils.storage.FileTypeUtils
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import okio.FileSystem

class CoilVideoThumbnailDecoder(
  private val source: ImageSource,
  private val options: Options,
  private val strategy: ThumbnailStrategy,
  private val diskCache: Lazy<DiskCache?>,
) : Decoder {
  private val diskCacheKey: String
    get() =
      options.diskCacheKey ?: run {
        val metadata = source.metadata
        when {
          metadata is ContentMetadata -> metadata.uri.toAndroidUri().toString()
          source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
          else -> error("Unsupported thumbnail source")
        }
      }

  @OptIn(ExperimentalCoilApi::class)
  override suspend fun decode(): DecodeResult {
    readFromDiskCache()?.use { snapshot ->
      val cachedBitmap =
        snapshot.data
          .toFile()
          .inputStream()
          .use(BitmapFactory::decodeStream)

      if (cachedBitmap != null) {
        val sampledBitmap = cachedBitmap.scaleToThumbnailMax()
        return DecodeResult(
          image = sampledBitmap.toDrawable(options.context.resources).asImage(),
          isSampled = sampledBitmap !== cachedBitmap,
        )
      }
    }

    if (strategy is ThumbnailStrategy.FirstFrame) {
      tryLoadSystemThumbnail()?.let { systemBitmap ->
        val cachedBitmap = writeToDiskCache(systemBitmap.scaleToThumbnailMax())
        return DecodeResult(
          image = cachedBitmap.toDrawable(options.context.resources).asImage(),
          isSampled = true,
        )
      }
    }

    return MediaMetadataRetriever().use { retriever ->
      retriever.setDataSource(source)
      val sourcePath = sourcePath()

      val embeddedPicture =
        if (strategy.prefersEmbeddedPicture()) {
          EmbeddedArtworkResolver.decodeEmbeddedArtwork(sourcePath, retriever)
        } else {
          null
        }

      var shouldRotate = true
      val rawBitmap =
        when (strategy) {
          ThumbnailStrategy.FirstFrame -> retriever.getThumbnailFrameAt(0)
          is ThumbnailStrategy.FrameAtPercentage -> {
            retriever.getThumbnailFrameAt(frameTimeMicros(retriever, strategy.percentage))
          }

          is ThumbnailStrategy.Hybrid -> {
            val firstFrame = retriever.getThumbnailFrameAt(0)
            if (firstFrame != null && isMostlySolidThumbnail(firstFrame)) {
              firstFrame.recycle()
              retriever.getThumbnailFrameAt(frameTimeMicros(retriever, strategy.percentage))
            } else {
              firstFrame
            }
          }

          is ThumbnailStrategy.EmbeddedOrHybrid ->
            embeddedPicture?.also { shouldRotate = false } ?: decodeHybridFrame(retriever, strategy.percentage)

          ThumbnailStrategy.EmbeddedOrFirstFrame ->
            embeddedPicture?.also { shouldRotate = false } ?: retriever.getThumbnailFrameAt(0)
        } ?: throw IllegalStateException("Failed to decode video thumbnail")

      val rotatedBitmap = if (shouldRotate) rotateBitmapIfNeeded(retriever, rawBitmap) else rawBitmap
      val thumbnailBitmap = rotatedBitmap.scaleToThumbnailMax()
      val cachedBitmap = writeToDiskCache(thumbnailBitmap)

      DecodeResult(
        image = cachedBitmap.toDrawable(options.context.resources).asImage(),
        isSampled = true,
      )
    }
  }

  private fun tryLoadSystemThumbnail(): Bitmap? {
    val uri =
      when (val metadata = source.metadata) {
        is ContentMetadata -> metadata.uri.toAndroidUri()
        else -> {
          if (source.fileSystem !== FileSystem.SYSTEM) return null
          findContentUriForPath(source.file().toFile().path) ?: return null
        }
      }

    return runCatching {
      options.context.contentResolver.loadThumbnail(
        uri,
        Size(MAX_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE),
        null,
      )
    }.getOrNull()
  }

  private fun findContentUriForPath(path: String): android.net.Uri? {
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(MediaStore.Video.Media._ID)
    return runCatching {
      options.context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.Video.Media.DATA} = ?",
        arrayOf(path),
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
          ContentUris.withAppendedId(collection, id)
        } else {
          null
        }
      }
    }.getOrNull()
  }

  private fun sourcePath(): String? {
    val metadata = source.metadata
    return when {
      metadata is ContentMetadata -> {
        val uri = metadata.uri.toAndroidUri()
        if (uri.scheme == "file") uri.path else uri.toString()
      }
      source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
      else -> null
    }
  }

  private fun frameTimeMicros(
    retriever: MediaMetadataRetriever,
    percentage: Float,
  ): Long {
    val durationMs =
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    return (durationMs * percentage.coerceIn(0f, 1f) * 1000).toLong()
  }

  private fun decodeHybridFrame(
    retriever: MediaMetadataRetriever,
    percentage: Float,
  ): Bitmap? {
    val firstFrame = retriever.getThumbnailFrameAt(0)
    return if (firstFrame != null && isMostlySolidThumbnail(firstFrame)) {
      firstFrame.recycle()
      retriever.getThumbnailFrameAt(frameTimeMicros(retriever, percentage))
    } else {
      firstFrame
    }
  }

  private fun MediaMetadataRetriever.getThumbnailFrameAt(timeUs: Long): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      runCatching {
        getScaledFrameAtTime(
          timeUs,
          MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
          MAX_THUMBNAIL_SIZE,
          MAX_THUMBNAIL_SIZE,
        )
      }.getOrNull() ?: getFrameAtTime(timeUs)
    } else {
      getFrameAtTime(timeUs)
    }

  private fun MediaMetadataRetriever.setDataSource(source: ImageSource) {
    val metadata = source.metadata
    when {
      metadata is ContentMetadata -> setDataSource(options.context, metadata.uri.toAndroidUri())
      source.fileSystem === FileSystem.SYSTEM -> setDataSource(source.file().toFile().path)
      else -> error("Unsupported thumbnail source")
    }
  }

  private fun rotateBitmapIfNeeded(
    retriever: MediaMetadataRetriever,
    bitmap: Bitmap,
  ): Bitmap {
    val rotation =
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
        ?: return bitmap
    if (rotation == 0) {
      return bitmap
    }

    val matrix =
      Matrix().apply {
        postRotate(rotation.toFloat())
      }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) {
      bitmap.recycle()
    }
    return rotated
  }

  private fun readFromDiskCache(): DiskCache.Snapshot? =
    if (options.diskCachePolicy.readEnabled) {
      diskCache.value?.openSnapshot(diskCacheKey)
    } else {
      null
    }

  private fun writeToDiskCache(inBitmap: Bitmap): Bitmap {
    if (!options.diskCachePolicy.writeEnabled) {
      return inBitmap
    }

    val editor = diskCache.value?.openEditor(diskCacheKey) ?: return inBitmap
    try {
      editor.data.toFile().outputStream().use { output ->
        inBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
      }
      editor.commitAndOpenSnapshot()?.use { snapshot ->
        val outBitmap =
          snapshot.data
            .toFile()
            .inputStream()
            .use(BitmapFactory::decodeStream)
        if (outBitmap != null) {
          if (outBitmap != inBitmap) {
            inBitmap.recycle()
          }
          return outBitmap
        }
      }
    } catch (_: Exception) {
      runCatching { editor.abort() }
    }

    return inBitmap
  }

  class Factory(
    private val thumbnailStrategy: () -> ThumbnailStrategy,
  ) : Decoder.Factory {
    override fun create(
      result: SourceFetchResult,
      options: Options,
      imageLoader: ImageLoader,
    ): Decoder? {
      if (!isApplicable(result)) {
        return null
      }

      return CoilVideoThumbnailDecoder(
        source = result.source,
        options = options,
        strategy = thumbnailStrategy(),
        diskCache = lazy { imageLoader.diskCache },
      )
    }

    private fun isApplicable(result: SourceFetchResult): Boolean {
      val mimeType = result.mimeType
      if (mimeType != null && mimeType.startsWith("video/")) {
        return true
      }

      val metadata = result.source.metadata
      val sourcePath =
        when {
          metadata is ContentMetadata -> metadata.uri.toString()
          result.source.fileSystem === FileSystem.SYSTEM -> result.source.file().toFile().path
          else -> null
        } ?: return false

      val extension = sourcePath.substringAfterLast('.', "").lowercase()
      return FileTypeUtils.VIDEO_EXTENSIONS.contains(extension)
    }
  }
}

sealed class ThumbnailStrategy {
  abstract val cacheKey: String

  data object FirstFrame : ThumbnailStrategy() {
    override val cacheKey: String = "first_frame"
  }

  data class FrameAtPercentage(
    val percentage: Float = 0.33f,
  ) : ThumbnailStrategy() {
    override val cacheKey: String = "frame_${percentage}"
  }

  data class Hybrid(
    val percentage: Float = 0.33f,
  ) : ThumbnailStrategy() {
    override val cacheKey: String = "hybrid_${percentage}"
  }

  data class EmbeddedOrHybrid(
    val percentage: Float = 0.33f,
  ) : ThumbnailStrategy() {
    override val cacheKey: String = "embedded_or_hybrid_${percentage}"
  }

  data object EmbeddedOrFirstFrame : ThumbnailStrategy() {
    override val cacheKey: String = "embedded_or_first_frame"
  }
}

private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T =
  try {
    block(this)
  } finally {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      close()
    } else {
      release()
    }
  }

