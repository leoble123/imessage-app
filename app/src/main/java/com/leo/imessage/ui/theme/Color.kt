package com.leo.imessage.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * iOS system colors, sampled from iOS 18 light/dark. These are the actual
 * system values rather than approximations - getting these exactly right is
 * most of what makes a UI read as "Apple" rather than "Apple-ish".
 */
object AppleColors {
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val GreenDark = Color(0xFF30D158)
    val Red = Color(0xFFFF3B30)
    val RedDark = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Purple = Color(0xFFAF52DE)
    val Pink = Color(0xFFFF2D55)
    val Indigo = Color(0xFF5856D6)
    val Teal = Color(0xFF5AC8FA)

    val Gray = Color(0xFF8E8E93)
    val Gray2 = Color(0xFFAEAEB2)
    val Gray3 = Color(0xFFC7C7CC)
    val Gray4 = Color(0xFFD1D1D6)
    val Gray5 = Color(0xFFE5E5EA)
    val Gray6 = Color(0xFFF2F2F7)

    val GrayDark = Color(0xFF8E8E93)
    val Gray2Dark = Color(0xFF636366)
    val Gray3Dark = Color(0xFF48484A)
    val Gray4Dark = Color(0xFF3A3A3C)
    val Gray5Dark = Color(0xFF2C2C2E)
    val Gray6Dark = Color(0xFF1C1C1E)
}

/**
 * Semantic color roles for this app. Kept as an explicit palette object rather
 * than leaning on Material3's ColorScheme, because iMessage's surfaces don't
 * map cleanly onto Material roles (the bubble colors especially).
 */
@Immutable
data class AppPalette(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val accent: Color,
    val outgoingBubble: Brush,
    /** The gradient's stops, so translucent variants can be rebuilt from it. */
    val outgoingBubbleColors: List<Color>,
    val outgoingBubbleFlat: Color,
    val outgoingText: Color,
    val incomingBubble: Color,
    val incomingText: Color,
    val smsBubble: Brush,
    val smsBubbleColors: List<Color>,
    val smsBubbleFlat: Color,
    val fieldBackground: Color,
    val glassTint: Color,
    val destructive: Color,
) {
    companion object {
        val Light = AppPalette(
            isDark = false,
            background = Color.White,
            groupedBackground = AppleColors.Gray6,
            surface = Color.White,
            surfaceElevated = Color.White,
            separator = Color(0x333C3C43),
            label = Color.Black,
            secondaryLabel = Color(0x993C3C43),
            tertiaryLabel = Color(0x4D3C3C43),
            accent = AppleColors.Blue,
            outgoingBubble = Brush.verticalGradient(
                listOf(Color(0xFF1E8FFF), Color(0xFF0A6CFF))
            ),
            outgoingBubbleColors = listOf(Color(0xFF1E8FFF), Color(0xFF0A6CFF)),
            outgoingBubbleFlat = AppleColors.Blue,
            outgoingText = Color.White,
            incomingBubble = AppleColors.Gray5,
            incomingText = Color.Black,
            smsBubble = Brush.verticalGradient(
                listOf(Color(0xFF4CD964), Color(0xFF2FB94E))
            ),
            smsBubbleColors = listOf(Color(0xFF4CD964), Color(0xFF2FB94E)),
            smsBubbleFlat = AppleColors.Green,
            fieldBackground = Color(0xFFF2F2F7),
            glassTint = Color(0xCCFFFFFF),
            destructive = AppleColors.Red,
        )

        val Dark = AppPalette(
            isDark = true,
            background = Color.Black,
            groupedBackground = Color.Black,
            surface = AppleColors.Gray6Dark,
            surfaceElevated = AppleColors.Gray5Dark,
            separator = Color(0x40545458),
            label = Color.White,
            secondaryLabel = Color(0x99EBEBF5),
            tertiaryLabel = Color(0x4DEBEBF5),
            accent = AppleColors.BlueDark,
            outgoingBubble = Brush.verticalGradient(
                listOf(Color(0xFF2B95FF), Color(0xFF0A72F5))
            ),
            outgoingBubbleColors = listOf(Color(0xFF2B95FF), Color(0xFF0A72F5)),
            outgoingBubbleFlat = AppleColors.BlueDark,
            outgoingText = Color.White,
            incomingBubble = Color(0xFF26262A),
            incomingText = Color.White,
            smsBubble = Brush.verticalGradient(
                listOf(Color(0xFF3ADB58), Color(0xFF27A83F))
            ),
            smsBubbleColors = listOf(Color(0xFF3ADB58), Color(0xFF27A83F)),
            smsBubbleFlat = AppleColors.GreenDark,
            fieldBackground = Color(0xFF1C1C1E),
            glassTint = Color(0xB3000000),
            destructive = AppleColors.RedDark,
        )
    }
}

