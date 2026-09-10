package com.leo.imessage.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val LocalPalette = staticCompositionLocalOf { AppPalette.Dark }

/**
 * iOS type scale. SF Pro isn't redistributable, so this uses the platform
 * default family at SF's actual sizes/weights/tracking - which lands much
 * closer to the real thing than Material's defaults do.
 */
private fun appleTypography(): Typography {
    fun style(
        size: Int,
        weight: FontWeight,
        lineHeight: Int,
        tracking: Double = 0.0,
    ) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    return Typography(
        // Large title (nav bar expanded)
        displaySmall = style(34, FontWeight.Bold, 41, 0.37),
        // Title 1 / 2
        headlineLarge = style(28, FontWeight.Bold, 34, 0.36),
        headlineMedium = style(22, FontWeight.Bold, 28, 0.35),
        // Headline (nav title, chat name)
        titleLarge = style(17, FontWeight.SemiBold, 22, -0.41),
        titleMedium = style(16, FontWeight.SemiBold, 21, -0.32),
        titleSmall = style(15, FontWeight.SemiBold, 20, -0.23),
        // Body - message text lives here
        bodyLarge = style(17, FontWeight.Normal, 22, -0.41),
        bodyMedium = style(15, FontWeight.Normal, 20, -0.23),
        bodySmall = style(13, FontWeight.Normal, 18, -0.08),
        // Footnote / caption - timestamps, status lines
        labelLarge = style(15, FontWeight.Medium, 20, -0.23),
        labelMedium = style(12, FontWeight.Medium, 16, 0.0),
        labelSmall = style(11, FontWeight.Medium, 13, 0.07),
    )
}

@Composable
fun iMessageTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }
    val base = if (darkTheme) AppPalette.Dark else AppPalette.Light

    // OLED isn't just "darker" - it's true #000, which on these panels means
    // the pixels are switched off entirely. That only works if the surfaces
    // above it lift enough to still separate, so they move too rather than
    // leaving controls floating on a void.
    val oled = settings.themeMode == ThemeMode.OLED
    val accent = androidx.compose.ui.graphics.Color(
        if (darkTheme) settings.accentColor.dark else settings.accentColor.light
    )
    val palette = remember(base, oled, accent) {
        base.copy(
            accent = accent,
            background = if (oled) androidx.compose.ui.graphics.Color.Black else base.background,
            groupedBackground = if (oled) androidx.compose.ui.graphics.Color.Black
                else base.groupedBackground,
            surface = if (oled) androidx.compose.ui.graphics.Color(0xFF0B0B0D) else base.surface,
            surfaceElevated = if (oled) androidx.compose.ui.graphics.Color(0xFF141417)
                else base.surfaceElevated,
            // The outgoing bubble follows the accent, so picking a colour
            // actually changes the thing you look at most.
            outgoingBubbleColors = listOf(
                accent.copy(alpha = 1f),
                androidx.compose.ui.graphics.lerp(
                    accent,
                    androidx.compose.ui.graphics.Color.Black,
                    0.22f,
                ),
            ),
            outgoingBubbleFlat = accent,
        )
    }

    // Reduce Motion still wins outright; the profile shapes everything else.
    Motion.responseScale = if (settings.lowPowerAnimations) 0.45f
        else settings.motionProfile.scale

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.label,
            onSurface = palette.label,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.label,
            onSurface = palette.label,
        )
    }

    val view = LocalContext.current
    SideEffect {
        (view as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalSettings provides settings,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appleTypography(),
            content = content,
        )
    }
}
