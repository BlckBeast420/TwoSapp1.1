package com.example.twos

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class DetectorLetrasTFLite(context: Context) {

    private var interpreter: Interpreter? = null

    // Mapeo de índice a letra EXACTAMENTE como está en clases.npy
    private val indexToLetter = listOf(
        "A", "B", "C", "D", "E",
        "F", "G", "H", "I", "L",
        "M", "N", "O", "P", "R",
        "S", "T", "U", "V", "W", "Y"
    )

    init {
        try {
            // Cargar el modelo desde la carpeta assets
            val modelFile = loadModelFile(context, "modelo_gestos.tflite")
            interpreter = Interpreter(modelFile)
            Log.d("DetectorLetrasTFLite", "✅ Modelo cargado correctamente")
            Log.d("DetectorLetrasTFLite", "📊 Clases disponibles: ${indexToLetter.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e("DetectorLetrasTFLite", "❌ Error cargando modelo: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Carga el modelo TFLite desde la carpeta assets y lo mapea a memoria
     */
    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Ejecuta la detección de la letra a partir de 21 landmarks normalizados (x,y)
     */
    fun detectarLetra(landmarks: List<Pair<Float, Float>>): String? {
        Log.d("DetectorLetrasTFLite", "🔍 Iniciando detección...")

        if (interpreter == null) {
            Log.e("DetectorLetrasTFLite", "❌ Interpreter es null")
            return null
        }

        if (landmarks.size != 21) {
            Log.e("DetectorLetrasTFLite", "❌ Landmarks incorrectos: ${landmarks.size} (esperados: 21)")
            return null
        }

        try {
            Log.d("DetectorLetrasTFLite", "📥 Preparando input con ${landmarks.size} landmarks")

            // Imprimir algunos landmarks para debug
            Log.d("DetectorLetrasTFLite", "🤏 Primeros 3 landmarks: ${landmarks.take(3)}")

            // 21 puntos × 2 coordenadas = 42 floats
            val inputBuffer = ByteBuffer.allocateDirect(42 * 4).order(ByteOrder.nativeOrder())
            landmarks.forEach { (x, y) ->
                inputBuffer.putFloat(x)
                inputBuffer.putFloat(y)
            }

            // El modelo tiene una salida de 21 letras → tamaño del array = 21
            val outputBuffer = ByteBuffer.allocateDirect(indexToLetter.size * 4).order(ByteOrder.nativeOrder())

            Log.d("DetectorLetrasTFLite", "🧠 Ejecutando inferencia...")
            interpreter!!.run(inputBuffer, outputBuffer)

            // Convertir el buffer a array de floats
            outputBuffer.rewind()
            val outputArray = FloatArray(indexToLetter.size)
            outputBuffer.asFloatBuffer().get(outputArray)

            // Buscar el índice con mayor probabilidad
            val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: return null
            val confidence = outputArray[maxIndex]
            val predictedLetter = indexToLetter[maxIndex]

            Log.d("DetectorLetrasTFLite", "📊 Resultado: $predictedLetter (confianza: $confidence)")

            // Mostrar top 3 predicciones para debug
            val sortedResults = outputArray.mapIndexed { index, confidence ->
                Pair(indexToLetter[index], confidence)
            }.sortedByDescending { it.second }.take(3)

            Log.d("DetectorLetrasTFLite", "🏆 Top 3: ${sortedResults.joinToString { "${it.first}(${String.format("%.3f", it.second)})" }}")

            // Retornar la letra si la confianza es razonable (REDUCIDO PARA DEBUG)
            return if (confidence > 0.3f) { // Umbral muy bajo para debug
                Log.d("DetectorLetrasTFLite", "✅ Aceptando predicción: $predictedLetter")
                predictedLetter
            } else {
                Log.d("DetectorLetrasTFLite", "❌ Confianza muy baja: $confidence < 0.3")
                null
            }

        } catch (e: Exception) {
            Log.e("DetectorLetrasTFLite", "❌ Error durante inferencia: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    fun close() {
        interpreter?.close()
    }
}

