package com.hairconsultant.app.ui.facescan

import android.graphics.Color as AndroidColor
import android.util.Log
import com.google.android.filament.Colors
import com.google.android.filament.MaterialInstance
import io.github.sceneview.material.setBaseColorFactor
import io.github.sceneview.material.setBaseColorIndex
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.ModelNode

/**
 * Applies try-on hair color to the live Filament GLB materials.
 *
 * The prepared wig (`Waves_AR_Prepared.glb`) uses a glTF PBR material with a baked
 * baseColorTexture. Runtime recoloring uses Filament ubershader parameters:
 * - baseColorFactor — selected hair color (sRGB)
 * - baseColorIndex — `-1` for solid tint (keeps metallic/roughness maps + lighting),
 *   `0` for NATURAL so the authored albedo texture is restored
 *
 * Does not reload the scene or change transforms.
 */
object HairMaterialTint {
    private const val TAG = "HairMaterialTint"

    fun apply(node: ModelNode, preset: HairColorPreset, assetName: String = WIG_ASSET) {
        val materials = collectMaterials(node)
        val hex = toHex(preset.r, preset.g, preset.b)

        if (materials.isEmpty()) {
            Log.e(
                TAG,
                "HairColor=${preset.name} Asset=$assetName Materials=[] " +
                    "AppliedColor=$hex — NO material instances found; tint skipped"
            )
            return
        }

        val names = materials.map { it.first }
        var applied = 0
        for ((label, mat) in materials) {
            if (applyColor(mat, preset)) {
                applied++
                Log.i(TAG, "Tinted material '$label' → $hex (${preset.name})")
            } else {
                Log.w(TAG, "Failed to tint material '$label'")
            }
        }

        Log.i(
            TAG,
            "HairColor=${preset.name} Asset=$assetName " +
                "Materials=$names AppliedColor=$hex AppliedCount=$applied/${materials.size}"
        )
    }

    private fun collectMaterials(node: ModelNode): List<Pair<String, MaterialInstance>> {
        val out = LinkedHashMap<Int, Pair<String, MaterialInstance>>()
        runCatching {
            val byRenderable: List<List<MaterialInstance>>? = node.materialInstances
            byRenderable.orEmpty().forEachIndexed { renderableIndex, primitiveMats ->
                primitiveMats.orEmpty().forEachIndexed { primIndex, mat ->
                    val key = System.identityHashCode(mat)
                    val name = runCatching { mat.name }.getOrNull().orEmpty().ifBlank { "unnamed" }
                    out[key] = "r${renderableIndex}p$primIndex:$name" to mat
                }
            }
        }.onFailure { Log.w(TAG, "node.materialInstances failed: ${it.message}") }

        // Fallback: walk Filament renderables via SceneView ModelNode API.
        if (out.isEmpty()) {
            runCatching {
                node.renderableNodes.orEmpty().forEachIndexed { renderableIndex, renderable ->
                    val primitiveMats: List<MaterialInstance>? = renderable.materialInstances
                    primitiveMats.orEmpty().forEachIndexed { primIndex, mat ->
                        val key = System.identityHashCode(mat)
                        val name = runCatching { mat.name }.getOrNull().orEmpty().ifBlank { "unnamed" }
                        out[key] = "rn${renderableIndex}p$primIndex:$name" to mat
                    }
                }
            }.onFailure { Log.w(TAG, "renderableNodes materials failed: ${it.message}") }
        }

        return out.values.toList()
    }

    private fun applyColor(mat: MaterialInstance, preset: HairColorPreset): Boolean {
        val r = preset.r
        val g = preset.g
        val b = preset.b

        val okSrgb = runCatching {
            when (preset) {
                HairColorPreset.NATURAL -> {
                    mat.setParameter("baseColorIndex", 0)
                    mat.setParameter("baseColorFactor", Colors.RgbaType.SRGB, 1f, 1f, 1f, 1f)
                }
                HairColorPreset.BLACK -> {
                    // Meshy albedo is mid-gray; texture multiply (index 0) stays washed out.
                    // Solid near-black tint keeps roughness/normal/specular for strand highlights.
                    mat.setParameter("baseColorIndex", -1)
                    mat.setParameter("baseColorFactor", Colors.RgbaType.SRGB, r, g, b, 1f)
                }
                else -> {
                    mat.setParameter("baseColorIndex", -1)
                    mat.setParameter("baseColorFactor", Colors.RgbaType.SRGB, r, g, b, 1f)
                }
            }
        }.onFailure { Log.w(TAG, "SRGB setParameter failed: ${it.message}") }.isSuccess

        if (okSrgb) return true

        return runCatching {
            when (preset) {
                HairColorPreset.NATURAL -> {
                    mat.setBaseColorIndex(0)
                    mat.setBaseColorFactor(colorOf(1f, 1f, 1f, 1f))
                }
                HairColorPreset.BLACK -> {
                    mat.setBaseColorIndex(-1)
                    mat.setBaseColorFactor(colorOf(r, g, b, 1f))
                }
                else -> {
                    mat.setBaseColorIndex(-1)
                    mat.setBaseColorFactor(colorOf(r, g, b, 1f))
                }
            }
        }.onFailure { Log.w(TAG, "SceneView tint fallback failed: ${it.message}") }.isSuccess
    }

    private fun toHex(r: Float, g: Float, b: Float): String {
        val color = AndroidColor.rgb(
            (r.coerceIn(0f, 1f) * 255f).toInt(),
            (g.coerceIn(0f, 1f) * 255f).toInt(),
            (b.coerceIn(0f, 1f) * 255f).toInt()
        )
        return String.format("#%06X", 0xFFFFFF and color)
    }
}
