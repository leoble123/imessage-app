package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Attachment
import com.leo.imessage.ui.theme.LocalPalette

/**
 * Photo attachment.
 *
 * There's no image loader wired up yet, so this renders a deterministic
 * gradient derived from the attachment id - stable per attachment, and varied
 * enough across a thread that the layout reads the way a real photo thread
 * would rather than as identical grey boxes.
 */
@Composable
fun PhotoAttachment(
    attachment: Attachment,
    modifier: Modifier = Modifier,
) {
    val seed = attachment.id.hashCode()
    val palettes = listOf(
        listOf(Color(0xFF3A6073), Color(0xFF16222A)),
        listOf(Color(0xFFEE9CA7), Color(0xFFFFDDE1)),
        listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        listOf(Color(0xFF654EA3), Color(0xFFEAAFC8)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    )
    val colors = palettes[((seed % palettes.size) + palettes.size) % palettes.size]
    // Vary the aspect a little so a run of photos isn't a uniform grid.
    val tall = (seed / 7) % 2 == 0

    Box(
        modifier = modifier
            .width(212.dp)
            .height(if (tall) 268.dp else 158.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = colors,
                    start = Offset.Zero,
                    end = Offset(600f, 900f),
                )
            ),
    )
}

@Composable
fun FileAttachment(
    attachment: Attachment,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.incomingBubble)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.accent),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                attachment.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.label,
            )
            Text(
                attachment.mimeType,
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryLabel,
            )
        }
    }
}
