package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.hairconsultant.app.domain.model.ArType
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.GlbFit
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutGenderStyle
import com.hairconsultant.app.domain.model.TreatmentPreference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Firestore "haircuts" collection: the catalog's source of truth, edited from the admin dashboard
 * (hairconsultant-admin). Clients only read it — security rules reserve writes for admins.
 */
interface HaircutRemoteRepository {
    /**
     * Every active style, re-emitted whenever the dashboard changes the catalog. Snapshots with
     * nothing usable (e.g. offline with an empty Firestore cache) aren't emitted, so callers keep
     * whatever catalog they already have. Fails if Firestore ends the listener.
     */
    fun observeActive(): Flow<List<Haircut>>
}

class FirestoreHaircutRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : HaircutRemoteRepository {

    override fun observeActive(): Flow<List<Haircut>> = callbackFlow {
        val registration = firestore.collection("haircuts").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener
            trySend(
                snapshot.documents
                    .filter { it.getBoolean("isActive") != false }
                    .mapNotNull { it.toHaircutOrNull() }
            )
        }
        awaitClose { registration.remove() }
    }
}

private fun DocumentSnapshot.toHaircutOrNull(): Haircut? {
    val name = getString("name")?.takeIf { it.isNotBlank() } ?: return null
    val length = getString("length")?.let { runCatching { HairLength.valueOf(it) }.getOrNull() } ?: return null
    val texture = getString("texture")?.let { runCatching { HairTexture.valueOf(it) }.getOrNull() } ?: return null
    @Suppress("UNCHECKED_CAST")
    val fit = get("glbFit") as? Map<String, Any?>
    fun fitValue(key: String, default: Float) = (fit?.get(key) as? Number)?.toFloat() ?: default
    val defaults = GlbFit()
    return Haircut(
        id = id,
        name = name,
        imageUrl = getString("imageUrl").orEmpty(),
        length = length,
        texture = texture,
        recommendedFaceShapes = (get("recommendedFaceShapes") as? List<*>)
            ?.mapNotNull { (it as? String)?.let { shape -> runCatching { FaceShape.valueOf(shape) }.getOrNull() } }
            .orEmpty(),
        genderStyle = runCatching { HaircutGenderStyle.valueOf(getString("genderStyle").orEmpty()) }
            .getOrDefault(HaircutGenderStyle.UNISEX),
        treatment = runCatching { TreatmentPreference.valueOf(getString("treatment").orEmpty()) }
            .getOrDefault(TreatmentPreference.NONE),
        description = getString("description").orEmpty(),
        sortOrder = getLong("sortOrder")?.toInt() ?: 0,
        arType = runCatching { ArType.valueOf(getString("arType").orEmpty()) }.getOrDefault(ArType.AUTO),
        arAssetUrl = getString("arAssetUrl")?.takeIf { it.isNotBlank() },
        arAssetVersion = getLong("arAssetVersion")?.toInt() ?: 0,
        glbFit = GlbFit(
            scaleMul = fitValue("scaleMul", defaults.scaleMul),
            offsetX = fitValue("offsetX", defaults.offsetX),
            offsetY = fitValue("offsetY", defaults.offsetY),
            offsetZ = fitValue("offsetZ", defaults.offsetZ),
            yawOffset = fitValue("yawOffset", defaults.yawOffset)
        )
    )
}

/** In-memory stand-in used until a Firebase project is attached. */
class MockHaircutRemoteRepository : HaircutRemoteRepository {
    override fun observeActive(): Flow<List<Haircut>> = emptyFlow()
}
