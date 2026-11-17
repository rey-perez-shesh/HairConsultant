package com.hairconsultant.app.ui.haircolor

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * One picture and the hair matte that was computed from that same picture. Showing them as a pair
 * is what keeps the color from sliding off the hair when the head moves.
 *
 * @param alpha hair confidence 0..255, [maskWidth]×[maskHeight]; null shows the picture untouched.
 * @param restart true when this picture has nothing to do with the last one (a new photo, the
 *   camera just started), so the previous matte must not be blended in.
 */
class HairFrame(
    val bitmap: Bitmap,
    val alpha: ByteArray?,
    val maskWidth: Int,
    val maskHeight: Int,
    val stats: HairStats,
    val restart: Boolean = false
)

/**
 * Draws a [HairFrame] with its hair recolored. Two passes: the matte is refined against the
 * picture's own edges and steadied over time, then each hair pixel is re-dyed in OKLab, keeping
 * its light/dark detail. The dye math is a port of [HairColorMath]; keep the two in step.
 *
 * Camera and photo are the same to this class: a photo is a frame that never changes.
 */
class HairColorRenderer : GLSurfaceView.Renderer {

    /** Shade as OKLab L, a, b, then contrast gain and max lift. Replaced whole, never mutated. */
    @Volatile var shade: FloatArray = floatArrayOf(0.5f, 0f, 0f, 1f, 0.4f)
    /** 0 = untouched hair, 1 = full dye. Slider × shade on/off × compare. */
    @Volatile var amount: Float = 0f
    /** Photo: show all of it (letterboxed). Camera: fill the screen (cropped). */
    @Volatile var fitInside: Boolean = false
    /** Debug: paint the matte in magenta instead of dyeing. */
    @Volatile var showMatte: Boolean = false

    private val pending = AtomicReference<HairFrame?>()
    private val captureRequest = AtomicReference<((Bitmap?) -> Unit)?>()

    // GL thread only from here down.
    private var current: HairFrame? = null
    private var needsUpload = false
    private var matteProgram = 0
    private var dyeProgram = 0
    private var quad = 0
    private var frameTex = 0
    private var maskTex = 0
    private var frameW = 0
    private var frameH = 0
    private var maskW = 0
    private var maskH = 0
    private val matteTex = IntArray(2)
    private val matteFbo = IntArray(2)
    private var matteW = 0
    private var matteH = 0
    private var matteIndex = 0
    private var viewW = 1
    private var viewH = 1

    /** Any thread. The renderer owns [frame]'s bitmap from here and recycles it when replaced. */
    fun submit(frame: HairFrame) {
        pending.getAndSet(frame)?.bitmap?.recycle()
    }

    /** Any thread. Hands back the dyed picture at its own resolution (no crop, no bars) on the GL thread. */
    fun capture(onCaptured: (Bitmap?) -> Unit) {
        captureRequest.set(onCaptured)
    }

    /** Call when the view is going away for good (not on pause: a paused photo must survive). */
    fun release() {
        pending.getAndSet(null)?.bitmap?.recycle()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context: every GL object is gone, but the picture we hold can be uploaded again.
        matteProgram = program(VERTEX, MATTE_FRAGMENT)
        dyeProgram = program(VERTEX, DYE_FRAGMENT)
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        quad = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        val corners = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, corners.size * 4,
            ByteBuffer.allocateDirect(corners.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(corners).position(0),
            GLES30.GL_STATIC_DRAW
        )
        frameTex = texture()
        maskTex = texture()
        frameW = 0; frameH = 0; maskW = 0; maskH = 0; matteW = 0; matteH = 0
        needsUpload = current != null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = max(1, width)
        viewH = max(1, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pending.getAndSet(null)?.let { next ->
            current?.bitmap?.takeIf { it !== next.bitmap }?.recycle()
            current = next
            needsUpload = true
        }
        val frame = current
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
        if (frame == null || frame.bitmap.isRecycled) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            captureRequest.getAndSet(null)?.invoke(null)
            return
        }
        if (needsUpload) {
            needsUpload = false
            upload(frame)
            drawMatte(frame)
        }
        drawDye(frame, toScreen = true)
        captureRequest.getAndSet(null)?.let { onCaptured -> onCaptured(runCatching { captureFrame(frame) }.getOrNull()) }
    }

