package com.leo.imessage.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.leo.imessage.data.TapbackKind

/** The six classic tapbacks as the emoji they actually are. */
fun TapbackKind.emojiGlyph(): String = when (this) {
    TapbackKind.HEART -> "\u2764\ufe0f"
    TapbackKind.THUMBS_UP -> "\ud83d\udc4d"
    TapbackKind.THUMBS_DOWN -> "\ud83d\udc4e"
    TapbackKind.HAHA -> "\ud83d\ude02"
    TapbackKind.EXCLAIM -> "\u203c\ufe0f"
    TapbackKind.QUESTION -> "\u2753"
    TapbackKind.ANY_EMOJI -> "\ud83d\ude42"
}

@Composable
fun TapbackIcon(
    kind: TapbackKind,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    fontSize: TextUnit = 15.sp,
    color: Color = Color.Unspecified,
) {
    Text(
        text = emoji ?: kind.emojiGlyph(),
        fontSize = fontSize,
        modifier = modifier,
    )
}
