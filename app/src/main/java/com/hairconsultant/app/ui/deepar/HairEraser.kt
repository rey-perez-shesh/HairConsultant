package com.hairconsultant.app.ui.deepar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import com.hairconsultant.app.data.analysis.LiveHairSegmenter
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Hides the user's real hair in DeepAR camera frames, so the 3D hair from the `.deepar`
 * effect looks like their own (the "TikTok hair swap" look).
 *
 * Each camera frame is edited in place before it reaches DeepAR:
 * 1. A small upright copy goes to MediaPipe's hair segmenter (async, off the main thread).
 * 2. When a mask comes back, the hair area is grown slightly and filled with a smooth blur of
 *    the surrounding pixels (push-pull inpainting), with feathered edges. That fill is kept as
 *    a small overlay bitmap.
 * 3. Every frame, the latest overlay is scaled up over the frame, which blurs the real hair away.
 *
 * The overlay lags the camera by about one segmentation (a few frames), which isn't noticeable
 * at normal head speed. Turn the whole effect off with [ENABLED].
 */
class HairEraser(context: Context) : AutoCloseable {

    private class Overlay(val bitmap: Bitmap, val bounds: Rect)

    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    /** Only one segmentation runs at a time, so these describe the frame in flight. */
    @Volatile private var submitted: Bitmap? = null
    @Volatile private var submittedRotation = 0

    @Volatile private var overlay: Overlay? = null

    // Main-thread state.
    private var frame: Bitmap? = null
    private var lastTimestamp = 0L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()