    private fun upload(frame: HairFrame) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
        if (frame.bitmap.width == frameW && frame.bitmap.height == frameH) {
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, frame.bitmap)
        } else {
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, frame.bitmap, 0)
            frameW = frame.bitmap.width
            frameH = frame.bitmap.height
        }

        // No matte for this picture: an all-zero one, so nothing is dyed.
        val alpha = frame.alpha?.takeIf { it.size == frame.maskWidth * frame.maskHeight }
        val w = if (alpha != null) frame.maskWidth else 1
        val h = if (alpha != null) frame.maskHeight else 1
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val bytes = ByteBuffer.wrap(alpha ?: ByteArray(1))
        if (w == maskW && h == maskH) {
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, bytes)
        } else {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, w, h, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, bytes)
            maskW = w
            maskH = h
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
    }

    /** Refines the raw mask into the next matte, reading the previous matte to steady it. */
    private fun drawMatte(frame: HairFrame) {
        val w = max(64, frameW / 2)
        val h = max(64, frameH / 2)
        var restart = frame.restart
        if (w != matteW || h != matteH) {
            if (matteW != 0) {
                GLES30.glDeleteFramebuffers(2, matteFbo, 0)
                GLES30.glDeleteTextures(2, matteTex, 0)
            }
            GLES30.glGenFramebuffers(2, matteFbo, 0)
            for (i in 0..1) {
                matteTex[i] = texture()
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, matteFbo[i])
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, matteTex[i], 0)
            }
            matteW = w
            matteH = h
            restart = true
        }
        val previous = matteIndex
        matteIndex = 1 - matteIndex
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, matteFbo[matteIndex])
        GLES30.glViewport(0, 0, matteW, matteH)
        GLES30.glUseProgram(matteProgram)
        bind(matteProgram, "uFrame", 0, frameTex)
        bind(matteProgram, "uMask", 1, maskTex)
        bind(matteProgram, "uPrev", 2, matteTex[previous])
        GLES30.glUniform2f(GLES30.glGetUniformLocation(matteProgram, "uTexel"), 1f / matteW, 1f / matteH)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(matteProgram, "uRestart"), if (restart) 1f else 0f)
        drawQuad(matteProgram)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
    }

    private fun drawDye(frame: HairFrame, toScreen: Boolean) {
        GLES30.glUseProgram(dyeProgram)
        bind(dyeProgram, "uFrame", 0, frameTex)
        bind(dyeProgram, "uMatte", 1, matteTex[matteIndex])
        val scale = if (toScreen) uvScale() else floatArrayOf(1f, 1f)
        GLES30.glUniform2f(uniform("uUvScale"), scale[0], scale[1])
        // On screen the picture's top row goes to the top; into a capture buffer it goes to row 0.
        GLES30.glUniform1f(uniform("uFlipY"), if (toScreen) 1f else 0f)
        GLES30.glUniform2f(uniform("uTexel"), 1f / frameW, 1f / frameH)
        val s = shade
        GLES30.glUniform3f(uniform("uShadeLab"), s[0], s[1], s[2])
        GLES30.glUniform1f(uniform("uGain"), s[3])
        GLES30.glUniform1f(uniform("uMaxLift"), s[4])
        GLES30.glUniform1f(uniform("uMeanL"), frame.stats.meanL)
        GLES30.glUniform1f(uniform("uStdL"), frame.stats.stdL)
        GLES30.glUniform1f(uniform("uAmount"), amount.coerceIn(0f, 1f))
        GLES30.glUniform1f(uniform("uShowMatte"), if (showMatte && toScreen) 1f else 0f)
        drawQuad(dyeProgram)
    }

    private fun uniform(name: String) = GLES30.glGetUniformLocation(dyeProgram, name)

    /** How much of the picture's UV range the view shows: under 1 crops that axis, over 1 letterboxes it. */
    private fun uvScale(): FloatArray {
        val view = viewW.toFloat() / viewH
        val image = frameW.toFloat() / max(1, frameH)
        val imageIsWider = image > view
        return if (imageIsWider != fitInside) floatArrayOf(view / image, 1f) else floatArrayOf(1f, image / view)
    }

    private fun captureFrame(frame: HairFrame): Bitmap {
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        val tex = texture()
        try {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, frameW, frameH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
            GLES30.glViewport(0, 0, frameW, frameH)
            drawDye(frame, toScreen = false)
            val pixels = ByteBuffer.allocateDirect(frameW * frameH * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, frameW, frameH, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
            return Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(pixels) }
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, viewW, viewH)
            GLES30.glDeleteFramebuffers(1, ids, 0)
            GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
        }
    }

    private fun bind(program: Int, name: String, unit: Int, tex: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, name), unit)
    }

    private fun drawQuad(program: Int) {
        val position = GLES30.glGetAttribLocation(program, "aPos")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        GLES30.glEnableVertexAttribArray(position)
        GLES30.glVertexAttribPointer(position, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** A new linear, edge-clamped texture, left bound to GL_TEXTURE_2D. */
    private fun texture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun program(vertex: String, fragment: String): Int {
        val id = GLES30.glCreateProgram()
        for ((type, source) in listOf(GLES30.GL_VERTEX_SHADER to vertex, GLES30.GL_FRAGMENT_SHADER to fragment)) {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.e(TAG, "Shader failed to compile: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glAttachShader(id, shader)
        }
        GLES30.glLinkProgram(id)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.e(TAG, "Program failed to link: ${GLES30.glGetProgramInfoLog(id)}")
        return id
    }

    private companion object {
        const val TAG = "HairColorRenderer"

        const val VERTEX = """#version 300 es
in vec2 aPos;
out vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

        // The segmenter's mask is soft but blobby, and it shimmers from frame to frame. Each tap of
        // the mask is weighted by how close its picture brightness is to this pixel's, so the matte
        // edge snaps to real edges in the picture (strands, hairline) instead of the mask's guess.
        const val MATTE_FRAGMENT = """#version 300 es
precision mediump float;
uniform sampler2D uFrame;
uniform sampler2D uMask;
uniform sampler2D uPrev;
uniform vec2 uTexel;
uniform float uRestart;
in vec2 vUv;
out vec4 outColor;

float luma(vec2 uv) { return dot(texture(uFrame, uv).rgb, vec3(0.299, 0.587, 0.114)); }

void main() {
    float here = luma(vUv);
    float sum = 0.0;
    float weight = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 uv = vUv + vec2(float(x), float(y)) * uTexel * 2.0;
            float d = luma(uv) - here;
            float w = exp(-d * d / 0.02);
            sum += texture(uMask, uv).r * w;
            weight += w;
        }
    }
    float matte = smoothstep(0.3, 0.7, sum / weight);
    // Small frame-to-frame changes are flicker and get averaged away; big ones are the head
    // actually moving and are taken at once, so the matte never trails behind.
    float prev = texture(uPrev, vUv).r;
    float trust = max(uRestart, mix(0.55, 1.0, smoothstep(0.08, 0.35, abs(matte - prev))));
    outColor = vec4(vec3(mix(prev, matte, trust)), 1.0);
}
"""

        // Port of HairColorMath.remapL / chromaScale. Change one, change the other.
        const val DYE_FRAGMENT = """#version 300 es
precision highp float;
uniform sampler2D uFrame;
uniform sampler2D uMatte;
uniform vec2 uUvScale;
uniform float uFlipY;
uniform vec2 uTexel;
uniform vec3 uShadeLab;
uniform float uGain;
uniform float uMaxLift;
uniform float uMeanL;
uniform float uStdL;
uniform float uAmount;
uniform float uShowMatte;
in vec2 vUv;
out vec4 outColor;

vec3 toLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 toSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

vec3 toOklab(vec3 srgb) {
    vec3 c = toLinear(srgb);
    vec3 lms = pow(vec3(
        0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b,
        0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b,
        0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b), vec3(1.0 / 3.0));
    return vec3(
        0.2104542553 * lms.x + 0.7936177850 * lms.y - 0.0040720468 * lms.z,
        1.9779984951 * lms.x - 2.4285922050 * lms.y + 0.4505937099 * lms.z,
        0.0259040371 * lms.x + 0.7827717662 * lms.y - 0.8086757660 * lms.z);
}

vec3 fromOklab(vec3 lab) {
    vec3 lms = vec3(
        lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z,
        lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z,
        lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z);
    lms = lms * lms * lms;
    return toSrgb(vec3(
        4.0767416621 * lms.x - 3.3077115913 * lms.y + 0.2309699292 * lms.z,
        -1.2684380046 * lms.x + 2.6097574011 * lms.y - 0.3413193965 * lms.z,
        -0.0041960863 * lms.x - 0.7034186147 * lms.y + 1.7076147010 * lms.z));
}

void main() {
    vec2 uv = vec2(vUv.x, mix(vUv.y, 1.0 - vUv.y, uFlipY));
    uv = (uv - 0.5) * uUvScale + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        outColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 src = texture(uFrame, uv).rgb;
    float matte = texture(uMatte, uv).r;
    if (uShowMatte > 0.5) {
        outColor = vec4(mix(src, vec3(1.0, 0.0, 1.0), matte * 0.7), 1.0);
        return;
    }
    float amount = matte * uAmount;
    if (amount < 0.004) {
        outColor = vec4(src, 1.0);
        return;
    }

    float L = toOklab(src).x;
    // The same pixel, blurred: contrast gain goes on this so it stretches the hair's broad
    // light and dark, not the sensor noise riding on top of it.
    vec2 r = uTexel * 2.5;
    float base = (L
        + toOklab(texture(uFrame, uv + vec2(r.x, r.y)).rgb).x
        + toOklab(texture(uFrame, uv + vec2(-r.x, r.y)).rgb).x
        + toOklab(texture(uFrame, uv + vec2(r.x, -r.y)).rgb).x
        + toOklab(texture(uFrame, uv + vec2(-r.x, -r.y)).rgb).x) * 0.2;

    float sd = max(uStdL, 0.02);
    float lift = min(uShadeLab.x - uMeanL, uMaxLift);
    float moved;
    if (lift >= 0.0) {
        float shadow = smoothstep(sd, 3.0 * sd, uMeanL - base);
        moved = L + lift * (1.0 - 0.45 * shadow) + (base - uMeanL) * (uGain - 1.0);
    } else {
        moved = L * ((uMeanL + lift) / max(uMeanL, 0.001));
    }
    float spec = smoothstep(uMeanL + 2.0 * sd, uMeanL + 4.0 * sd, L);
    float newL = clamp(mix(moved, max(moved, L), spec), 0.0, 1.0);
    float chroma = clamp(newL / max(uShadeLab.x, 0.001), 0.0, 1.25)
        * (1.0 - 0.7 * spec)
        * (1.0 - smoothstep(0.9, 1.0, newL));

    vec3 dyed = fromOklab(vec3(newL, uShadeLab.yz * chroma));
    outColor = vec4(mix(src, dyed, amount), 1.0);
}
"""
    }
}
