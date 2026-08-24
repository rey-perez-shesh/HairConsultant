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
        val remoteHaircuts = runCatching { remote.fetchAll() }.getOrNull()
        val haircuts = remoteHaircuts?.takeIf { it.isNotEmpty() } ?: SampleData.allHaircuts
        haircutDao.insertAll(haircuts.map { it.toEntity() })
    }
}

private fun HaircutEntity.toDomain() = Haircut(
    id = id,
    name = name,
    imageUrl = imageUrl,
    length = runCatching { HairLength.valueOf(length) }.getOrDefault(HairLength.MEDIUM),
    texture = runCatching { HairTexture.valueOf(texture) }.getOrDefault(HairTexture.STRAIGHT),
    recommendedFaceShapes = recommendedFaceShapes.mapNotNull { runCatching { FaceShape.valueOf(it) }.getOrNull() },
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
    treatment = treatment.name,
    description = description
)
