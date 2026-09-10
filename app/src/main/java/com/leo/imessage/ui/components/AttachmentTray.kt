package com.leo.imessage.ui.components

import android.Manifest
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Attachment
import com.leo.imessage.data.MessageEffect
import com.leo.imessage.media.MediaTools
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/**
 * What the "+" opens.
 *
 * iOS presents this as a sheet that rises under your thumb with the options
 * as a row of round tiles, so this does the same: one spring up from the
 * bottom, tiles staggered in behind it, and a scrim you can tap anywhere to
 * put it away.
 *
 * Every tile is real. Photos and Videos go through the system photo picker
 * (which needs no permission at all and never sees your whole library),
 * Camera writes into our own cache through a FileProvider, Files uses the
 * document picker, and Audio records in place with a live timer.
 */
@Composable
fun AttachmentTray(
    onAttach: (List<Attachment>) -> Unit,
    onPickEffect: (MessageEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.gentle()) }

    var recording by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    fun deliver(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val attachments = uris.map { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            MediaTools.describe(context, uri, MediaTools.mimeTypeOf(context, uri))
        }
        onAttach(attachments)
        onDismiss()
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> deliver(uris) }

    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> deliver(listOfNotNull(uri)) }

    // The camera writes into a file we own, so we have to hold on to where
    // it went while the camera app is in the foreground.
    var pendingCapture by remember { mutableStateOf<Pair<File, Uri>?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = pendingCapture
        pendingCapture = null
        if (success && capture != null) deliver(listOf(capture.second))
    }

    val requestAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> recording = granted }

    if (showEffects) {
        EffectPicker(
            onPick = { effect ->
                showEffects = false
                onPickEffect(effect)
                onDismiss()
            },
            onDismiss = { showEffects = false },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = 320.dp.toPx() * (1f - appear.value) }
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(palette.surface)
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(38.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(palette.tertiaryLabel)
            )

            if (recording) {
                AudioRecorderPanel(
                    onCancel = { recording = false },
                    onFinished = { attachment ->
                        recording = false
                        onAttach(listOf(attachment))
                        onDismiss()
                    },
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TrayTile("Photos", index = 0, appear = { appear.value }) {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                    TrayTile("Camera", index = 1, appear = { appear.value }) {
                        val capture = MediaTools.newCaptureTarget(context, "jpg")
                        pendingCapture = capture
                        takePicture.launch(capture.second)
                    }
                    TrayTile("Files", index = 2, appear = { appear.value }) {
                        pickDocument.launch(arrayOf("*/*"))
                    }
                    TrayTile("Audio", index = 3, appear = { appear.value }) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    TrayTile("Effects", index = 4, appear = { appear.value }) {
                        showEffects = true
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayTile(
    label: String,
    index: Int,
    appear: () -> Float,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.88f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                // Staggered: each tile trails the one before it, so the row
                // unfurls rather than arriving as a single block.
                val delayed = ((appear() - index * 0.06f) / (1f - index * 0.06f))
                    .coerceIn(0f, 1f)
                alpha = delayed
                translationY = 22.dp.toPx() * (1f - delayed)
            }
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            },
    ) {
        Box(
            Modifier
                .size(58.dp)
                .scaleFrom(scale)
                .clip(CircleShape)
                .background(palette.fieldBackground),
            contentAlignment = Alignment.Center,
        ) {
            TrayGlyph(label, palette.accent)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

/** Hand-drawn glyphs, so the tray doesn't pull in an icon font. */
@Composable
private fun TrayGlyph(label: String, color: Color) {
    Canvas(Modifier.size(25.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        when (label) {
            "Photos" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.06f, h * 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.32f, h * 0.36f))
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.12f, h * 0.78f)
                    lineTo(w * 0.42f, h * 0.48f)
                    lineTo(w * 0.88f, h * 0.82f)
                }
                drawPath(
                    path, color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            "Camera" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.04f, h * 0.24f),
                    size = androidx.compose.ui.geometry.Size(w * 0.92f, h * 0.58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawCircle(
                    color, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.53f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
            }
            "Files" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.2f, h * 0.1f)
                    lineTo(w * 0.6f, h * 0.1f)
                    lineTo(w * 0.82f, h * 0.34f)
                    lineTo(w * 0.82f, h * 0.9f)
                    lineTo(w * 0.2f, h * 0.9f)
                    close()
                }
                drawPath(
                    path, color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            "Audio" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.34f, h * 0.08f),
                    size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f),
                )
                drawLine(
                    color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.92f),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.34f),
                    size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.44f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            else -> {
                // Effects: a small burst.
                repeat(8) { i ->
                    val angle = Math.toRadians(i * 45.0)
                    val inner = w * 0.2f
                    val outer = w * 0.44f
                    drawLine(
                        color,
                        Offset(
                            w / 2f + (inner * Math.cos(angle)).toFloat(),
                            h / 2f + (inner * Math.sin(angle)).toFloat(),
                        ),
                        Offset(
                            w / 2f + (outer * Math.cos(angle)).toFloat(),
                            h / 2f + (outer * Math.sin(angle)).toFloat(),
                        ),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * Records a voice message in place.
 *
 * Recording stops and the file is attached in one tap - a separate "attach"
 * step after stopping is the sort of thing that makes a voice note feel like
 * paperwork.
 */
@Composable
private fun AudioRecorderPanel(
    onCancel: () -> Unit,
    onFinished: (Attachment) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var elapsed by remember { mutableStateOf(0L) }

    val target = remember {
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        File(dir, "${System.currentTimeMillis()}.m4a")
    }

    val recorder = remember {
        runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            r.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder?.release() } }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            elapsed += 100
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Cancel",
            style = MaterialTheme.typography.titleSmall,
            color = palette.secondaryLabel,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    runCatching { recorder?.stop() }
                    target.delete()
                    onCancel()
                }
                .padding(6.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(palette.destructive)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                MediaTools.formatDuration(elapsed),
                style = MaterialTheme.typography.titleMedium,
                color = palette.label,
            )
        }

        Text(
            "Send",
            style = MaterialTheme.typography.titleSmall,
            color = palette.accent,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val stopped = runCatching { recorder?.stop() }.isSuccess
                    if (stopped && target.exists() && target.length() > 0) {
                        onFinished(
                            Attachment(
                                id = UUID.randomUUID().toString(),
                                fileName = "Audio Message.m4a",
                                mimeType = "audio/mp4",
                                uri = Uri.fromFile(target).toString(),
                                durationMs = elapsed,
                                sizeBytes = target.length(),
                            )
                        )
                    } else {
                        onCancel()
                    }
                }
                .padding(6.dp),
        )
    }
}
