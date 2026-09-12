package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
        if (contact.initials.isEmpty()) {
            // Nobody whose name we know. iOS draws a person here rather than
            // trying to make letters out of a phone number, and so does this.
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(size * 0.55f),
            )
        } else {
            Text(
                text = contact.initials,
                color = Color.White,
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
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
