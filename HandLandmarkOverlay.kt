package com.example.twos

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class HandLandmarkOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<Pair<Float, Float>> = emptyList()

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 18f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    /**
     * Recibe lista de landmarks (una sola mano) y actualiza la vista
     */
    fun updateLandmarks(landmarks: List<List<Pair<Float, Float>>>) {
        this.landmarks = if (landmarks.isNotEmpty()) landmarks[0] else emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (landmarks.isEmpty()) return

        // Convertir coordenadas normalizadas a píxeles de pantalla
        // Para cámara frontal necesitamos transformaciones específicas
        val screenPoints = landmarks.map { (xNorm, yNorm) ->
            // Para dispositivos móviles con cámara frontal:
            // - Intercambiar X e Y (rotación 90°)
            // - Aplicar espejo en la coordenada Y final
            val x = (1f - yNorm) * width
            val y = (1f - xNorm) * height
            PointF(x, y)
        }

        // Dibujar puntos
        screenPoints.forEach { point ->
            canvas.drawCircle(point.x, point.y, 14f, pointPaint)
        }

        // Dibujar conexiones
        drawConnections(canvas, screenPoints)
    }

    private fun drawConnections(canvas: Canvas, points: List<PointF>) {
        // Conexiones estándar de MediaPipe Hand Landmarks
        val connections = listOf(
            // Pulgar
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
            // Índice
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
            // Medio
            Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
            // Anular
            Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
            // Meñique
            Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
            // Conexiones entre nudillos
            Pair(5, 9), Pair(9, 13), Pair(13, 17)
        )

        connections.forEach { (startIdx, endIdx) ->
            val start = points.getOrNull(startIdx)
            val end = points.getOrNull(endIdx)
            if (start != null && end != null) {
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
            }
        }
    }
}



