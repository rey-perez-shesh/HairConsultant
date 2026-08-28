package com.hairconsultant.app.data.repository

import com.hairconsultant.app.data.SampleData
import com.hairconsultant.app.data.local.dao.HaircutDao
import com.hairconsultant.app.data.local.entity.HaircutEntity
import com.hairconsultant.app.data.remote.firebase.HaircutRemoteRepository
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutCluster
import com.hairconsultant.app.domain.model.HaircutGenderStyle
import com.hairconsultant.app.domain.model.TreatmentPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HaircutRepository {
    /** All haircuts grouped into the Home screen's length x texture clusters. */
    fun observeClusters(): Flow<List<HaircutCluster>>
    fun observeMatching(faceShape: FaceShape, length: HairLength, texture: HairTexture): Flow<List<Haircut>>
    suspend fun refresh()
}

class HaircutRepositoryImpl(
    private val haircutDao: HaircutDao,
    private val remote: HaircutRemoteRepository
) : HaircutRepository {

    override fun observeClusters(): Flow<List<HaircutCluster>> = haircutDao.observeAll().map { entities ->
        val haircuts = entities.map { it.toDomain() }
        HairLength.entries.filter { it != HairLength.BALD }.flatMap { length ->
            HairTexture.entries.map { texture ->
                HaircutCluster(
                    length = length,
                    texture = texture,
                    haircuts = haircuts.filter { it.length == length && it.texture == texture }
                )
            }
        }.filter { it.haircuts.isNotEmpty() }
    }

    override fun observeMatching(
        faceShape: FaceShape,
        length: HairLength,
        texture: HairTexture
    ): Flow<List<Haircut>> = haircutDao.observeByCluster(length.name, texture.name).map { entities ->
        entities.map { it.toDomain() }.filter { faceShape in it.recommendedFaceShapes }
    }

    override suspend fun refresh() {
        // One-time migration: populates Firestore's "haircuts" collection from the curated
        // catalog the first time this runs against an empty project; a no-op afterwards.
        runCatching { remote.seedIfEmpty(SampleData.allHaircuts) }
        // Patch only the Meshy-replaced rows (names/thumbnails) without rebuilding the catalog.
        val meshyPatches = SampleData.allHaircuts.filter { it.id in SampleData.MESHY_REPLACED_IDS }
        runCatching { remote.upsertHaircuts(meshyPatches) }
        // Merge in newly added catalog cards when Firestore was seeded from an older SampleData snapshot.
        val newCatalogEntries = SampleData.allHaircuts.filter { it.id in SampleData.NEW_CATALOG_IDS }
        runCatching { remote.upsertHaircuts(newCatalogEntries) }
        val genderPatches = SampleData.allHaircuts.filter { it.id in SampleData.GENDER_UNISEX_PATCH_IDS }
        runCatching { remote.upsertHaircuts(genderPatches) }
        val thumbPatches = SampleData.allHaircuts.filter { it.id in SampleData.THUMB_PATCH_IDS }
        runCatching { remote.upsertHaircuts(thumbPatches) }
        val remoteHaircuts = runCatching { remote.fetchAll() }.getOrNull()
        val haircuts = applyThumbCatalogPatches(
            applyMeshyCatalogPatches(
                applyGenderStylePatches(
                    mergeNewCatalogEntries(
                        remoteHaircuts?.takeIf { it.isNotEmpty() } ?: SampleData.allHaircuts
                    )
                )
            )
        )
        haircutDao.insertAll(haircuts.map { it.toEntity() })
    }
}

/** Prefer SampleData thumbnail for catalog cards with corrected reference images. */
private fun applyThumbCatalogPatches(haircuts: List<Haircut>): List<Haircut> {
    val patches = SampleData.allHaircuts
        .filter { it.id in SampleData.THUMB_PATCH_IDS }
        .associateBy { it.id }
    if (patches.isEmpty()) return haircuts
    return haircuts.map { haircut ->
        val patch = patches[haircut.id] ?: return@map haircut
        haircut.copy(imageUrl = patch.imageUrl)
    }
}

/** Prefer SampleData name/thumbnail/description for the three Meshy-replaced IDs. */
private fun applyMeshyCatalogPatches(haircuts: List<Haircut>): List<Haircut> {
    val patches = SampleData.allHaircuts
        .filter { it.id in SampleData.MESHY_REPLACED_IDS }
        .associateBy { it.id }
    if (patches.isEmpty()) return haircuts
    return haircuts.map { haircut ->
        val patch = patches[haircut.id] ?: return@map haircut
        haircut.copy(
            name = patch.name,
            imageUrl = patch.imageUrl,
            description = patch.description,
            genderStyle = patch.genderStyle
        )
    }
}

/** Keep Curtains / Side Bangs visible for Male and Female filters (UNISEX). */
private fun applyGenderStylePatches(haircuts: List<Haircut>): List<Haircut> {
    val patches = SampleData.allHaircuts
        .filter { it.id in SampleData.GENDER_UNISEX_PATCH_IDS }
        .associateBy { it.id }
    if (patches.isEmpty()) return haircuts
    return haircuts.map { haircut ->
        val patch = patches[haircut.id] ?: return@map haircut
        haircut.copy(genderStyle = patch.genderStyle)
    }
}

/** Add catalog cards introduced after the initial Firestore seed. */
private fun mergeNewCatalogEntries(haircuts: List<Haircut>): List<Haircut> {
    val additions = SampleData.allHaircuts.filter { it.id in SampleData.NEW_CATALOG_IDS }
    if (additions.isEmpty()) return haircuts
    val byId = haircuts.associateBy { it.id }.toMutableMap()
    additions.forEach { byId[it.id] = it }
    return byId.values.toList()
}

private fun HaircutEntity.toDomain() = Haircut(
    id = id,
    name = name,
    imageUrl = imageUrl,
    length = runCatching { HairLength.valueOf(length) }.getOrDefault(HairLength.MEDIUM),
    texture = runCatching { HairTexture.valueOf(texture) }.getOrDefault(HairTexture.STRAIGHT),
    recommendedFaceShapes = recommendedFaceShapes.mapNotNull { runCatching { FaceShape.valueOf(it) }.getOrNull() },
    genderStyle = runCatching { HaircutGenderStyle.valueOf(genderStyle) }.getOrDefault(HaircutGenderStyle.UNISEX),
    treatment = runCatching { TreatmentPreference.valueOf(treatment) }.getOrDefault(TreatmentPreference.NONE),
    description = description
)

private fun Haircut.toEntity() = HaircutEntity(
    id = id,
    name = name,
    imageUrl = imageUrl,
    length = length.name,
    texture = texture.name,
    recommendedFaceShapes = recommendedFaceShapes.map { it.name },
    genderStyle = genderStyle.name,
    treatment = treatment.name,
    description = description
)
