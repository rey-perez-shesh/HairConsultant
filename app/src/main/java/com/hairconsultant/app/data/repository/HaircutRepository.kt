package com.hairconsultant.app.data.repository

import android.util.Log
import com.hairconsultant.app.data.SampleData
import com.hairconsultant.app.data.local.dao.HaircutDao
import com.hairconsultant.app.data.local.entity.toDomain
import com.hairconsultant.app.data.local.entity.toEntity
import com.hairconsultant.app.data.remote.firebase.HaircutRemoteRepository
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutCluster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface HaircutRepository {
    /** All haircuts grouped into the Home screen's length x texture clusters. */
    fun observeClusters(): Flow<List<HaircutCluster>>
    fun observeMatching(faceShape: FaceShape, length: HairLength, texture: HairTexture): Flow<List<Haircut>>
    /** Gives a fresh install the bundled catalog, so Home never waits on the network. */
    suspend fun seedIfEmpty()
    /** Keeps the local catalog matching Firestore until cancelled, or until the listener fails. */
    suspend fun syncWithRemote()
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

    override suspend fun seedIfEmpty() {
        if (haircutDao.count() == 0) {
            haircutDao.replaceAll(SampleData.allHaircuts.map { it.toEntity() })
        }
    }

    override suspend fun syncWithRemote() {
        // Firestore (edited from the admin dashboard) is the source of truth; Room is the offline
        // cache the UI observes, so every dashboard change reaches the screens through it. If the
        // listener fails or has nothing to offer (offline, empty cache) the cached catalog stays as-is.
        remote.observeActive()
            .catch { Log.w(TAG, "Couldn't sync the catalog from Firestore", it) }
            .collect { catalog -> haircutDao.replaceAll(catalog.map { it.toEntity() }) }
    }

    private companion object {
        const val TAG = "HaircutRepository"
    }
}
