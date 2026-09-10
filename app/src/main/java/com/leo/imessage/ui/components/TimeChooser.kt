package com.leo.imessage.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

/**
 * Picks an exact date and time, using the platform's own dialogs.
 *
 * Deliberately not a hand-rolled Compose wheel. This is one of the few places
 * where the system control genuinely wins: it already knows the user's
 * 12/24-hour preference, their locale's date order, and how their particular
 * OEM skins a picker - and getting any of those wrong is far more jarring
 * than the dialog not matching the app's glass.
 *
 * Date first, then time, because picking 9am and *then* discovering you meant
 * tomorrow means doing it twice.
 */
fun pickDateTime(
    context: Context,
    initial: Long = System.currentTimeMillis() + 3_600_000L,
    onPicked: (Long) -> Unit,
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }

    val dateDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val chosen = Calendar.getInstance().apply {
                timeInMillis = initial
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    chosen.set(Calendar.HOUR_OF_DAY, hour)
                    chosen.set(Calendar.MINUTE, minute)
                    chosen.set(Calendar.SECOND, 0)
                    // A time already past is almost always meant for
                    // tomorrow - nobody schedules a message into the past.
                    if (chosen.timeInMillis <= System.currentTimeMillis()) {
                        chosen.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    onPicked(chosen.timeInMillis)
                },
                chosen.get(Calendar.HOUR_OF_DAY),
                chosen.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(context),
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    )
    // No point offering yesterday.
    dateDialog.datePicker.minDate = System.currentTimeMillis() - 60_000
    dateDialog.show()
}
