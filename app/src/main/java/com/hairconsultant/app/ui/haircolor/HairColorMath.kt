package com.hairconsultant.app.ui.haircolor

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Lightness of the hair in one frame, in OKLab L (0..1), plus how much of the frame is hair. */
data class HairStats(val meanL: Float, val stdL: Float, val coverage: Float) {
    companion object {
        /** Mid-brown guess used until the first mask arrives. */
        val Default = HairStats(meanL = 0.35f, stdL = 0.08f, coverage = 0f)
    }
}

/**
 * The hair recolor, on the CPU. [HairColorRenderer]'s fragment shader is a line-for-line port of
 * [remapL] and [chromaScale]; change one and change the other.
 *
 * A dye is not a tint: each pixel keeps its own light/dark detail (strands, shine, shadow) and only
 * the tone is replaced. Lightness is shifted relative to the hair's measured average, so the same
 * shade lands in the same place on black, brown or blonde hair.
 */
object HairColorMath {
    /** How much of the lift deep shadows (roots, partings) give up, so lightened hair keeps depth. */
    const val SHADOW_KEEP = 0.45f
    /** How far speculars are pulled back to neutral; shine on real hair is the light's color, not the dye's. */
    const val SPECULAR_DESATURATION = 0.7f
    const val MIN_STD = 0.02f
    const val MAX_CHROMA_SCALE = 1.25f

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun srgbToLinear(c: Float) =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
    }

    /** sRGB components 0..1 → OKLab `[L, a, b]`. */
    fun srgbToOklab(r: Float, g: Float, b: Float): FloatArray {
        val lr = srgbToLinear(r)
        val lg = srgbToLinear(g)
        val lb = srgbToLinear(b)
        val l = cbrt(0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb)
        val m = cbrt(0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb)
        val s = cbrt(0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb)
        return floatArrayOf(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        )
    }

    /** OKLab → sRGB components, clamped to 0..1. */
    fun oklabToSrgb(lightness: Float, a: Float, b: Float): FloatArray {
        val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).pow(3)
        val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).pow(3)
        val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).pow(3)
        return floatArrayOf(
            linearToSrgb(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s),
            linearToSrgb(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s),
            linearToSrgb(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s)
        )
    }

    fun oklabOf(argb: Int): FloatArray =
        srgbToOklab((argb shr 16 and 0xFF) / 255f, (argb shr 8 and 0xFF) / 255f, (argb and 0xFF) / 255f)

    /** How bright a pixel is against the rest of the hair: 0 = ordinary strand, 1 = specular shine. */
    fun specular(l: Float, stats: HairStats): Float {
        val sd = max(stats.stdL, MIN_STD)
        return smoothstep(stats.meanL + 2f * sd, stats.meanL + 4f * sd, l)
    }

    /**
     * New lightness for a hair pixel of lightness [l].
     *
     * [baseL] is the same pixel blurred: the contrast gain is applied to that, so lightening dark
     * hair stretches the broad light/dark shape without also amplifying sensor noise. Pass [l]
     * when there is no blurred copy.
     */
    fun remapL(l: Float, baseL: Float, shade: HairShade, stats: HairStats): Float {
        val sd = max(stats.stdL, MIN_STD)
        val lift = min(shade.l - stats.meanL, shade.maxLift)
        val moved = if (lift >= 0f) {
            val shadow = smoothstep(sd, 3f * sd, stats.meanL - baseL)
            l + lift * (1f - SHADOW_KEEP * shadow) + (baseL - stats.meanL) * (shade.contrastGain - 1f)
        } else {
            // Going darker behaves like pigment: everything scales down, detail included.
            l * ((stats.meanL + lift) / max(stats.meanL, 1e-3f))
        }
        // Shine never gets darker than it was, or dyed-black hair reads as matte paint.
        return mix(moved, max(moved, l), specular(l, stats)).coerceIn(0f, 1f)
    }

    /** Scale for the shade's a/b at a pixel: tone follows lightness, and fades out of shine and near-white. */
    fun chromaScale(newL: Float, originalL: Float, shade: HairShade, stats: HairStats): Float {
        val byLightness = (newL / max(shade.l, 1e-3f)).coerceIn(0f, MAX_CHROMA_SCALE)
        return byLightness *
            (1f - SPECULAR_DESATURATION * specular(originalL, stats)) *
            (1f - smoothstep(0.9f, 1f, newL))
    }

    /** One pixel through the whole recolor. [amount] is matte alpha × the intensity slider, 0..1. */
    fun recolor(argb: Int, shade: HairShade, stats: HairStats, amount: Float): Int {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val lab = srgbToOklab(r, g, b)
        val newL = remapL(lab[0], lab[0], shade, stats)
        val chroma = chromaScale(newL, lab[0], shade, stats)
        val dyed = oklabToSrgb(newL, shade.a * chroma, shade.b * chroma)
        val t = amount.coerceIn(0f, 1f)
        fun channel(from: Float, to: Float) = (mix(from, to, t) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (argb and 0xFF000000.toInt()) or
            (channel(r, dyed[0]) shl 16) or (channel(g, dyed[1]) shl 8) or channel(b, dyed[2])
    }

    /**
     * Average hair lightness from a matte ([alpha], 0..255 per pixel, [width]×[height]) and the
     * picture it belongs to. [pixelAt] takes matte coordinates and returns that spot's ARGB.
     * Only confident hair counts, so skin at a soft edge doesn't drag the average.
     */
    fun hairStats(alpha: ByteArray, width: Int, height: Int, stride: Int, pixelAt: (Int, Int) -> Int): HairStats {
        var weight = 0.0
        var sum = 0.0
        var sumSq = 0.0
        var samples = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                samples++
                val a = alpha[y * width + x].toInt() and 0xFF
                if (a >= 128) {
                    val w = a / 255.0
                    val l = oklabOf(pixelAt(x, y))[0]
                    weight += w
                    sum += w * l
                    sumSq += w * l * l
                }
                x += stride
            }
            y += stride
        }
        if (weight <= 0.0) return HairStats.Default
        val mean = sum / weight
        val variance = max(0.0, sumSq / weight - mean * mean)
        return HairStats(mean.toFloat(), sqrt(variance).toFloat(), (weight / samples).toFloat())
    }
}
