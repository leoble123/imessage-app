package com.leo.imessage.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.leo.imessage.data.MediaKind
import com.leo.imessage.media.MediaTools

/**
 * Decoded thumbnails, kept alive across scrolling.
 *
 * A LazyColumn destroys and rebuilds items constantly, so without a cache
 * every scroll back up would re-decode the same photo from disk - which is
 * both slow and visibly pops. Bounded, because holding every bitmap a long
 * thread ever showed is how you run out of memory.
 */
private object ThumbnailCache {
    private const val MAX_ENTRIES = 48
    private val entries = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): Bitmap? = entries[key]

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        entries[key] = bitmap
    }
}

/**
 * Loads an attachment's thumbnail off the main thread.
 *
 * Returns null until it's ready, and null forever for attachments with no
 * file behind them - callers fall back to a placeholder in that case.
 */
@Composable
fun rememberThumbnail(
    uri: String?,
    kind: MediaKind,
    maxPx: Int = 900,
): ImageBitmap? {
    if (uri == null) return null
    val context = LocalContext.current
    val cacheKey = "$uri@$maxPx"

    var bitmap by remember(cacheKey) { mutableStateOf(ThumbnailCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap != null) return@LaunchedEffect
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@LaunchedEffect
        val loaded = when (kind) {
            MediaKind.VIDEO -> MediaTools.loadVideoFrame(context, parsed)
            else -> MediaTools.loadBitmap(context, parsed, maxPx)
        }
        if (loaded != null) {
            ThumbnailCache.put(cacheKey, loaded)
            bitmap = loaded
        }
    }

    return remember(bitmap) { bitmap?.asImageBitmap() }
}
