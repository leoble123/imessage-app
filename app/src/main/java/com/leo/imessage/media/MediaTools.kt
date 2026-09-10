package com.leo.imessage.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.leo.imessage.data.Attachment
import com.leo.imessage.data.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Everything the UI needs to do with a real file: read a thumbnail out of
 * it, learn its name and duration, hand it to another app, or put a copy in
 * the user's gallery.
 *
 * Deliberately dependency-free - decoding goes through the platform's own
 * ImageDecoder and MediaMetadataRetriever rather than pulling in an image
 * loading library, so the APK stays small and there's nothing to keep in
 * step with Compose.
 */
object MediaTools {

    /**
     * Reads an attachment's display name, size and duration from whoever
     * owns the URI.
     *
     * Content URIs from the photo picker don't carry a filename in the path,
     * so the only way to show "IMG_4821.HEIC" instead of a row of digits is
     * to ask the provider for it.
     */
    fun describe(context: Context, uri: Uri, mimeType: String): Attachment {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameCol >= 0 && !cursor.isNull(nameCol)) name = cursor.getString(nameCol)
                    val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeCol >= 0 && !cursor.isNull(sizeCol)) size = cursor.getLong(sizeCol)
                }
            }
        }

        val duration = if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
            durationOf(context, uri)
        } else {
            null
        }

        return Attachment(
            id = UUID.randomUUID().toString(),
            fileName = name ?: uri.lastPathSegment ?: "Attachment",
            mimeType = mimeType,
            uri = uri.toString(),
            durationMs = duration,
            sizeBytes = size,
        )
    }

    /** Best-effort MIME lookup, falling back to the extension. */
    fun mimeTypeOf(context: Context, uri: Uri): String {
        context.contentResolver.getType(uri)?.let { return it }
        val ext = uri.toString().substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private fun durationOf(context: Context, uri: Uri): Long? = withRetriever(context, uri) {
        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    }

    /**
     * MediaMetadataRetriever only became AutoCloseable in API 29, so `use`
     * would compile against 35 and then blow up on an older device. Release
     * it by hand instead.
     */
    private fun <T> withRetriever(context: Context, uri: Uri, block: (MediaMetadataRetriever) -> T): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            block(retriever)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Decodes a thumbnail, downsampled to roughly [maxPx] on its long edge.
     *
     * The downsample isn't optional: a modern phone photo is 4000px wide and
     * decoding a dozen of those at full size to show them at 212dp would run
     * the app out of memory long before it ran out of thread to scroll.
     */
    suspend fun loadBitmap(context: Context, uri: Uri, maxPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > maxPx) decoder.setTargetSampleSize(sampleSize(longest, maxPx))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                    }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    val longest = maxOf(bounds.outWidth, bounds.outHeight)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = if (longest > maxPx) sampleSize(longest, maxPx) else 1
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
            }.getOrNull()
        }

    /** Pulls a poster frame out of a video so it can be shown in a bubble. */
    suspend fun loadVideoFrame(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            withRetriever(context, uri) { it.frameAtTime }
        }

    private fun sampleSize(longest: Int, maxPx: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= maxPx) sample *= 2
        return sample
    }

    /**
     * Copies an attachment into the user's Photos (or Downloads for
     * non-media), the way Save Image does on iOS.
     *
     * Uses IS_PENDING so the file never appears in the gallery half-written,
     * which is what produces those broken grey tiles when an app crashes
     * mid-save.
     */
    suspend fun saveToGallery(context: Context, uri: Uri, mimeType: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val collection = when {
                    mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                }
                val folder = when {
                    mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
                    mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
                    mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_DOWNLOADS
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Messages")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val target = context.contentResolver.insert(collection, values)
                    ?: return@runCatching false

                context.contentResolver.openOutputStream(target)?.use { out ->
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.copyTo(out)
                    } ?: return@runCatching false
                } ?: return@runCatching false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(
                        target,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                true
            }.getOrDefault(false)
        }

    /** Hands the file to the system share sheet. */
    fun share(context: Context, uri: Uri, mimeType: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    /** Opens the file in whichever app handles its type. */
    fun openExternally(context: Context, uri: Uri, mimeType: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** A file in cache the camera can write into, exposed via FileProvider. */
    fun newCaptureTarget(context: Context, extension: String): Pair<File, Uri> {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.$extension")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val minutes = total / 60
        val seconds = total % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun iconLabelFor(kind: MediaKind): String = when (kind) {
        MediaKind.IMAGE -> "Photo"
        MediaKind.VIDEO -> "Video"
        MediaKind.AUDIO -> "Audio"
        MediaKind.FILE -> "File"
    }
}
