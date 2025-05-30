package com.example.twos

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandSignAnalyzer(
    private val handLandmarker: HandLandmarker,            // Modelo MediaPipe HandLandmarker
    private val overlay: HandLandmarkOverlay,              // Vista para dibujar puntos de la mano
    private val detectorTFLite: DetectorLetrasTFLite,      // Modelo TFLite personalizado
    private val updateLetraDetectada: (String) -> Unit,    // Callback para mostrar letra en pantalla
    private val yuvToRgbConverter: YuvToRgbConverter       // Conversor de YUV a Bitmap
) : ImageAnalysis.Analyzer {

    // Handler para ejecutar callbacks en el hilo principal
    private val mainHandler = Handler(Looper.getMainLooper())
    private var frameCount = 0

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        frameCount++
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        try {
            // Convertir la imagen YUV a Bitmap
            val bitmap: Bitmap = yuvToRgbConverter.convert(imageProxy)

            // Crear objeto MPImage para usar con MediaPipe
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()

            // Detectar manos de forma asíncrona usando MediaPipe
            handLandmarker.detectAsync(mpImage, System.currentTimeMillis())

            // Log cada 30 frames para verificar que se está ejecutando
            if (frameCount % 30 == 0) {
                Log.d("HandSignAnalyzer", "Procesando frame #$frameCount")
            }

        } catch (e: Exception) {
            Log.e("HandSignAnalyzer", "Error analizando imagen: ${e.message}")
        } finally {
            // Importante: cerrar imagen para liberar recursos
            imageProxy.close()
        }
    }

    // Este método es invocado automáticamente cuando hay detección de manos
    fun onHandResult(result: HandLandmarkerResult) {
        Log.d("HandSignAnalyzer", "onHandResult llamado")

        val landmarks = result.landmarks()
        Log.d("HandSignAnalyzer", "Número de manos detectadas: ${landmarks.size}")

        // Solo si hay al menos una mano detectada
        if (landmarks.isNotEmpty()) {
            val originalLandmarks = landmarks[0]
            Log.d("HandSignAnalyzer", "Landmarks de la mano: ${originalLandmarks.size} puntos")

            // PARA VISUALIZACIÓN: Pasar landmarks originales al overlay
            val landmarksParaVisualizacion = originalLandmarks.map { landmark ->
                Pair(landmark.x(), landmark.y())
            }

            // PARA EL MODELO: Usar landmarks originales sin transformaciones
            val landmarksParaModelo = originalLandmarks.map { landmark ->
                Pair(landmark.x(), landmark.y())
            }

            // Dibujar landmarks en el overlay (ejecutar en hilo principal)
            mainHandler.post {
                overlay.updateLandmarks(listOf(landmarksParaVisualizacion))
            }

            // Clasificar usando landmarks originales (sin transformar)
            Log.d("HandSignAnalyzer", "Iniciando clasificación con ${landmarksParaModelo.size} landmarks")
            val letra = detectorTFLite.detectarLetra(landmarksParaModelo)
            Log.d("HandSignAnalyzer", "Resultado de clasificación: $letra")

            // Si se detectó una letra, actualizar la UI en el hilo principal
            if (letra != null) {
                Log.d("HandSignAnalyzer", "✅ Letra detectada: $letra - Actualizando UI")
                mainHandler.post {
                    Log.d("HandSignAnalyzer", "🔄 Ejecutando callback en hilo principal")
                    updateLetraDetectada(letra)
                }
            } else {
                Log.d("HandSignAnalyzer", "❌ No se detectó letra (confianza baja o error)")
            }
        } else {
            // Limpiar overlay si no hay manos (ejecutar en hilo principal)
            mainHandler.post {
                overlay.updateLandmarks(emptyList())
            }
        }
    }
}