/**
 * Deterministic avatar gradient, picked from the contact's identifier so the
 * same person always gets the same colors across launches.
 */
private val avatarPairs = listOf(
    Color(0xFF62C1FF) to Color(0xFF2A7BFF),
    Color(0xFFFF9F6E) to Color(0xFFFF5E3A),
    Color(0xFF7AE582) to Color(0xFF29B765),
    Color(0xFFCE9CFF) to Color(0xFF8A4FFF),
    Color(0xFFFFD36E) to Color(0xFFFFA51F),
    Color(0xFFFF8FB1) to Color(0xFFFF2D6F),
    Color(0xFF7BE7DA) to Color(0xFF1FB6A6),
    Color(0xFFA8B4FF) to Color(0xFF5B6BFF),
)

fun avatarColorsFor(seed: String): Pair<Color, Color> {
    val idx = ((seed.hashCode() % avatarPairs.size) + avatarPairs.size) % avatarPairs.size
    return avatarPairs[idx]
}

fun avatarGradientFor(seed: String): Brush {
    val (a, b) = avatarColorsFor(seed)
    return Brush.linearGradient(listOf(a, b))
}

/**
 * One colour standing for a whole conversation - its ring, its unread pill,
 * its accent anywhere else it needs one.
 *
 * Deliberately the deeper stop of the same pair the avatar is painted with,
 * rather than a second palette that happens to look similar. A person is one
 * colour in this app; two systems that agree today drift apart the first time
 * either is touched, and then the same thread is teal in one place and blue
 * in another.
 */
fun chatTintFor(seed: String): Color = avatarColorsFor(seed).second

/** The pair itself, for a ring that wants the gradient rather than the tint. */
fun chatRingColorsFor(seed: String): List<Color> =
    avatarColorsFor(seed).let { listOf(it.first, it.second) }

/**
 * A conversation's own bubble colours, derived from who it's with.
 *
 * iMessage is blue, grey, and nothing else - which is fine for one thread
 * and monotonous across twenty. Hashing the chat's identifier into a hue
 * gives each conversation a stable colour of its own: the same person is
 * always the same colour, so you can tell which thread you're in before
 * you've read a single word, and nobody has to pick anything.
 */
fun colorfulBubbleFor(seed: String, dark: Boolean): List<Color> {
    val palettes = if (dark) {
        listOf(
            listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),   // azure
            listOf(Color(0xFF8B5CF6), Color(0xFF5B21B6)),   // violet
            listOf(Color(0xFFEC4899), Color(0xFF9D174D)),   // rose
            listOf(Color(0xFF10B981), Color(0xFF065F46)),   // jade
            listOf(Color(0xFFF59E0B), Color(0xFFB45309)),   // amber
            listOf(Color(0xFF06B6D4), Color(0xFF0E7490)),   // cyan
            listOf(Color(0xFFF43F5E), Color(0xFF9F1239)),   // coral
            listOf(Color(0xFF6366F1), Color(0xFF3730A3)),   // indigo
        )
    } else {
        listOf(
            listOf(Color(0xFF4C9BFF), Color(0xFF1668E3)),
            listOf(Color(0xFFA478FF), Color(0xFF6D33D6)),
            listOf(Color(0xFFFF6FB0), Color(0xFFD62A75)),
            listOf(Color(0xFF34D9A0), Color(0xFF0E9A6C)),
            listOf(Color(0xFFFFBB4D), Color(0xFFE08300)),
            listOf(Color(0xFF3ECEE4), Color(0xFF1195AE)),
            listOf(Color(0xFFFF7A7A), Color(0xFFDB3A3A)),
            listOf(Color(0xFF7C86FF), Color(0xFF4148D6)),
        )
    }
    val index = ((seed.hashCode() % palettes.size) + palettes.size) % palettes.size
    return palettes[index]
}
