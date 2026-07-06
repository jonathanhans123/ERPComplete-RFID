package com.erpcomplete.rfid.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.erpcomplete.rfid.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.erpcomplete.rfid.util.PhoneBluetooth
import com.erpcomplete.rfid.rfid.PairingBarcodeParser
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

enum class PairingScanMode {
    /** Scan reader QR / pairing barcode and return raw payload. */
    READER_CONNECT,
    /** Scan any barcode that contains a Bluetooth MAC address. */
    MAC_ADDRESS,
}

@Composable
fun PairingCameraScanner(
    onBarcodeScanned: (String) -> Unit,
    scanGeneration: Int = 0,
    isConnecting: Boolean = false,
    modifier: Modifier = Modifier,
    scanMode: PairingScanMode = PairingScanMode.READER_CONNECT,
    onMacCaptured: ((String) -> Unit)? = null,
    onScanRejected: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var scanned by remember { mutableStateOf(false) }
    var bindGeneration by remember { mutableIntStateOf(0) }
    var lastRejectedAt by remember { mutableStateOf(0L) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(scanGeneration) {
        if (!isConnecting) {
            scanned = false
            bindGeneration++
            lastRejectedAt = 0L
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasCameraPermission) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(R.string.pairing_camera_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.TextButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.pairing_camera_grant))
                }
            }
            return
        }

        if (scanned || isConnecting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.pairing_connecting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            return
        }

        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            update = { previewView ->
                if (scanned || !hasCameraPermission) return@AndroidView
                val generationAtBind = bindGeneration
                previewView.post {
                    if (scanned || generationAtBind != bindGeneration) return@post
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        if (scanned || generationAtBind != bindGeneration) return@addListener
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val scanner = BarcodeScanning.getClient()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (scanned) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    var sawCandidate = false
                                    for (barcode in barcodes) {
                                        val value = barcode.rawValue?.trim().orEmpty()
                                        if (value.isBlank()) continue
                                        when (scanMode) {
                                            PairingScanMode.MAC_ADDRESS -> {
                                                val mac = PhoneBluetooth.parseMacInput(value)
                                                if (mac == null) continue
                                                scanned = true
                                                cameraProvider.unbindAll()
                                                onMacCaptured?.invoke(mac)
                                                break
                                            }
                                            PairingScanMode.READER_CONNECT -> {
                                                sawCandidate = true
                                                if (PairingBarcodeParser.parse(value) == null) continue
                                                scanned = true
                                                cameraProvider.unbindAll()
                                                onBarcodeScanned(value)
                                                break
                                            }
                                        }
                                    }
                                    if (scanMode == PairingScanMode.READER_CONNECT && sawCandidate && !scanned) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastRejectedAt > 2000) {
                                            lastRejectedAt = now
                                            val sample = barcodes.firstNotNullOfOrNull {
                                                it.rawValue?.trim()?.takeIf { v -> v.isNotBlank() }
                                            } ?: return@addOnSuccessListener
                                            onScanRejected?.invoke(sample)
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                        runCatching {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            },
        )
    }
}
