package com.hairconsultant.app.data.repository

import android.util.Log
import com.hairconsultant.app.data.local.dao.ConsultationDao
import com.hairconsultant.app.data.local.dao.HaircutDao
import com.hairconsultant.app.data.local.entity.ConsultationEntity
import com.hairconsultant.app.data.local.entity.toDomain
import com.hairconsultant.app.data.remote.firebase.ConsultationRemoteRepository
import com.hairconsultant.app.data.remote.firebase.RemoteSavedLook
import com.hairconsultant.app.domain.model.AR_FILTER_ID_PREFIX
import com.hairconsultant.app.domain.model.arFilterFavoriteId
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.SkinTone
import com.hairconsultant.app.domain.model.SkinUndertone
import com.hairconsultant.app.domain.model.confidentSkinTone
import com.hairconsultant.app.domain.model.confidentUndertone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface ConsultationRepository {
    fun observeHistory(userId: String): Flow<List<Consultation>>
    fun observeFavorites(userId: String): Flow<List<Consultation>>
    suspend fun save(consultation: Consultation)
    suspend fun setFavorite(consultationId: String, isFavorite: Boolean)

    /** Deletes one saved look ([Consultation.isSavedLook]); un-favoriting it would leave a row no screen shows. */
    suspend fun removeSavedLook(lookId: String)

    /** Favorites an AR try-on filter ([arFilterFavoriteId]); un-favorite it with [removeSavedLook]. */
    suspend fun saveArFilter(userId: String, haircutId: String)

    /**
     * Copies the account's saved looks from Firestore into this phone's Favorites, e.g. after a
     * reinstall, and backs up this phone's looks if the account has none stored yet.
     */
    suspend fun restoreSavedLooks(userId: String)
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
        if (consultation.isSavedLook) {
            // A look whose picture never reached Storage only has a file on this phone, which
            // another device couldn't show, so it stays local. Its own collection, not
            // "consultations": there each look would count as an extra scan in admin analytics.
            if (!consultation.resultImageUrl.isRemote()) return
            runCatching { remote.saveLook(RemoteSavedLook(consultation, consultation.selectedHaircut?.id)) }
                .onFailure { Log.w(TAG, "Couldn't save look ${consultation.id} to Firestore", it) }
            return
        }
        runCatching { remote.save(consultation) }
            .onFailure { Log.w(TAG, "Couldn't save consultation ${consultation.id} to Firestore", it) }
    }

    override suspend fun removeSavedLook(lookId: String) {
        consultationDao.delete(lookId)
        runCatching { remote.deleteLook(lookId) }
            .onFailure { Log.w(TAG, "Couldn't delete look $lookId from Firestore", it) }
    }

    override suspend fun saveArFilter(userId: String, haircutId: String) {
        // The scan fields are placeholders the row can't go without; a filter never shows them.
        val entity = ConsultationEntity(
            id = arFilterFavoriteId(userId, haircutId),
            userId = userId,
            source = ConsultationSource.FACE_SCAN.name,
            faceShape = FaceShape.OVAL.name,
            hairLength = HairLength.MEDIUM.name,
            hairTexture = HairTexture.STRAIGHT.name,
            selectedHaircutId = haircutId,
            sourceImageUrl = null,
            resultImageUrl = null,
            isFavorite = true,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        consultationDao.upsert(entity)
        runCatching { remote.saveLook(RemoteSavedLook(entity.toDomain(haircut = null), haircutId)) }
            .onFailure { Log.w(TAG, "Couldn't save AR filter ${entity.id} to Firestore", it) }
    }

    /**
     * shortcut: adds what Firestore has and never removes, so a look deleted on one phone stays
     * on another until it's removed there too; make Firestore authoritative if that matters.
     */
    override suspend fun restoreSavedLooks(userId: String) {
        val looks = runCatching { remote.fetchLooks(userId) }
            .onFailure { Log.w(TAG, "Couldn't restore saved looks from Firestore", it) }
            .getOrNull() ?: return
        looks.forEach { consultationDao.upsert(it.look.toEntity().copy(selectedHaircutId = it.haircutId)) }
        // Nothing backed up yet but looks on the phone: they were saved before the account had
        // a backup (an older build, or before the savedLooks rules were deployed), so send them up.
        if (looks.isNotEmpty()) return
        // AR filters have no picture to wait for, so they always qualify.
        consultationDao.savedLooks(userId).filter {
            it.resultImageUrl.isRemote() || it.id.startsWith(AR_FILTER_ID_PREFIX)
        }.forEach { entity ->
            runCatching { remote.saveLook(RemoteSavedLook(entity.toDomain(haircut = null), entity.selectedHaircutId)) }
                .onFailure { Log.w(TAG, "Couldn't back up look ${entity.id} to Firestore", it) }
        }
    }

    override suspend fun setFavorite(consultationId: String, isFavorite: Boolean) {
        consultationDao.setFavorite(consultationId, isFavorite)
        runCatching { remote.setFavorite(consultationId, isFavorite) }
            .onFailure { Log.w(TAG, "Couldn't sync favorite for $consultationId to Firestore", it) }
    }

    override suspend fun clearHistory(userId: String) {
        consultationDao.clearHistory(userId)
    }

    override suspend fun clearFavorites(userId: String) {
        consultationDao.clearFavorites(userId)
        // Otherwise the next restoreSavedLooks would bring the deleted looks straight back.
        runCatching { remote.deleteLooks(userId) }
            .onFailure { Log.w(TAG, "Couldn't delete saved looks from Firestore", it) }
    }

    private companion object {
        const val TAG = "ConsultationRepository"
    }
}

private fun com.hairconsultant.app.data.local.entity.HaircutEntity.toHaircutOrNull() = toDomain()

/** A Storage link another device can load, as opposed to a file or picker URI on this phone. */
private fun String?.isRemote() = this?.startsWith("http") == true

private fun ConsultationEntity.toDomain(haircut: com.hairconsultant.app.domain.model.Haircut?) = Consultation(
    id = id,
    userId = userId,
    source = runCatching { ConsultationSource.valueOf(source) }.getOrDefault(ConsultationSource.FACE_SCAN),
    scanResult = ScanResult(
        faceShape = runCatching { FaceShape.valueOf(faceShape) }.getOrDefault(FaceShape.OVAL),
        hairLength = runCatching { HairLength.valueOf(hairLength) }.getOrDefault(HairLength.MEDIUM),
        hairTexture = runCatching { HairTexture.valueOf(hairTexture) }.getOrDefault(HairTexture.STRAIGHT),
        // Rows only keep readings the scan was sure of (see toEntity), so a stored one counts as sure.
        skinTone = skinTone?.let { runCatching { SkinTone.valueOf(it) }.getOrNull() },
        skinUndertone = skinUndertone?.let { runCatching { SkinUndertone.valueOf(it) }.getOrNull() },
        skinToneConfidence = if (skinTone != null) 1f else 0f,
        skinUndertoneConfidence = if (skinUndertone != null) 1f else 0f
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
    createdAtEpochMillis = createdAtEpochMillis,
    skinTone = scanResult.confidentSkinTone?.name,
    skinUndertone = scanResult.confidentUndertone?.name
)
