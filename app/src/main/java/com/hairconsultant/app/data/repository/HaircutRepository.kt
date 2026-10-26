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
        // Firestore (edited from the admin dashboard) is the source of truth; Room is the offline
        // cache the UI observes. A fresh install gets the bundled SampleData first so Home never
        // waits on the network, then the Firestore catalog replaces it once it arrives. If the
        // fetch fails or comes back empty (offline, empty cache) the cached catalog stays as-is.
        if (haircutDao.count() == 0) {
            haircutDao.replaceAll(SampleData.allHaircuts.map { it.toEntity() })
        }
        val remoteCatalog = runCatching { remote.fetchActive() }
            .onFailure { Log.w(TAG, "Couldn't fetch the catalog from Firestore", it) }
            .getOrNull()
            ?: return
        haircutDao.replaceAll(remoteCatalog.map { it.toEntity() })
    }

    private companion object {
        const val TAG = "HaircutRepository"
    }
}
