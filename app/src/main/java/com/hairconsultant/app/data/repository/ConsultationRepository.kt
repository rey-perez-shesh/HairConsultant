package com.hairconsultant.app.data.repository

import com.hairconsultant.app.data.local.dao.ConsultationDao
import com.hairconsultant.app.data.local.dao.HaircutDao
import com.hairconsultant.app.data.local.entity.ConsultationEntity
import com.hairconsultant.app.data.remote.firebase.ConsultationRemoteRepository
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface ConsultationRepository {
    fun observeHistory(userId: String): Flow<List<Consultation>>
    fun observeFavorites(userId: String): Flow<List<Consultation>>
    suspend fun save(consultation: Consultation)
    suspend fun setFavorite(consultationId: String, isFavorite: Boolean)
    suspend fun clearHistory(userId: String)
    suspend fun clearFavorites(userId: String)
}

class ConsultationRepositoryImpl(
    private val consultationDao: ConsultationDao,
    private val haircutDao: HaircutDao,
    private val remote: ConsultationRemoteRepository
) : ConsultationRepository {

    override fun observeHistory(userId: String): Flow<List<Consultation>> =
        combine(consultationDao.observeHistory(userId), haircutDao.observeAll()) { history, haircuts ->
            history.map { it.toDomain(haircuts.find { h -> h.id == it.selectedHaircutId }?.toHaircutOrNull()) }
        }

    override fun observeFavorites(userId: String): Flow<List<Consultation>> =
        combine(consultationDao.observeFavorites(userId), haircutDao.observeAll()) { favorites, haircuts ->
            favorites.map { it.toDomain(haircuts.find { h -> h.id == it.selectedHaircutId }?.toHaircutOrNull()) }
        }

    override suspend fun save(consultation: Consultation) {
        consultationDao.upsert(consultation.toEntity())
        runCatching { remote.save(consultation) }
    }

    override suspend fun setFavorite(consultationId: String, isFavorite: Boolean) {
        consultationDao.setFavorite(consultationId, isFavorite)
    }

    override suspend fun clearHistory(userId: String) {
        consultationDao.clearHistory(userId)
    }

    override suspend fun clearFavorites(userId: String) {
        consultationDao.clearFavorites(userId)
    }
}

private fun com.hairconsultant.app.data.local.entity.HaircutEntity.toHaircutOrNull() =
    runCatching {
        com.hairconsultant.app.domain.model.Haircut(
            id = id,
            name = name,
            imageUrl = imageUrl,
            length = HairLength.valueOf(length),
            texture = HairTexture.valueOf(texture),
            recommendedFaceShapes = recommendedFaceShapes.mapNotNull { runCatching { FaceShape.valueOf(it) }.getOrNull() },
            description = description
        )
    }.getOrNull()

private fun ConsultationEntity.toDomain(haircut: com.hairconsultant.app.domain.model.Haircut?) = Consultation(
    id = id,
    userId = userId,
    source = runCatching { ConsultationSource.valueOf(source) }.getOrDefault(ConsultationSource.FACE_SCAN),
    scanResult = ScanResult(
        faceShape = runCatching { FaceShape.valueOf(faceShape) }.getOrDefault(FaceShape.OVAL),
        hairLength = runCatching { HairLength.valueOf(hairLength) }.getOrDefault(HairLength.MEDIUM),
        hairTexture = runCatching { HairTexture.valueOf(hairTexture) }.getOrDefault(HairTexture.STRAIGHT)
    ),
    selectedHaircut = haircut,
    sourceImageUrl = sourceImageUrl,
    resultImageUrl = resultImageUrl,
    isFavorite = isFavorite,
    createdAtEpochMillis = createdAtEpochMillis
)

private fun Consultation.toEntity() = ConsultationEntity(
    id = id,
    userId = userId,
    source = source.name,
    faceShape = scanResult.faceShape.name,
    hairLength = scanResult.hairLength.name,
    hairTexture = scanResult.hairTexture.name,
    selectedHaircutId = selectedHaircut?.id,
    sourceImageUrl = sourceImageUrl,
    resultImageUrl = resultImageUrl,
    isFavorite = isFavorite,
    createdAtEpochMillis = createdAtEpochMillis
)
