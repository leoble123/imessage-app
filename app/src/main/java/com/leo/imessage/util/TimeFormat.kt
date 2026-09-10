package com.leo.imessage.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = cal(a); val cb = cal(b)
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/** Chat list stamp: time today, "Yesterday", weekday this week, else a date. */
fun relativeTimeLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val dayMs = 86_400_000L
    return when {
        isSameDay(ms, now) -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
        isSameDay(ms, now - dayMs) -> "Yesterday"
        now - ms < 7 * dayMs -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("M/d/yy", Locale.getDefault()).format(Date(ms))
    }
}

/** The centered divider inside a thread, e.g. "Today 8:14 PM". */
fun conversationTimestampHeader(ms: Long, now: Long = System.currentTimeMillis()): String {
    val dayMs = 86_400_000L
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
    return when {
        isSameDay(ms, now) -> "Today $time"
        isSameDay(ms, now - dayMs) -> "Yesterday $time"
        now - ms < 7 * dayMs ->
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms)) + " $time"
        else ->
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms)) + " $time"
    }
}

/** Compact stamp shown by the swipe-for-timestamps gesture. */
fun messageStamp(ms: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

/** Today at a given wall-clock time, rolled to tomorrow if already past. */
fun todayAt(hour: Int, minute: Int): Long {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= System.currentTimeMillis()) {
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}

/** Tomorrow at a given wall-clock time. */
fun tomorrowAt(hour: Int, minute: Int): Long =
    java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
