package com.leo.imessage.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) AppPalette.Dark else AppPalette.Light

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

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appleTypography(),
            content = content,
        )
    }
}
