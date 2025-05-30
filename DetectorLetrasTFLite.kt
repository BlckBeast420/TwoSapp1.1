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
        } catch (e: Exception) {
            Log.e("DetectorLetrasTFLite", "❌ Error cargando modelo: ${e.message}")
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
        if (interpreter == null || landmarks.size != 21) return null

        try {
            // 21 puntos × 2 coordenadas = 42 floats
            val inputBuffer = ByteBuffer.allocateDirect(42 * 4).order(ByteOrder.nativeOrder())
            landmarks.forEach { (x, y) ->
                inputBuffer.putFloat(x)
                inputBuffer.putFloat(y)
            }

            // El modelo tiene una salida de 21 letras → tamaño del array = 21
            val outputBuffer = ByteBuffer.allocateDirect(indexToLetter.size * 4).order(ByteOrder.nativeOrder())
            interpreter!!.run(inputBuffer, outputBuffer)

            // Convertir el buffer a array de floats
            outputBuffer.rewind()
            val outputArray = FloatArray(indexToLetter.size)
            outputBuffer.asFloatBuffer().get(outputArray)

            // Buscar el índice con mayor probabilidad
            val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: return null
            val confidence = outputArray[maxIndex]

            // Retornar la letra si la confianza es razonable
            return if (confidence > 0.7f) {
                indexToLetter[maxIndex]
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e("DetectorLetrasTFLite", "❌ Error durante inferencia: ${e.message}")
            return null
        }
    }

    fun close() {
        interpreter?.close()
    }
}

