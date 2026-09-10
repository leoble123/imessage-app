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
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * What the "+" opens.
 *
 * It takes the keyboard's place rather than floating over the composer -
 * that's how iOS does it, and it's why the tray never covers the thing you
 * just tapped. The panel matches the height the keyboard last occupied, so
 * swapping between the two doesn't make the conversation jump; before the
 * keyboard has ever been up it falls back to a sensible default.
 *
 * Every tile is real. Photos goes through the system picker (no permission,
 * and it never sees your whole library), Gallery hands off to whatever
 * gallery app the phone ships - Samsung's, here - Camera writes into our
 * cache through a FileProvider, Files uses the document picker, and Audio
 * records in place with a live timer and transcription.
 */
@Composable
fun AttachmentTray(
    onAttach: (List<Attachment>) -> Unit,
    onPickEffect: (MessageEffect) -> Unit,
    onDismiss: () -> Unit,
    /** Height the keyboard last occupied, so the panel takes its place. */
    panelHeight: androidx.compose.ui.unit.Dp = 300.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val haptics = com.leo.imessage.ui.components.rememberHaptics()

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

    // ACTION_PICK on the media store is what opens the phone's own gallery
    // app rather than the system picker sheet - on a Samsung that's Gallery.
    val pickFromGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris = buildList {
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
            }
            data?.data?.let { add(it) }
        }
        deliver(uris.distinct())
    }

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

    Column(
        modifier
            .fillMaxWidth()
            .height(panelHeight)
            .graphicsLayer {
                translationY = panelHeight.toPx() * (1f - appear.value)
                alpha = 0.4f + 0.6f * appear.value
            }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(palette.surface)
            // A flick down anywhere on the panel puts it away, the same
            // gesture that dismisses the keyboard it replaced.
            .verticalFlick(onUp = {}, onDown = onDismiss)
            .padding(top = 10.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        run {
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
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { TrayTile("Photos", 0, { appear.value }) {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    } }
                    item { TrayTile("Gallery", 1, { appear.value }) {
                        pickFromGallery.launch(openGalleryIntent())
                    } }
                    item { TrayTile("Camera", 2, { appear.value }) {
                        val capture = MediaTools.newCaptureTarget(context, "jpg")
                        pendingCapture = capture
                        takePicture.launch(capture.second)
                    } }
                    item { TrayTile("Files", 3, { appear.value }) {
                        pickDocument.launch(arrayOf("*/*"))
                    } }
                    item { TrayTile("Audio", 4, { appear.value }) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } }
                    item { TrayTile("Effects", 5, { appear.value }) {
                        showEffects = true
                    } }
                }
            }
        }
    }
}

/**
 * Opens the phone's gallery app rather than the system picker sheet.
 *
 * ACTION_PICK against the media store is what the OEM gallery registers
 * for, so on a Samsung this lands directly in Gallery with its albums and
 * multi-select, instead of the generic Android photo sheet.
 */
private fun openGalleryIntent(): android.content.Intent =
    android.content.Intent(
        android.content.Intent.ACTION_PICK,
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    ).apply {
        type = "image/*, video/*"
        putExtra(
            android.content.Intent.EXTRA_MIME_TYPES,
            arrayOf("image/*", "video/*"),
        )
        putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
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
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(LocalPalette.current.fieldBackground)
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
            Modifier.scaleFrom(scale),
            contentAlignment = Alignment.Center,
        ) {
            TrayGlyph(label, palette.accent)
        }
        Spacer(Modifier.height(8.dp))
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
            "Gallery" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.04f, h * 0.16f),
                    size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.34f, h * 0.32f),
                    size = androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var elapsed by remember { mutableStateOf(0L) }
    var transcribing by remember { mutableStateOf(false) }

    val target = remember {
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        File(dir, "${System.currentTimeMillis()}.wav")
    }
    val recorder = remember { com.leo.imessage.media.VoiceRecorder(context) }
    var live by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val started = runCatching { recorder.start(target) }.getOrDefault(false)
        if (!started) onCancel()
        onDispose { runCatching { recorder.stop(target) } }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            elapsed += 60
            live = recorder.level
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Live level, so you can see the mic is actually hearing you.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val bars = 40
            val gap = size.width / (bars * 1.8f)
            val barWidth = (size.width - gap * (bars - 1)) / bars
            repeat(bars) { i ->
                val distance = kotlin.math.abs(i - bars / 2f) / (bars / 2f)
                val amp = (live * (1f - distance * 0.75f)).coerceIn(0.02f, 1f)
                val h = size.height * amp
                drawLine(
                    color = palette.accent,
                    start = Offset(i * (barWidth + gap) + barWidth / 2f, size.height / 2f - h / 2f),
                    end = Offset(i * (barWidth + gap) + barWidth / 2f, size.height / 2f + h / 2f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = if (transcribing) "Transcribing…" else MediaTools.formatDuration(elapsed),
            style = MaterialTheme.typography.headlineSmall,
            color = palette.label,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cancel",
                style = MaterialTheme.typography.titleSmall,
                color = palette.secondaryLabel,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !transcribing,
                    ) {
                        recorder.cancel(target)
                        onCancel()
                    }
                    .padding(10.dp),
            )
            Text(
                "Send",
                style = MaterialTheme.typography.titleSmall,
                color = palette.accent,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !transcribing,
                    ) {
                        val duration = elapsed
                        val ok = recorder.stop(target)
                        if (!ok) {
                            onCancel()
                            return@clickable
                        }
                        transcribing = true
                        scope.launch {
                            // Transcribed before sending rather than patched
                            // in afterwards: a voice note whose text appears
                            // a second later reads as a glitch.
                            val text = com.leo.imessage.media.transcribeWav(context, target)
                            transcribing = false
                            onFinished(
                                Attachment(
                                    id = UUID.randomUUID().toString(),
                                    fileName = "Audio Message.wav",
                                    mimeType = "audio/wav",
                                    uri = Uri.fromFile(target).toString(),
                                    durationMs = duration,
                                    sizeBytes = target.length(),
                                    transcript = text,
                                )
                            )
                        }
                    }
                    .padding(10.dp),
            )
        }
    }
}
