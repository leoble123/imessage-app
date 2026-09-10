package com.leo.imessage.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.imessage.ui.theme.LocalPalette

/**
 * Shown once after a crash, so the trace can be copied and sent on.
 *
 * Deliberately plain and impossible to miss. A crash the user can describe
 * only as "it closed" costs a round of guessing; the stack trace names the
 * line.
 */
@Composable
fun CrashScreen(report: String, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Echo crashed",
                color = palette.label,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Copy this and send it over - it says exactly what went wrong.",
                color = palette.secondaryLabel,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.fieldBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    report,
                    color = palette.label,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Action(
                    label = if (copied) "Copied" else "Copy",
                    background = palette.accent,
                    modifier = Modifier.weight(1f),
                ) {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Echo crash", report))
                    copied = true
                }
                Action(
                    label = "Dismiss",
                    background = palette.fieldBackground,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun Action(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (background == palette.accent) androidx.compose.ui.graphics.Color.White
            else palette.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
