package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.AppleColors
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.util.Change
import com.leo.imessage.util.ChangeKind

/**
 * One line of a release, with its kind as a coloured pill.
 *
 * The colours carry the same meaning they do everywhere else in the app -
 * blue for something to try, green for something that works now, orange for
 * something that got out of its own way - so the shape of a release is
 * readable before any of the words are.
 */
@Composable
fun ChangeRow(change: Change, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val tint = change.kind.tint(palette.isDark)

    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                // A fixed width so the text of every line starts at the same
                // place; a pill that shrinks to its word gives a ragged left
                // edge down the whole list.
                .widthIn(min = 52.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tint.copy(alpha = if (palette.isDark) 0.22f else 0.14f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                change.kind.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            change.text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.label,
        )
    }
}

/** The releases, laid out as they appear in both the card and the full list. */
@Composable
fun ChangeList(changes: List<Change>, modifier: Modifier = Modifier) {
    Column(modifier) {
        changes.forEachIndexed { index, change ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            ChangeRow(change)
        }
    }
}

private fun ChangeKind.tint(dark: Boolean): Color = when (this) {
    ChangeKind.NEW -> if (dark) AppleColors.BlueDark else AppleColors.Blue
    ChangeKind.FIXED -> if (dark) AppleColors.GreenDark else AppleColors.Green
    ChangeKind.BETTER -> AppleColors.Orange
}
