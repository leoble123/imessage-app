package com.leo.imessage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette

/**
 * The "4 Replies" affordance beneath a message that has a reply thread.
 *
 * Messages doesn't inline replies into the transcript - the original keeps
 * its place and grows a short hooked connector down to a count, and tapping
 * that opens the thread. Getting this right is what stops a busy group chat
 * from turning into a wall of duplicated quotes.
 */
@Composable
fun ReplyChainLink(
    count: Int,
    outgoing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    Row(
        modifier = modifier
            .padding(top = 1.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val connector = @Composable {
            Canvas(Modifier.size(width = 22.dp, height = 16.dp)) {
                val stroke = 1.6.dp.toPx()
                val inset = 9.dp.toPx()
                // Drops out of the bubble's bottom edge, then hooks toward
                // the label. Mirrored for outgoing so the hook always turns
                // inward, toward the middle of the screen.
                val startX = if (outgoing) size.width - inset else inset
                val endX = if (outgoing) 0f else size.width
                val dir = if (outgoing) -1f else 1f
                // Circular-arc corner, same cubic approximation the bubble
                // silhouette uses, so the two curves read as one family.
                val k = 0.5523f
                val r = 7.dp.toPx()
                val bottom = size.height - stroke / 2f
                val path = Path().apply {
                    moveTo(startX, 0f)
                    lineTo(startX, bottom - r)
                    cubicTo(
                        startX, bottom - r + r * k,
                        startX + dir * (r - r * k), bottom,
                        startX + dir * r, bottom,
                    )
                    lineTo(endX, bottom)
                }
                drawPath(
                    path = path,
                    color = palette.tertiaryLabel,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        if (!outgoing) connector()
        Text(
            text = if (count == 1) "1 Reply" else "$count Replies",
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (outgoing) connector()
    }
}
