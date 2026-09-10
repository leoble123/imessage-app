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
import androidx.compose.foundation.layout.size
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
import com.leo.imessage.ui.components.glassSource
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.leo.imessage.data.Handles
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
    /**
     * Starts a conversation with a handle that isn't in the list yet.
     *
     * Without this the screen can only reopen threads that already exist,
     * which on a fresh account means it can't do anything at all.
     */
    onStartNew: (String) -> Unit = {},
    /** Set when the last attempt failed - e.g. the handle isn't on iMessage. */
    error: String? = null,
) {
    val palette = LocalPalette.current
    val hazeState = remember { HazeState() }
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
            Modifier.fillMaxSize().glassSource(hazeState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 118.dp,
                bottom = 24.dp,
            ),
        ) {
            // Anything that could be an address or a number gets an explicit
            // row, so a handle you've never messaged is reachable by typing it.
            val typed = to.trim()
            val looksLikeHandle = typed.length >= 3 && (
                typed.contains('@') ||
                    typed.count { it.isDigit() } >= 7
                )
            val alreadyListed = filtered.any {
                it.first.handle.equals(Handles.normalize(typed), ignoreCase = true)
            }
            if (looksLikeHandle && !alreadyListed) {
                item(key = "new:$typed") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onStartNew(typed) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("→", color = Color.White, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Message $typed",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.label,
                            )
                            Text(
                                "Start a new conversation",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                        }
                    }
                }
            }

            if (error != null) {
                item(key = "error") {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.destructive,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

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
            hazeState = hazeState,
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
