package com.leo.imessage.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.leo.imessage.ui.theme.LocalPalette
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A dedicated, full-screen QR scanner for setup.
 *
 * CameraX supplies the preview and the frames; ML Kit reads only QR codes out
 * of them. [onResult] fires at most once - a scan is a single decision, and
 * the caller (not this screen) owns what happens with it, including undoing
 * that decision by reopening the scanner on failure.
 */
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) permanentlyDenied = true
    }

    // Asked for the moment this screen appears, not before - the setup
    // screen behind it never touches the camera, so nothing here should cost
    // a permission dialog until scanning is actually chosen.
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(onResult = onResult)

            // Scan target and instructions, over the live preview.
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Transparent)
                ) {
                    ScanTargetCorners()
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
                Text(
                    "Point the camera at your setup QR code",
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (permanentlyDenied) {
                        "Camera access was denied. Enable it for this app in " +
                            "Android Settings, then try again."
                    } else {
                        "Camera access is needed to scan a QR code."
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                if (!permanentlyDenied) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
                    Text(
                        "Try again",
                        color = palette.accent,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )
                }
            }
        }

        // Cancel is always reachable, permission granted or not.
        Box(
            Modifier
                .systemBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
        }
    }
}

@Composable
private fun ScanTargetCorners() {
    val stroke = 4.dp
    val length = 28.dp
    val color = Color.White
    Box(Modifier.fillMaxSize()) {
        // Four corner brackets rather than a full border - reads as a
        // viewfinder rather than a box drawn around the preview.
        Box(Modifier.align(Alignment.TopStart).size(length, stroke).background(color))
        Box(Modifier.align(Alignment.TopStart).size(stroke, length).background(color))
        Box(Modifier.align(Alignment.TopEnd).size(length, stroke).background(color))
        Box(Modifier.align(Alignment.TopEnd).size(stroke, length).background(color))
        Box(Modifier.align(Alignment.BottomStart).size(length, stroke).background(color))
        Box(Modifier.align(Alignment.BottomStart).size(stroke, length).background(color))
        Box(Modifier.align(Alignment.BottomEnd).size(length, stroke).background(color))
        Box(Modifier.align(Alignment.BottomEnd).size(stroke, length).background(color))
    }
}

/**
 * The live CameraX preview, wired to an ML Kit analyzer that looks only for
 * QR codes.
 *
 * [onResult] is guarded by an [AtomicBoolean] rather than a `remember`ed
 * `Boolean`: the analyzer callback runs on its own executor thread, off the
 * composition, so a plain flag read-then-write there is a race that can fire
 * the callback twice from two frames decoded microseconds apart.
 */
@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult = rememberUpdatedState(onResult)
    val handled = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    // Only the newest frame is ever queued - a QR scan has no
                    // use for a backlog, and processing stale frames is what
                    // makes a scanner feel laggy under a slow decode.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || handled.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstOrNull()?.rawValue
                            if (value != null && !handled.getAndSet(true)) {
                                provider.unbindAll()
                                currentOnResult.value(value)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (_: Exception) {
                    // The screen is torn down (e.g. Cancel tapped) before the
                    // provider finished initializing. Nothing to bind to
                    // anymore, and nothing worth surfacing for it.
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
