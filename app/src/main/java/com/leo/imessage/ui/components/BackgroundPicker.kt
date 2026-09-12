package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.AllChatBackgrounds
import com.leo.imessage.ui.theme.LocalPalette

/**
 * The wallpaper strip, shared by a conversation's own background and the
 * home screen's.
 *
 * One picker rather than two: they list the same backgrounds, and the moment
 * there are two copies one of them gets an entry the other does not.
 */
@Composable
fun BackgroundPicker(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AllChatBackgrounds.forEach { bg ->
            val selected = bg.id == selectedId
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 54.dp, height = 88.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (bg.brush != null) Modifier.background(bg.brush)
                            else Modifier.background(palette.background)
                        )
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) palette.accent else palette.separator,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(bg.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    // The swatch runs the real thing, so a dynamic background
                    // is obviously moving before you commit to it.
                    if (bg.isDynamic) {
                        ChatWallpaper(
                            background = bg,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (bg.isDynamic) "${bg.name} ✦" else bg.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) palette.accent else palette.secondaryLabel,
                )
            }
        }
    }
}
