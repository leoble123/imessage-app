package com.leo.imessage.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class ChatBackground(
    val id: String,
    val name: String,
    /** Null means "use the theme's plain background". */
    val brush: Brush?,
    /**
     * Whether bubbles and bars sitting on this background should switch to
     * their dark treatment. A dark background under a light theme otherwise
     * gets white bars and opaque light bubbles, which looks broken.
     */
    val isDark: Boolean = true,
)

/**
 * Conversation backgrounds.
 *
 * These are deliberately low-contrast: a thread's job is to make bubbles
 * legible, so anything busy enough to fight the text is worse than plain,
 * however nice it looks in a picker.
 */
val ChatBackgrounds: List<ChatBackground> = listOf(
    ChatBackground("none", "Default", null, isDark = false),
    ChatBackground(
        "dusk", "Dusk",
        Brush.verticalGradient(listOf(Color(0xFF11131A), Color(0xFF1E2333))),
    ),
    ChatBackground(
        "ember", "Ember",
        Brush.verticalGradient(listOf(Color(0xFF1A1113), Color(0xFF33201E))),
    ),
    ChatBackground(
        "moss", "Moss",
        Brush.verticalGradient(listOf(Color(0xFF0F1613), Color(0xFF1B2A24))),
    ),
    ChatBackground(
        "tide", "Tide",
        Brush.linearGradient(listOf(Color(0xFF0E1620), Color(0xFF16303B))),
    ),
    ChatBackground(
        "linen", "Linen",
        Brush.verticalGradient(listOf(Color(0xFFF7F4EF), Color(0xFFE9E4DC))),
        isDark = false,
    ),
    ChatBackground(
        "blush", "Blush",
        Brush.verticalGradient(listOf(Color(0xFFFDF1F3), Color(0xFFF6E2E8))),
        isDark = false,
    ),
    // Saturated options, so the glass has something worth transmitting.
    ChatBackground(
        "indigo", "Indigo",
        Brush.linearGradient(listOf(Color(0xFF1B1464), Color(0xFF4B1E8C), Color(0xFF0C1A4D))),
    ),
    ChatBackground(
        "sunset", "Sunset",
        Brush.verticalGradient(listOf(Color(0xFF3A0E3D), Color(0xFF8E2A4A), Color(0xFFC24E2E))),
    ),
    ChatBackground(
        "teal", "Teal",
        Brush.linearGradient(listOf(Color(0xFF04262E), Color(0xFF0B6B70), Color(0xFF11304A))),
    ),
    ChatBackground(
        "sorbet", "Sorbet",
        Brush.verticalGradient(listOf(Color(0xFFFFE9C7), Color(0xFFFFC4C4), Color(0xFFE4C6FF))),
        isDark = false,
    ),
)

fun backgroundById(id: String): ChatBackground =
    ChatBackgrounds.firstOrNull { it.id == id } ?: ChatBackgrounds.first()
