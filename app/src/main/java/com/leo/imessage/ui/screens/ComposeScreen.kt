package com.leo.imessage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.data.Contact
import com.leo.imessage.ui.components.Avatar
import com.leo.imessage.ui.components.GlassSurface
import com.leo.imessage.ui.theme.LocalPalette

/**
 * New Message. Typing filters the known contacts live, matching Messages'
 * "To:" field behaviour.
 */
@Composable
fun ComposeScreen(
    chats: List<Chat>,
    onBack: () -> Unit,
    onPick: (Chat) -> Unit,
) {
    val palette = LocalPalette.current
    var to by remember { mutableStateOf("") }

    val contacts = remember(chats) {
        chats.flatMap { chat -> chat.participants.map { it to chat } }
            .distinctBy { it.first.id }
    }
    val filtered = remember(contacts, to) {
        if (to.isBlank()) contacts
        else contacts.filter { (c, _) ->
            c.displayName.contains(to, ignoreCase = true) ||
                c.handle.contains(to, ignoreCase = true)
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 118.dp,
                bottom = 24.dp,
            ),
        ) {
            items(filtered, key = { it.first.id }) { (contact, chat) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(chat) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(contact, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            contact.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.label,
                        )
                        Text(
                            contact.handle,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryLabel,
                        )
                    }
                }
                Box(
                    Modifier
                        .padding(start = 68.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(palette.separator)
                )
            }
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            hairlineAtBottom = true,
        ) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.accent,
                        modifier = Modifier.clickable { onBack() },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "New Message",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.label,
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(46.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "To:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.secondaryLabel,
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = to,
                        onValueChange = { to = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(palette.separator)
                )
            }
        }
    }
}
