package com.leo.imessage.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.chatTintFor
import com.leo.imessage.util.InboxSummary
import com.leo.imessage.util.relativeTimeLabel
import dev.chrisbanes.haze.HazeState

/**
 * Everything waiting, in one panel, reached by pulling the list down.
 *
 * The list already shows the same conversations, so this earns its place by
 * showing only what is unresolved and by being one gesture from anywhere -
 * no menu, no screen. It also puts "mark it all read" somewhere it can
 * actually be found, which until now meant opening every thread in turn.
 */
@Composable
fun InboxSummarySheet(
    summary: InboxSummary,
    hazeState: HazeState?,
    onOpenChat: (Chat) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = rememberPanelProgress(true) ?: return

    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize()) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = null,
            onDismiss = onDismiss,
        )
        GlassSheet(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = 460.dp.toPx() * (1f - progress.value) },
            hazeState = hazeState,
            tintAlpha = 0.76f,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
            ) {
                // The grab handle, so the panel reads as something that came
                // from the gesture rather than something that appeared.
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.tertiaryLabel)
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    summary.headline(),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                if (summary.isEmpty) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Nothing is waiting on you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryLabel,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    return@Column
                }

                if (summary.questions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${summary.questions.size} waiting on an answer",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryLabel,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    summary.waiting.forEach { chat ->
                        WaitingRow(chat = chat, onOpen = { onDismiss(); onOpenChat(chat) })
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(palette.fieldBackground)
                        .clickable { onMarkAllRead(); onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Mark all as read",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitingRow(chat: Chat, onOpen: () -> Unit) {
    val palette = LocalPalette.current
    val tint = chatTintFor(chat.avatarSeed)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.isGroup) {
            GroupAvatar(chat.participants, 34.dp)
        } else {
            Avatar(chat.participants.first(), 34.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    chat.lastMessage?.timestamp?.let { relativeTimeLabel(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.tertiaryLabel,
                )
            }
            Text(
                chat.lastMessage?.text?.takeIf { it.isNotBlank() } ?: "Attachment",
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(tint)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                if (chat.unreadCount > 99) "99+" else "${chat.unreadCount}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}
