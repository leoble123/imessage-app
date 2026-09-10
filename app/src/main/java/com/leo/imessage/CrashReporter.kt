package com.leo.imessage

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches crashes and keeps the stack trace so it can be read on next launch.
 *
 * This app is sideloaded onto a phone with no development tools attached, so
 * the usual way of finding out why something crashed - watching logcat - isn't
 * available. Without this, a crash report is "it closed", which is not enough
 * to fix anything, and the alternative is guessing.
 *
 * The default handler is still called afterwards, so the app dies exactly as
 * it would have. Nothing here changes behaviour; it only leaves a note.
 */
object CrashReporter {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            // Chain rather than swallow: swallowing would leave the process
            // alive in a broken state, which is worse than crashing.
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash, if there was one. */
    fun lastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val report = buildString {
            appendLine("Echo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine(when_)
            appendLine()
            append(trace)
        }
        File(context.filesDir, FILE).writeText(report)
    }
}
