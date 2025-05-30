package com.example.twos

import android.graphics.Bitmap
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

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
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

        } catch (e: Exception) {
            Log.e("HandSignAnalyzer", "Error analizando imagen: ${e.message}")
        } finally {
            // Importante: cerrar imagen para liberar recursos
            imageProxy.close()
        }
    }

    // Este método es invocado automáticamente cuando hay detección de manos
    fun onHandResult(result: HandLandmarkerResult) {
        val landmarks = result.landmarks()

        // Solo si hay al menos una mano detectada
        if (landmarks.isNotEmpty()) {
            val originalLandmarks = landmarks[0]

            // PARA VISUALIZACIÓN: Pasar landmarks originales al overlay
            // (las transformaciones se aplicarán en el overlay)
            val landmarksParaVisualizacion = originalLandmarks.map { landmark ->
                Pair(landmark.x(), landmark.y())
            }

            // PARA EL MODELO: Usar landmarks originales sin transformaciones
            // (tal como fueron entrenados en Python)
            val landmarksParaModelo = originalLandmarks.map { landmark ->
                Pair(landmark.x(), landmark.y())
            }

            // Dibujar landmarks transformados en el overlay
            overlay.updateLandmarks(listOf(landmarksParaVisualizacion))

            // Clasificar usando landmarks originales (sin transformar)
            val letra = detectorTFLite.detectarLetra(landmarksParaModelo)

            // Si se detectó una letra, actualizar la UI
            letra?.let {
                Log.d("HandSignAnalyzer", "Letra detectada: $it")
                updateLetraDetectada(it)
            }
        } else {
            // Limpiar overlay si no hay manos
            overlay.updateLandmarks(emptyList())
        }
    }
}



