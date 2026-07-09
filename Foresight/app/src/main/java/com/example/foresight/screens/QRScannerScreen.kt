package com.example.foresight.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private val DarkBg = Color(0xFF070913)
private val SurfaceVariant = Color(0xFF171C2A)
private val AccentPurple = Color(0xFF7C4DFF)
private val AccentMint = Color(0xFF20E3B2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onBackClick: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { decodeQrFromGallery(context, it, onQrScanned) }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var isFlashOn by remember { mutableStateOf(false) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var isScanningActive by remember { mutableStateOf(true) }
    var showSuccessPulse by remember { mutableStateOf(false) }

    val successPulseAlpha by animateFloatAsState(
        targetValue = if (showSuccessPulse) 1f else 0f,
        animationSpec = tween(400),
        label = "success_pulse",
        finishedListener = { if (it == 1f) showSuccessPulse = false }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan any UPI QR", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isFlashOn = !isFlashOn
                        camera?.cameraControl?.enableTorch(isFlashOn)
                    }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                            contentDescription = "Flash",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProviderInstance = cameraProviderFuture.get()
                            cameraProvider = cameraProviderInstance
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (isScanningActive) {
                                            processImageProxy(barcodeScanner, imageProxy) { result ->
                                                if (isScanningActive) {
                                                    isScanningActive = false
                                                    showSuccessPulse = true
                                                    vibrate(ctx)
                                                    onQrScanned(result)
                                                }
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProviderInstance.unbindAll()
                                camera = cameraProviderInstance.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("QRScanner", "Use case binding failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PermissionDeniedUI { launcher.launch(Manifest.permission.CAMERA) }
            }

            // Overlay
            ScannerOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                successAlpha = successPulseAlpha
            )

            // Bottom Actions
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 60.dp, start = 32.dp, end = 32.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ScannerAction("Upload QR", Icons.Default.Image) {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ScannerAction("My QR", Icons.Default.QrCode) {
                    // Dummy
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            barcodeScanner.close()
            cameraExecutor.shutdown()
        }
    }
}

@Composable
private fun ScannerOverlay(
    modifier: Modifier = Modifier,
    successAlpha: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )

    Box(modifier = modifier) {
        // Scrim
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameSize = 260.dp.toPx()
            val left = (size.width - frameSize) / 2
            val top = (size.height - frameSize) / 2
            val right = left + frameSize
            val bottom = top + frameSize

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
                
                moveTo(left, top)
                lineTo(left, bottom)
                lineTo(right, bottom)
                lineTo(right, top)
                close()
            }
            drawPath(path, Color.Black.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Fill)
            
            // Frame corners
            val cornerLength = 30.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val frameColor = if (successAlpha > 0) AccentMint.copy(alpha = successAlpha) else AccentPurple
            
            // Top Left
            drawLine(frameColor, offset(left, top + cornerLength), offset(left, top), strokeWidth)
            drawLine(frameColor, offset(left, top), offset(left + cornerLength, top), strokeWidth)
            
            // Top Right
            drawLine(frameColor, offset(right - cornerLength, top), offset(right, top), strokeWidth)
            drawLine(frameColor, offset(right, top), offset(right, top + cornerLength), strokeWidth)
            
            // Bottom Left
            drawLine(frameColor, offset(left, bottom - cornerLength), offset(left, bottom), strokeWidth)
            drawLine(frameColor, offset(left, bottom), offset(left + cornerLength, bottom), strokeWidth)
            
            // Bottom Right
            drawLine(frameColor, offset(right - cornerLength, bottom), offset(right, bottom), strokeWidth)
            drawLine(frameColor, offset(right, bottom), offset(right, bottom - cornerLength), strokeWidth)

            if (successAlpha == 0f) {
                // Scan line
                val lineY = top + (bottom - top) * scanLineY
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(AccentPurple.copy(alpha = 0f), AccentPurple, AccentPurple.copy(alpha = 0f))
                    ),
                    start = offset(left + 10.dp.toPx(), lineY),
                    end = offset(right - 10.dp.toPx(), lineY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        Text(
            "Align QR code within the frame to scan",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 320.dp)
        )
    }
}

private fun offset(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)

@Composable
private fun PermissionDeniedUI(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Camera, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Camera Permission Needed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Camera access is required to scan UPI QR codes and make payments quickly.",
            color = Color.White.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("Grant Permission", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScannerAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceVariant.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onSuccess: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { 
                        if (it.startsWith("upi://pay")) {
                            onSuccess(it)
                        }
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

private fun decodeQrFromGallery(context: Context, uri: Uri, onSuccess: (String) -> Unit) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val upiBarcode = barcodes.firstOrNull { it.rawValue?.startsWith("upi://pay") == true }
                if (upiBarcode != null) {
                    vibrate(context)
                    upiBarcode.rawValue?.let(onSuccess)
                } else {
                    Toast.makeText(context, "No UPI QR found in image", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to decode QR", Toast.LENGTH_SHORT).show()
            }
    } catch (e: Exception) {
        Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show()
    }
}

private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(100)
    }
}