    private val segmenter: ImageSegmenter? = runCatching {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(LiveHairSegmenter.MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .setResultListener(::onResult)
            .setErrorListener { error ->
                busy.set(false)
                Log.w(TAG, "Hair segmenter error", error)
            }
            .build()
        ImageSegmenter.createFromOptions(context, options)
    }.onFailure { Log.e(TAG, "Couldn't create hair segmenter; real hair stays visible", it) }
        .getOrNull()

    private val hairClass: Int = segmenter?.let { s ->
        runCatching { s.labels }.getOrNull().orEmpty()
            .indexOfFirst { it.equals("hair", ignoreCase = true) }
            .takeIf { it >= 0 }
    } ?: DEFAULT_HAIR_CLASS

    /**
     * Blurs the real hair out of one RGBA_8888 camera frame, in place. Call on the thread that
     * feeds DeepAR, before `receiveFrame`. Frames it can't handle are left untouched.
     */
    fun process(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, rotationDegrees: Int) {
        if (closed.get() || segmenter == null) return
        // Padded rows would need a per-row copy; skip rather than corrupt the frame.
        if (rowStride != width * 4 || buffer.capacity() < width * height * 4) return

        val bitmap = frame?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { frame = it }
        buffer.position(0)
        bitmap.copyPixelsFromBuffer(buffer)

        submit(bitmap, rotationDegrees)

        val current = overlay ?: run { buffer.position(0); return }
        val sx = width.toFloat() / current.bitmap.width
        val sy = height.toFloat() / current.bitmap.height
        val b = current.bounds
        dst.set(
            (b.left * sx).toInt(),
            (b.top * sy).toInt(),
            min(width, ceil(b.right * sx).toInt()),
            min(height, ceil(b.bottom * sy).toInt())
        )
        Canvas(bitmap).drawBitmap(current.bitmap, b, dst, paint)

        buffer.position(0)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.position(0)
    }

    private fun submit(frame: Bitmap, rotationDegrees: Int) {
        val s = segmenter ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            // Small and upright: the model expects an upright face, and small keeps it fast.
            val scale = SEGMENT_SIZE.toFloat() / max(frame.width, frame.height)
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotationDegrees.toFloat())
            }
            val upright = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
            var timestamp = SystemClock.uptimeMillis()
            if (timestamp <= lastTimestamp) timestamp = lastTimestamp + 1
            lastTimestamp = timestamp
            submitted = upright
            submittedRotation = rotationDegrees
            s.segmentAsync(BitmapImageBuilder(upright).build(), timestamp)
        } catch (t: Throwable) {
            busy.set(false)
            Log.w(TAG, "segmentAsync failed: ${t.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onResult(result: ImageSegmenterResult, input: MPImage) {
        try {
            if (closed.get()) return
            val upright = submitted ?: return
            val mask = result.categoryMask().orElse(null) ?: return
            val w = mask.width
            val h = mask.height
            if (w != upright.width || h != upright.height) return
            val buffer = ByteBufferExtractor.extract(mask).apply { rewind() }
            if (buffer.remaining() < w * h) return
            val labels = ByteArray(w * h).also { buffer.get(it) }
            overlay = buildOverlay(upright, labels, w, h, submittedRotation)
        } catch (t: Throwable) {
            Log.w(TAG, "Hair overlay failed: ${t.message}")
        } finally {
            busy.set(false)
        }
    }

    private fun buildOverlay(upright: Bitmap, labels: ByteArray, w: Int, h: Int, rotation: Int): Overlay? {
        val n = w * h
        var hairPixels = 0
        val hair = FloatArray(n) { i ->
            if ((labels[i].toInt() and 0xFF) == hairClass) { hairPixels++; 1f } else 0f
        }
        if (hairPixels == 0) return null

        // Grow the mask a little so stray strands at the edge are covered too.
        val hole = dilate(hair, w, h, DILATE_RADIUS)
        val pixels = IntArray(n).also { upright.getPixels(it, 0, w, 0, 0, w, h) }
        val fill = inpaint(pixels, hole, w, h)
        val alpha = boxBlur(hole, w, h, FEATHER_RADIUS)

        val out = IntArray(n) { i ->
            val a = (alpha[i] * 255f).toInt().coerceIn(0, 255)
            (a shl 24) or (fill[i] and 0x00FFFFFF)
        }
        val uprightOverlay = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        val sensorOverlay = if (rotation % 360 != 0) {
            Bitmap.createBitmap(uprightOverlay, 0, 0, w, h, Matrix().apply { postRotate(-rotation.toFloat()) }, true)
        } else {
            uprightOverlay
        }
        val bounds = alphaBounds(sensorOverlay) ?: return null
        return Overlay(sensorOverlay, bounds)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { segmenter?.close() }
        overlay = null
        submitted = null
        frame = null
    }

    companion object {
        private const val TAG = "HairEraser"

        /** Set to false (and rebuild) to show the user's real hair under the 3D hair again. */
        const val ENABLED = true

        private const val DEFAULT_HAIR_CLASS = 1
        /** Long side of the image sent to the segmenter. */
        private const val SEGMENT_SIZE = 256
        /** In segmenter pixels; at 256 px, 1 px is about 5 px of a 1280 px camera frame. */
        private const val DILATE_RADIUS = 3
        private const val FEATHER_RADIUS = 3
    }
}

/** Bounding box of pixels with any alpha, padded by 1 px, or null if fully transparent. */
private fun alphaBounds(bitmap: Bitmap): Rect? {
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
    var left = w
    var top = h
    var right = -1
    var bottom = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            if ((px[row + x] ushr 24) != 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < 0) return null
    return Rect(max(0, left - 1), max(0, top - 1), min(w, right + 2), min(h, bottom + 2))
}

/** Separable square max filter. */
private fun dilate(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
    val tmp = FloatArray(src.size)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            var m = 0f
            for (k in max(0, x - r)..min(w - 1, x + r)) m = max(m, src[row + k])
            tmp[row + x] = m
        }
    }
    val out = FloatArray(src.size)
    for (x in 0 until w) {
        for (y in 0 until h) {
            var m = 0f
            for (k in max(0, y - r)..min(h - 1, y + r)) m = max(m, tmp[k * w + x])
            out[y * w + x] = m
        }
    }
    return out
}

/** Separable box blur with clamped edges. */
private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
    val tmp = FloatArray(src.size)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += src[row + (x + k).coerceIn(0, w - 1)]
            tmp[row + x] = sum / (2 * r + 1)
        }
    }
    val out = FloatArray(src.size)
    for (x in 0 until w) {
        for (y in 0 until h) {
            var sum = 0f
            for (k in -r..r) sum += tmp[(y + k).coerceIn(0, h - 1) * w + x]
            out[y * w + x] = sum / (2 * r + 1)
        }
    }
    return out
}

