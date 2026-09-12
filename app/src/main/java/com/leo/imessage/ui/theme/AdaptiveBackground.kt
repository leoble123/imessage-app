package com.leo.imessage.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.leo.imessage.data.Conditions
import com.leo.imessage.data.Sky
import java.util.Calendar

/** The ids the picker offers that are worked out rather than looked up. */
object AdaptiveBackgrounds {
    const val TIME = "adaptive_time"
    const val WEATHER = "adaptive_weather"
    const val BOTH = "adaptive_both"

    val ids = setOf(TIME, WEATHER, BOTH)

    fun isAdaptive(id: String) = id in ids
}

/** Roughly where the sun is, which is all a colour field needs to know. */
private enum class Daypart { NIGHT, DAWN, MORNING, AFTERNOON, DUSK }

private fun daypartFor(hour: Int): Daypart = when (hour) {
    in 5..7 -> Daypart.DAWN
    in 8..11 -> Daypart.MORNING
    in 12..16 -> Daypart.AFTERNOON
    in 17..19 -> Daypart.DUSK
    else -> Daypart.NIGHT
}

/**
 * A background that follows the clock.
 *
 * Dark before dawn and after dusk whatever the theme says, because the point
 * is that the room you are in at 3am does not look like the one you are in at
 * noon. The two ends of the day get the colour - low sun is the only time the
 * sky is interesting - and the middle of the day is deliberately the plainest
 * of the five.
 */
fun timeBackground(hour: Int = nowHour()): ChatBackground = when (daypartFor(hour)) {
    Daypart.NIGHT -> ChatBackground(
        AdaptiveBackgrounds.TIME, "Time of Day",
        Brush.verticalGradient(listOf(Color(0xFF05070F), Color(0xFF0D1220))),
        drift = listOf(Color(0xFF1B2A6B), Color(0xFF2E1A5E), Color(0xFF0E3552)),
    )
    Daypart.DAWN -> ChatBackground(
        AdaptiveBackgrounds.TIME, "Time of Day",
        Brush.verticalGradient(listOf(Color(0xFF1A1024), Color(0xFF32203A))),
        drift = listOf(Color(0xFFFF8A5B), Color(0xFFB05C9E), Color(0xFF4A5FC1)),
    )
    Daypart.MORNING -> ChatBackground(
        AdaptiveBackgrounds.TIME, "Time of Day",
        Brush.verticalGradient(listOf(Color(0xFF0A1520), Color(0xFF14293A))),
        drift = listOf(Color(0xFF56B8E8), Color(0xFF7FD4C1), Color(0xFF3E7FC4)),
    )
    Daypart.AFTERNOON -> ChatBackground(
        AdaptiveBackgrounds.TIME, "Time of Day",
        Brush.verticalGradient(listOf(Color(0xFF091621), Color(0xFF102B3C))),
        drift = listOf(Color(0xFF3FA0DA), Color(0xFF5FC6D8), Color(0xFF2E6FA8)),
    )
    Daypart.DUSK -> ChatBackground(
        AdaptiveBackgrounds.TIME, "Time of Day",
        Brush.verticalGradient(listOf(Color(0xFF1A0E18), Color(0xFF361A28))),
        drift = listOf(Color(0xFFFF7043), Color(0xFFD8456F), Color(0xFF6A3C9E)),
    )
}

/**
 * A background that follows the sky.
 *
 * Day and night are kept apart within each condition, because rain at two in
 * the afternoon and rain at midnight are not the same colour and pretending
 * otherwise is what makes weather themes look like stickers. Everything stays
 * desaturated and slow: this sits behind text all day.
 */
fun weatherBackground(conditions: Conditions, id: String = AdaptiveBackgrounds.WEATHER): ChatBackground {
    val day = conditions.isDay
    val (base, drift) = when (conditions.sky) {
        Sky.CLEAR -> if (day) {
            listOf(Color(0xFF06131F), Color(0xFF0E3050)) to
                listOf(Color(0xFF49B6F0), Color(0xFF7FE0E8), Color(0xFF2C79C9))
        } else {
            listOf(Color(0xFF03060E), Color(0xFF0A1024)) to
                listOf(Color(0xFF2B3E9E), Color(0xFF172A6B), Color(0xFF4A2E8C))
        }
        Sky.CLOUDY -> if (day) {
            listOf(Color(0xFF0D1219), Color(0xFF1D2733)) to
                listOf(Color(0xFF7C93A8), Color(0xFF9FB4C4), Color(0xFF546B80))
        } else {
            listOf(Color(0xFF06080D), Color(0xFF10151E)) to
                listOf(Color(0xFF3A4658), Color(0xFF27354A), Color(0xFF4C5570))
        }
        Sky.OVERCAST -> listOf(Color(0xFF0A0D11), Color(0xFF171C22)) to
            listOf(Color(0xFF5C6874), Color(0xFF78848F), Color(0xFF424C57))
        Sky.FOG -> listOf(Color(0xFF0E1113), Color(0xFF1E2326)) to
            listOf(Color(0xFF8C9AA0), Color(0xFFA9B6BA), Color(0xFF6B777C))
        Sky.RAIN -> listOf(Color(0xFF060B12), Color(0xFF10202E)) to
            listOf(Color(0xFF3E7391), Color(0xFF2A5570), Color(0xFF56A0B4))
        Sky.SNOW -> listOf(Color(0xFF0B0F14), Color(0xFF1A222C)) to
            listOf(Color(0xFFBFD4E4), Color(0xFF8FA8BE), Color(0xFFE2EDF5))
        Sky.STORM -> listOf(Color(0xFF05060A), Color(0xFF121020)) to
            listOf(Color(0xFF4A3F86), Color(0xFF6E5AB0), Color(0xFF23306B))
    }
    return ChatBackground(
        id = id,
        name = if (id == AdaptiveBackgrounds.BOTH) "Time & Weather" else "Weather",
        brush = Brush.verticalGradient(base),
        drift = drift,
    )
}

/**
 * Both, with the clock deciding the light and the sky deciding the colour.
 *
 * Not an average of the two. The weather sets which palette is used and the
 * daypart sets how lit it is, which is the way round that matches what you
 * see out of a window: overcast at dawn is still dawn-coloured, and clear at
 * midnight is not bright.
 */
fun timeAndWeatherBackground(
    conditions: Conditions,
    hour: Int = nowHour(),
): ChatBackground {
    val part = daypartFor(hour)
    val lit = part != Daypart.NIGHT
    val weather = weatherBackground(
        conditions.copy(isDay = lit && conditions.isDay),
        id = AdaptiveBackgrounds.BOTH,
    )
    // The low-sun hours borrow their warmth from the time palette, so a
    // clear dusk is orange rather than the same blue as a clear noon.
    return when (part) {
        Daypart.DAWN, Daypart.DUSK -> weather.copy(
            drift = weather.drift.take(2) + timeBackground(hour).drift.take(2),
        )
        else -> weather
    }
}

private fun nowHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
