package com.leo.imessage.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Whether a message is nothing but a few emoji.
 *
 * iMessage renders one to three bare emoji at triple size with no bubble at
 * all, and it's one of those details you don't consciously notice until it's
 * missing - a lone 👍 in a small grey capsule reads as a completely
 * different, much more grudging message than a big one floating on its own.
 *
 * Counts by code point rather than by char so surrogate pairs, skin-tone
 * modifiers, variation selectors and ZWJ sequences (👩‍👩‍👧 is one emoji made
 * of five code points) all come out right.
 */
fun isJumboEmoji(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    var visible = 0
    var i = 0
    while (i < trimmed.length) {
        val cp = trimmed.codePointAt(i)
        i += Character.charCount(cp)
        if (Character.isWhitespace(cp)) continue
        if (cp < 0x2000) return false

        when (Character.getType(cp).toByte()) {
            Character.OTHER_SYMBOL -> visible++
            // Joiners, variation selectors and skin tones ride along without
            // counting as separate emoji.
            Character.FORMAT,
            Character.NON_SPACING_MARK,
            Character.MODIFIER_SYMBOL,
            Character.ENCLOSING_MARK -> Unit
            else -> return false
        }
        if (visible > 3) return false
    }
    return visible in 1..3
}

private val LINK_PATTERN = Regex("""(https?://\S+|www\.\S+)""", RegexOption.IGNORE_CASE)

fun firstLinkIn(text: String): String? = LINK_PATTERN.find(text)?.value?.trimEnd('.', ',', ')')

/**
 * The link card under a message containing a URL.
 *
 * Deliberately not fetched: pulling the page to scrape a title and hero
 * image would mean a network request per message, on a thread you might be
 * scrolling through fast, for content the sender didn't choose. The domain
 * and path say enough to know where a tap goes.
 */
@Composable
fun LinkPreview(
    url: String,
    outgoing: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val normalized = remember(url) {
        if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
    }
    val host = remember(normalized) {
        runCatching { Uri.parse(normalized).host?.removePrefix("www.") }.getOrNull()
            ?: normalized
    }
    Column(
        modifier
            .widthIn(max = 262.dp)
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (outgoing) Color.White.copy(alpha = 0.16f)
                else Color.Black.copy(alpha = 0.08f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(
            text = host,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = normalized,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
