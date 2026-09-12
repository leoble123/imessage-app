package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    // A disc, and specifically not two circles floating in a square.
    //
    // The cluster hangs off the top-right and bottom-left corners, so its
    // silhouette was a diagonal with two empty corners - while everything
    // drawn concentric with it (the live ring, the unread level rising
    // inside it) is a circle of the full size. The result read as
    // misaligned because it was: the ring was true to the frame and the
    // thing inside the frame was not.
    //
    // Clipping to a circle and tinting the ground with the group's own
    // colours gives the cluster an outline to belong to, and puts the
    // avatar, the ring and the liquid on the same edge. It also brings a
    // group into line with a one-to-one chat, which has always been a
    // filled disc.
    val seed = remember(contacts) { contacts.joinToString(",") { it.id } }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarGradientFor(seed), alpha = 0.30f),
    ) {
        // Smaller and inset, so both sit inside the disc instead of being
        // shaved by the clip that now defines its edge.
        val small = size * 0.54f
        val inset = size * 0.04f
        val back = contacts.getOrNull(1)
        val front = contacts.getOrNull(0)

        if (back != null) {
            Avatar(
                back,
                small,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = inset, end = inset),
            )
        }
        if (front != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = inset, bottom = inset)
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
