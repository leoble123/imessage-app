package com.leo.imessage.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.leo.imessage.data.Attachment
import com.leo.imessage.data.MediaKind
import com.leo.imessage.media.MediaTools
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen media, presented the way Photos does it.
 *
 * Pinch to zoom and pan around, double-tap to snap between fit and 2.5x, and
 * drag down to throw it away - the background fading and the image shrinking
 * as you pull, so the dismissal is something you're doing rather than
 * something that happens after you let go. Save puts a copy in the gallery;
 * Share hands it to the system sheet.
 */
@Composable
fun MediaViewer(
    attachment: Attachment,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = remember(attachment.uri) { attachment.uri?.let { runCatching { Uri.parse(it) }.getOrNull() } }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(attachment.id) { appear.animateTo(1f, Motion.standard()) }

    // Zoom/pan state, and the drag-to-dismiss offset.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val dismissDrag = remember { Animatable(0f) }
    var savedNotice by remember { mutableStateOf<String?>(null) }

    BackHandler { onDismiss() }

    LaunchedEffect(savedNotice) {
        if (savedNotice != null) {
            kotlinx.coroutines.delay(1800)
            savedNotice = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // The backdrop thins out as the media is pulled away, so the
                // conversation behind comes back gradually rather than
                // reappearing all at once on release.
                val pull = (abs(dismissDrag.value) / 700f).coerceIn(0f, 1f)
                alpha = appear.value * (1f - pull * 0.55f)
            }
            .background(Color.Black.copy(alpha = 0.97f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pull = (abs(dismissDrag.value) / 700f).coerceIn(0f, 1f)
                    translationY = dismissDrag.value
                    val s = (1f - pull * 0.25f) * (0.94f + 0.06f * appear.value)
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) {
            when (attachment.kind) {
                MediaKind.VIDEO -> if (uri != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(uri)
                                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                setOnPreparedListener { it.isLooping = false; start() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ViewerPlaceholder(attachment)
                }

                MediaKind.IMAGE -> {
                    val bitmap = rememberThumbnail(attachment.uri, attachment.kind, maxPx = 2400)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = attachment.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                                .pointerInput(attachment.id) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.05f) {
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            } else {
                                                scale = 2.5f
                                            }
                                        },
                                    )
                                }
                                .pointerInput(attachment.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                        if (scale > 1.02f) {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                },
                        )
                    } else {
                        ViewerPlaceholder(attachment)
                    }
                }

                else -> ViewerPlaceholder(attachment)
            }
        }

        // Drag-to-dismiss lives on its own strip rather than over the image,
        // so it can't fight the pan gesture once you've zoomed in.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(attachment.id, scale) {
                    if (scale > 1.02f) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(dismissDrag.value) > 180f) onDismiss()
                                else dismissDrag.animateTo(0f, Motion.standard())
                            }
                        },
                        onDragCancel = {
                            scope.launch { dismissDrag.animateTo(0f, Motion.standard()) }
                        },
                    ) { _, drag ->
                        scope.launch { dismissDrag.snapTo(dismissDrag.value + drag) }
                    }
                }
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerAction("Done") { onDismiss() }
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ViewerAction("Share") {
                uri?.let { MediaTools.share(context, it, attachment.mimeType) }
            }
            ViewerAction("Save") {
                val target = uri ?: return@ViewerAction
                scope.launch {
                    val ok = MediaTools.saveToGallery(
                        context = context,
                        uri = target,
                        mimeType = attachment.mimeType,
                        name = attachment.fileName,
                    )
                    savedNotice = if (ok) "Saved" else "Couldn't save"
                }
            }
        }

        savedNotice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        Color.Black.copy(alpha = 0.65f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ViewerAction(label: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.9f)
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = Color(0xFF3B9BFF),
        modifier = Modifier
            .scaleFrom(scale)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .padding(6.dp),
    )
}

@Composable
private fun ViewerPlaceholder(attachment: Attachment) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Text(
            text = "This sample has no file behind it",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