/**
 * Push-pull inpainting: fills pixels where [hole] is 1 with a smooth blend of the known pixels
 * around them, at every scale, so even large hair areas get a soft, blurred fill.
 * Returns opaque ARGB colours.
 */
private fun inpaint(pixels: IntArray, hole: FloatArray, w: Int, h: Int): IntArray {
    class Level(val w: Int, val h: Int) {
        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val b = FloatArray(w * h)
        val wt = FloatArray(w * h)
    }

    // Level 0: colours premultiplied by how "known" each pixel is.
    val base = Level(w, h)
    for (i in pixels.indices) {
        val known = 1f - hole[i]
        val c = pixels[i]
        base.r[i] = ((c shr 16) and 0xFF) * known
        base.g[i] = ((c shr 8) and 0xFF) * known
        base.b[i] = (c and 0xFF) * known
        base.wt[i] = known
    }

    // Push: halve until 1x1, averaging premultiplied colour and weight.
    val levels = mutableListOf(base)
    while (levels.last().w > 1 || levels.last().h > 1) {
        val f = levels.last()
        val c = Level((f.w + 1) / 2, (f.h + 1) / 2)
        for (y in 0 until c.h) for (x in 0 until c.w) {
            var r = 0f; var g = 0f; var b = 0f; var wt = 0f; var count = 0
            for (dy in 0..1) for (dx in 0..1) {
                val fx = x * 2 + dx
                val fy = y * 2 + dy
                if (fx < f.w && fy < f.h) {
                    val i = fy * f.w + fx
                    r += f.r[i]; g += f.g[i]; b += f.b[i]; wt += f.wt[i]; count++
                }
            }
            val i = y * c.w + x
            c.r[i] = r / count; c.g[i] = g / count; c.b[i] = b / count; c.wt[i] = wt / count
        }
        levels.add(c)
    }

    // Top: normalise to plain colour (grey if nothing at all was known).
    val top = levels.last()
    if (top.wt[0] > 1e-6f) {
        top.r[0] /= top.wt[0]; top.g[0] /= top.wt[0]; top.b[0] /= top.wt[0]
    } else {
        top.r[0] = 128f; top.g[0] = 128f; top.b[0] = 128f
    }
    top.wt[0] = 1f

    // Pull: fill each finer level's unknown share from the (already filled) coarser level.
    for (li in levels.size - 2 downTo 0) {
        val f = levels[li]
        val c = levels[li + 1]
        for (y in 0 until f.h) for (x in 0 until f.w) {
            val i = y * f.w + x
            val missing = 1f - f.wt[i]
            if (missing > 0f) {
                val cx = ((x + 0.5f) / 2f - 0.5f).coerceIn(0f, (c.w - 1).toFloat())
                val cy = ((y + 0.5f) / 2f - 0.5f).coerceIn(0f, (c.h - 1).toFloat())
                f.r[i] += missing * bilinear(c.r, c.w, c.h, cx, cy)
                f.g[i] += missing * bilinear(c.g, c.w, c.h, cx, cy)
                f.b[i] += missing * bilinear(c.b, c.w, c.h, cx, cy)
            }
            f.wt[i] = 1f
        }
    }

    return IntArray(pixels.size) { i ->
        val r = base.r[i].toInt().coerceIn(0, 255)
        val g = base.g[i].toInt().coerceIn(0, 255)
        val b = base.b[i].toInt().coerceIn(0, 255)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

private fun bilinear(data: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
    val x0 = x.toInt()
    val y0 = y.toInt()
    val x1 = min(x0 + 1, w - 1)
    val y1 = min(y0 + 1, h - 1)
    val fx = x - x0
    val fy = y - y0
    val top = data[y0 * w + x0] * (1 - fx) + data[y0 * w + x1] * fx
    val bottom = data[y1 * w + x0] * (1 - fx) + data[y1 * w + x1] * fx
    return top * (1 - fy) + bottom * fy
}
