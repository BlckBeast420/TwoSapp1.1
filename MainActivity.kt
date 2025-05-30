package com.example.twos

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.twos.ui.theme.TwoSTheme
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicita permisos de cámara al usuario
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Log.e("MainActivity", "Permiso de cámara denegado")
            }
        }

        // Lanza solicitud de permiso de cámara
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        // Muestra UI principal con Jetpack Compose
        setContent {
            TwoSTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraPreviewWithOverlay()
                }
            }
        }
    }
}

@Composable
fun CameraPreviewWithOverlay() {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val letraDetectada = remember { mutableStateOf<String?>(null) }
    val estadoConexion = remember { mutableStateOf("Inicializando...") }

    AndroidView(factory = { ctx ->
        val previewView = PreviewView(ctx)
        val overlay = HandLandmarkOverlay(ctx)
        val frameLayout = FrameLayout(ctx).apply {
            addView(previewView)
            addView(overlay)
        }

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val yuvToRgbConverter = YuvToRgbConverter(ctx)

                // Cargar modelo de MediaPipe hand_landmarker.task
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build()

                val detectorTFLite = DetectorLetrasTFLite(ctx)

                lateinit var analyzer: HandSignAnalyzer

                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1) // Detectar solo una mano para mejor rendimiento
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result: HandLandmarkerResult, _: MPImage ->
                        // Enviar resultado a analyzer para dibujar y clasificar
                        analyzer.onHandResult(result)
                    }
                    .build()

                val handLandmarker = HandLandmarker.createFromOptions(ctx, options)

                // Crear instancia del analizador personalizado
                analyzer = HandSignAnalyzer(
                    handLandmarker = handLandmarker,
                    overlay = overlay,
                    detectorTFLite = detectorTFLite,
                    updateLetraDetectada = { letra ->
                        Log.d("MainActivity", "📱 Callback recibido: $letra")
                        Log.d("MainActivity", "🔧 Thread actual: ${Thread.currentThread().name}")

                        letraDetectada.value = letra
                        estadoConexion.value = "Detectando: $letra"

                        Log.d("MainActivity", "✅ Estados actualizados - letra: ${letraDetectada.value}, estado: ${estadoConexion.value}")
                    },
                    yuvToRgbConverter = yuvToRgbConverter
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor, analyzer)
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    context as ComponentActivity,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )

                estadoConexion.value = "Listo - Muestra tu mano"
                Log.d("MainActivity", "Cámara iniciada correctamente")

            } catch (exc: Exception) {
                Log.e("MainActivity", "Error al iniciar cámara", exc)
                estadoConexion.value = "Error: ${exc.message}"
            }

        }, ContextCompat.getMainExecutor(ctx))

        frameLayout
    })

    // Overlay con información
    Box(modifier = Modifier.fillMaxSize()) {
        // Estado de conexión en la parte superior
        Text(
            text = estadoConexion.value,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // Letra detectada en el centro superior
        letraDetectada.value?.let { letra ->
            Text(
                text = letra,
                color = Color.Red,
                fontSize = 64.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
            )
        }

        // Instrucciones en la parte inferior
        Text(
            text = "Muestra tu mano derecha hacia la cámara",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}





