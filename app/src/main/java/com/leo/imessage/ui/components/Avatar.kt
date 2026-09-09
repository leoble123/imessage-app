package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.imessage.data.Contact
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.avatarGradientFor

@Composable
fun Avatar(
    contact: Contact,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarGradientFor(contact.id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = contact.initials,
            color = Color.White,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Group threads show a cluster of overlapping avatars, the way Messages does
 * for a conversation with more than one other person.
 */
@Composable
fun GroupAvatar(
    contacts: List<Contact>,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    if (contacts.size == 1) {
        Avatar(contacts.first(), size, modifier)
        return
    }

    Box(modifier = modifier.size(size)) {
        val small = size * 0.62f
        val back = contacts.getOrNull(1)
        val front = contacts.getOrNull(0)

        if (back != null) {
            Avatar(
                back,
                small,
                Modifier.align(Alignment.TopEnd),
            )
        }
        if (front != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(small + 3.dp)
                    .clip(CircleShape)
                    .background(palette.background),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(front, small)
            }
        }
    }
}
