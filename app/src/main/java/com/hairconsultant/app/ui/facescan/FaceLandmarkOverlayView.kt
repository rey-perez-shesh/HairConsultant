package com.hairconsultant.app.ui.facescan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.hairconsultant.app.data.analysis.FaceOverlayFrame
import com.hairconsultant.app.data.analysis.HairMask
import com.hairconsultant.app.domain.model.HairLength
import kotlin.math.max

/** Draws the MediaPipe face mesh, hair mask, and a dashed oval so the user can line up for a real scan. */
class FaceLandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var frame: FaceOverlayFrame = FaceOverlayFrame.idle()
    private val ovalBounds = RectF()
    private val hairDst = RectF()
    private var hairBitmap: Bitmap? = null

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7EE0C6")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F6E27A")
        strokeWidth = 6f
        style = Paint.Style.FILL
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(22f, 16f), 0f)
    }
    private val foundGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7EE0C6")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(22f, 16f), 0f)
    }
    private val hairPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 36f
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
    }

    fun setFrame(frame: FaceOverlayFrame) {
        this.frame = frame
        val previous = hairBitmap
        hairBitmap = frame.hairMask?.toOverlayBitmap()
        if (previous != null && previous !== hairBitmap && !previous.isRecycled) {
            previous.recycle()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val guideWidth = width * 0.72f
        val guideHeight = guideWidth * 1.28f
        ovalBounds.set(
            (width - guideWidth) / 2f,
            (height - guideHeight) / 2.4f,
            (width + guideWidth) / 2f,
            (height - guideHeight) / 2.4f + guideHeight
        )

        val scale = max(width / frame.imageWidth.toFloat(), height / frame.imageHeight.toFloat())
        val offsetX = (width - frame.imageWidth * scale) / 2f
        val offsetY = (height - frame.imageHeight * scale) / 2f
        hairBitmap?.let { overlay ->
            if (frame.estimatedHairLength != HairLength.BALD) {
                hairDst.set(
                    offsetX,
                    offsetY,
                    offsetX + frame.imageWidth * scale,
                    offsetY + frame.imageHeight * scale
                )
                canvas.drawBitmap(overlay, null, hairDst, hairPaint)
            }
        }

        canvas.drawOval(ovalBounds, if (frame.faceDetected) foundGuidePaint else guidePaint)

        if (frame.faceDetected && frame.points.isNotEmpty()) {
            FaceLandmarker.FACE_LANDMARKS_CONNECTORS.filterNotNull().forEach { connector ->
                val start = frame.points.getOrNull(connector.start()) ?: return@forEach
                val end = frame.points.getOrNull(connector.end()) ?: return@forEach
                canvas.drawLine(
                    start.x * frame.imageWidth * scale + offsetX,
                    start.y * frame.imageHeight * scale + offsetY,
                    end.x * frame.imageWidth * scale + offsetX,
                    end.y * frame.imageHeight * scale + offsetY,
                    meshPaint
                )
            }
            frame.points.forEach { point ->
                canvas.drawPoint(
                    point.x * frame.imageWidth * scale + offsetX,
                    point.y * frame.imageHeight * scale + offsetY,
                    pointPaint
                )
            }
        }

        canvas.drawText(frame.statusMessage, width / 2f, ovalBounds.bottom + 56f, textPaint)
    }
}

private fun HairMask.toOverlayBitmap(color: Int = 0xAA9B59B6.toInt()): Bitmap {
    val pixels = IntArray(width * height)
    for (index in labels.indices) {
        if (isHair(index)) pixels[index] = color
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
