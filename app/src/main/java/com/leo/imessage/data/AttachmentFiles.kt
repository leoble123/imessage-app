package com.leo.imessage.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Moving attachments between Android's world and the protocol's.
 *
 * Android hands out `content://` URIs, which are permission-scoped handles
 * belonging to whichever app produced them - the Rust side can't open one, and
 * the grant behind it can be revoked the moment the picker closes. So anything
 * being sent is copied into our own storage first.
 */
object AttachmentFiles {

    /** Where sent copies and downloaded attachments live. */
    fun dir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    /**
     * Copies a picked file into our own storage and describes it for sending.
     *
     * Returns null when the URI can't be read - a revoked grant, or a provider
     * that has gone away - rather than sending a zero-byte file.
     */
    fun stage(context: Context, uri: Uri, attachmentId: String): OutgoingFileInfo? {
        val name = displayName(context, uri) ?: "attachment"
        val mime = context.contentResolver.getType(uri)
            ?: guessMime(name)
            ?: "application/octet-stream"
        val target = File(dir(context), "$attachmentId-$name")

        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "couldn't open $uri" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            OutgoingFileInfo(
                path = target.absolutePath,
                name = name,
                mimeType = mime,
                utiType = utiFor(mime),
            )
        }.onFailure { Log.e(TAG, "couldn't stage $uri", it) }.getOrNull()
    }

    data class OutgoingFileInfo(
        val path: String,
        val name: String,
        val mimeType: String,
        val utiType: String,
    )

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun guessMime(name: String): String? =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

    /**
     * Apple's type identifier for a MIME type.
     *
     * The recipient uses this to decide how to present the file, so getting it
     * wrong shows a photo as a generic document rather than an image. Only the
     * common ones are named; anything else falls back to a type that means
     * "some data", which is honest.
     */
    fun utiFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "public.jpeg"
        "image/png" -> "public.png"
        "image/gif" -> "com.compuserve.gif"
        "image/heic" -> "public.heic"
        "image/heif" -> "public.heif"
        "image/webp" -> "org.webmproject.webp"
        "video/mp4" -> "public.mpeg-4"
        "video/quicktime" -> "com.apple.quicktime-movie"
        "audio/mpeg", "audio/mp3" -> "public.mp3"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> "public.mpeg-4-audio"
        "audio/amr" -> "org.3gpp.adaptive-multi-rate-audio"
        "audio/wav", "audio/x-wav" -> "com.microsoft.waveform-audio"
        "application/pdf" -> "com.adobe.pdf"
        "text/plain" -> "public.plain-text"
        "text/vcard", "text/x-vcard" -> "public.vcard"
        else -> when {
            mime.startsWith("image/") -> "public.image"
            mime.startsWith("video/") -> "public.movie"
            mime.startsWith("audio/") -> "public.audio"
            else -> "public.data"
        }
    }

    private const val TAG = "AttachmentFiles"
}
