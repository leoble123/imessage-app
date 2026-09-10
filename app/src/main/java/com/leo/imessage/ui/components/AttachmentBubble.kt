package com.leo.imessage.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Attachment
import com.leo.imessage.data.MediaKind
import com.leo.imessage.media.MediaTools
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/**
 * Photo and video attachments.
 *
 * Renders the real file when there's one behind the attachment, and falls
 * back to a deterministic gradient for the seeded samples that have no bytes
 * - stable per attachment, so a sample thread still reads like a photo
 * thread rather than a row of identical grey boxes.
 */
@Composable
fun PhotoAttachment(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val thumbnail = rememberThumbnail(attachment.uri, attachment.kind)
    val seed = attachment.id.hashCode()
    val tall = (seed / 7) % 2 == 0

    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.97f)

    Box(
        modifier = modifier
            .width(212.dp)
            .height(if (thumbnail != null) 232.dp else if (tall) 268.dp else 158.dp)
            .scaleFrom(scale)
            .clip(RoundedCornerShape(18.dp))
            .then(if (thumbnail == null) Modifier.background(placeholderBrush(seed)) else Modifier)
            .pointerInput(attachment.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onOpen() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (attachment.kind == MediaKind.VIDEO) {
            // Play badge, plus a duration chip in the corner like Photos.
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                PlayGlyph(Color.White, Modifier.size(22.dp))
            }
            attachment.durationMs?.let { ms ->
                Text(
                    text = MediaTools.formatDuration(ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun placeholderBrush(seed: Int): Brush {
    val palettes = listOf(
        listOf(Color(0xFF3A6073), Color(0xFF16222A)),
        listOf(Color(0xFFEE9CA7), Color(0xFFFFDDE1)),
        listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        listOf(Color(0xFF654EA3), Color(0xFFEAAFC8)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    )
    val colors = palettes[((seed % palettes.size) + palettes.size) % palettes.size]
    return Brush.linearGradient(colors, start = Offset.Zero, end = Offset(600f, 900f))
}

/**
 * A voice message.
 *
 * Plays in place rather than handing off to another app - a voice note that
 * bounces you out to a media player isn't a voice note, it's a file. The
 * waveform is generated from the attachment id so it's stable, and fills in
 * as playback advances, which is the part that tells you where you are
 * without needing a scrubber.
 */
@Composable
fun AudioAttachment(
    attachment: Attachment,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    val tint = if (outgoing) Color.White else palette.label
    val trackTint = tint.copy(alpha = 0.35f)

    DisposableEffect(attachment.id) {
        onDispose {
            player.value?.runCatching { release() }
            player.value = null
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            val p = player.value
            if (p != null && p.duration > 0) {
                progress = (p.currentPosition.toFloat() / p.duration).coerceIn(0f, 1f)
            }
            delay(50)
        }
    }

    Column(modifier.widthIn(max = 260.dp).padding(vertical = 2.dp)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val uri = attachment.uri ?: return@clickable
                    val current = player.value
                    if (playing) {
                        current?.runCatching { pause() }
                        playing = false
                    } else {
                        val prepared = current ?: runCatching {
                            MediaPlayer().apply {
                                setDataSource(context, Uri.parse(uri))
                                prepare()
                                setOnCompletionListener {
                                    playing = false
                                    progress = 0f
                                }
                            }
                        }.getOrNull()
                        player.value = prepared
                        prepared?.runCatching { start() }
                        playing = prepared != null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (playing) PauseGlyph(tint, Modifier.size(13.dp))
            else PlayGlyph(tint, Modifier.size(14.dp))
        }

        Spacer(Modifier.width(10.dp))

        Waveform(
            seed = attachment.id.hashCode(),
            progress = { progress },
            playedColor = tint,
            trackColor = trackTint,
            modifier = Modifier
                .weight(1f)
                .height(26.dp),
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = attachment.durationMs?.let { MediaTools.formatDuration(it) } ?: "0:00",
            style = MaterialTheme.typography.labelSmall,
            color = tint.copy(alpha = 0.8f),
        )
    }

    // iOS puts the transcript right under the waveform, and it turns a
    // voice note into something you can read in a room where you can't
    // play it. Absent when the device's recogniser couldn't produce one.
    attachment.transcript?.let { transcript ->
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyMedium,
            color = tint.copy(alpha = 0.92f),
            modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
        )
    }
    }
}

@Composable
private fun Waveform(
    seed: Int,
    progress: () -> Float,
    playedColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    // Deterministic pseudo-waveform: real amplitude data would mean decoding
    // the whole file up front, and for a 30-second note that's a visible
    // stall for something purely decorative.
    val bars = remember(seed) {
        List(34) { i ->
            val v = abs(sin((seed % 97) * 0.37f + i * 0.9f)) * 0.75f +
                abs(sin(i * 0.31f)) * 0.25f
            (0.18f + v * 0.82f).coerceIn(0.15f, 1f)
        }
    }

    Canvas(modifier) {
        val played = progress()
        val gap = size.width / (bars.size * 1.7f)
        val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, amp ->
            val x = i * (barWidth + gap) + barWidth / 2f
            val h = size.height * amp
            val isPlayed = (i + 0.5f) / bars.size <= played
            drawLine(
                color = if (isPlayed) playedColor else trackColor,
                start = Offset(x, size.height / 2f - h / 2f),
                end = Offset(x, size.height / 2f + h / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun FileAttachment(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.97f)

    Row(
        modifier
            .widthIn(max = 280.dp)
            .scaleFrom(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.incomingBubble)
            .pointerInput(attachment.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        val uri = attachment.uri ?: return@detectTapGestures
                        MediaTools.openExternally(context, Uri.parse(uri), attachment.mimeType)
                    },
                    onLongPress = { onLongPress() },
                )
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = attachment.fileName.substringAfterLast('.', "?").take(4).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                attachment.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                attachment.sizeBytes?.let { MediaTools.formatSize(it) } ?: attachment.mimeType,
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryLabel,
            )
        }
    }
}

@Composable
fun PlayGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            // Nudged right, because a centred triangle reads as left-heavy.
            moveTo(size.width * 0.12f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width * 0.12f, size.height)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun PauseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barWidth = size.width * 0.32f
        drawLine(
            color = color,
            start = Offset(barWidth / 2f, 0f),
            end = Offset(barWidth / 2f, size.height),
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width - barWidth / 2f, 0f),
            end = Offset(size.width - barWidth / 2f, size.height),
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
        )
    }
}
