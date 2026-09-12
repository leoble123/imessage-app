package com.leo.imessage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.ui.components.Avatar
import com.leo.imessage.ui.components.GlassSurface
import com.leo.imessage.ui.components.GroupAvatar
import com.leo.imessage.ui.components.ListSection
import com.leo.imessage.ui.components.SettingsDivider
import com.leo.imessage.ui.components.SettingsRow
import com.leo.imessage.ui.components.SettingsToggle
import com.leo.imessage.ui.components.glassSource
import com.leo.imessage.ui.theme.ChatBackgrounds
import com.leo.imessage.ui.theme.LocalPalette
import dev.chrisbanes.haze.HazeState

/**
 * The info screen behind a conversation's title - contact or group details,
 * per-thread settings, and background selection.
 */
@Composable
fun ChatDetailsScreen(
    chat: Chat,
    selectedBackgroundId: String,
    onSelectBackground: (String) -> Unit,
    onBack: () -> Unit,
    onSetMuted: (Boolean) -> Unit = {},
    onSetPinned: (Boolean) -> Unit = {},
    attachments: List<com.leo.imessage.data.Attachment> = emptyList(),
    onOpenAttachment: (com.leo.imessage.data.Attachment) -> Unit = {},
    onExport: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val hazeState = remember { HazeState() }
    val settings = com.leo.imessage.ui.theme.LocalSettings.current
    val photos = remember(attachments) { attachments.filter { it.isVisual } }

    Box(Modifier.fillMaxSize().background(palette.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = 92.dp, bottom = 40.dp),
        ) {
            // Header: big avatar and name, centered.
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (chat.isGroup) {
                    GroupAvatar(chat.participants, 96.dp)
                } else {
                    Avatar(chat.participants.first(), 96.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = chat.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.label,
                    textAlign = TextAlign.Center,
                )
                if (!chat.isGroup) {
                    Text(
                        // Raw handles carry a mailto:/tel: scheme nobody wants to read.
                        text = com.leo.imessage.data.Handles.display(chat.participants.first().handle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryLabel,
                    )
                } else {
                    Text(
                        text = "${chat.participants.size} people",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryLabel,
                    )
                }
            }

            ListSection(header = "Background") {
                com.leo.imessage.ui.components.BackgroundPicker(
                    selectedId = selectedBackgroundId,
                    onSelect = onSelectBackground,
                )
            }

            ListSection(header = if (chat.isGroup) "People" else "Contact") {
                chat.participants.forEachIndexed { i, contact ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(contact, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                contact.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.label,
                            )
                            Text(
                                com.leo.imessage.data.Handles.display(contact.handle),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                        }
                    }
                    if (i != chat.participants.lastIndex) SettingsDivider()
                }
            }

            ListSection(header = "Conversation") {
                SettingsToggle("Pin Conversation", chat.isPinned) { onSetPinned(it) }
                SettingsDivider()
                SettingsToggle("Hide Alerts", chat.isMuted) { onSetMuted(it) }
                SettingsDivider()
                SettingsToggle("Send Read Receipts", settings.sendReadReceipts) {
                    settings.sendReadReceipts = it
                }
            }

            ListSection(header = "Conversation Tools") {
                SettingsRow("Export Conversation", onClick = { onExport() })
            }

            if (photos.isNotEmpty()) {
                ListSection(header = "Photos") {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(photos, key = { it.id }) { att ->
                            val thumb = com.leo.imessage.ui.components.rememberThumbnail(
                                att.uri, att.kind, maxPx = 300,
                            )
                            Box(
                                Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.fieldBackground)
                                    .clickable { onOpenAttachment(att) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thumb != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = thumb,
                                        contentDescription = att.fileName,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            hazeState = hazeState,
            hairlineAtBottom = true,
        ) {
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clickable { onBack() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = palette.accent,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "Back",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Details",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.label,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(64.dp))
            }
        }
    }
}

