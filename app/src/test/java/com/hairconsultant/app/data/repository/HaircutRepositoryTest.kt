package com.hairconsultant.app.data.repository

import com.hairconsultant.app.data.SampleData
import com.hairconsultant.app.data.local.dao.HaircutDao
import com.hairconsultant.app.data.local.entity.HaircutEntity
import com.hairconsultant.app.data.remote.firebase.HaircutRemoteRepository
import com.hairconsultant.app.domain.model.ArType
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dashboard-managed catalog: Firestore wins when it answers, the local catalog stays when it doesn't. */
class HaircutRepositoryTest {

    private val dashboardStyle = Haircut(
        id = "NEW_STYLE",
        name = "Dashboard Style",
        imageUrl = "https://example.com/thumb.png",
        length = HairLength.SHORT,
        texture = HairTexture.WAVY,
        recommendedFaceShapes = listOf(FaceShape.OVAL),
        arType = ArType.DEEPAR,
        arAssetUrl = "https://example.com/effect.deepar",
        arAssetVersion = 3
    )

    @Test
    fun freshInstallOfflineShowsBundledCatalog() = runBlocking {
        val dao = FakeHaircutDao()
        HaircutRepositoryImpl(dao, FakeRemote(result = null)).refresh()
        assertEquals(SampleData.allHaircuts.size, dao.rows.value.size)
    }

    @Test
    fun firestoreCatalogReplacesLocalOneWithAllDashboardFields() = runBlocking {
        val dao = FakeHaircutDao()
        HaircutRepositoryImpl(dao, FakeRemote(result = listOf(dashboardStyle))).refresh()
        val clusters = HaircutRepositoryImpl(dao, FakeRemote(result = null)).observeClusters().first()
        assertEquals(listOf(dashboardStyle), clusters.flatMap { it.haircuts })
    }

    @Test
    fun failedFetchKeepsTheCachedCatalog() = runBlocking {
        val dao = FakeHaircutDao()
        HaircutRepositoryImpl(dao, FakeRemote(result = listOf(dashboardStyle))).refresh()
        HaircutRepositoryImpl(dao, FakeRemote(error = IllegalStateException("UNAVAILABLE"))).refresh()
        assertEquals(listOf("NEW_STYLE"), dao.rows.value.map { it.id })
    }

    private class FakeRemote(
        private val result: List<Haircut>? = null,
        private val error: Throwable? = null
    ) : HaircutRemoteRepository {
        override suspend fun fetchActive(): List<Haircut>? {
            error?.let { throw it }
            return result
        }
    }

    private class FakeHaircutDao : HaircutDao {
        val rows = MutableStateFlow<List<HaircutEntity>>(emptyList())

        override suspend fun insertAll(haircuts: List<HaircutEntity>) {
            rows.value = rows.value.filterNot { row -> haircuts.any { it.id == row.id } } + haircuts
        }

        override fun observeAll(): Flow<List<HaircutEntity>> = rows

        override fun observeByCluster(length: String, texture: String): Flow<List<HaircutEntity>> =
            rows.map { all -> all.filter { it.length == length && it.texture == texture } }

        override suspend fun count(): Int = rows.value.size

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }
}
