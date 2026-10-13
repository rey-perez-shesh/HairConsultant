package com.hairconsultant.app.data.repository

import com.hairconsultant.app.data.local.dao.UserDao
import com.hairconsultant.app.data.local.entity.UserEntity
import com.hairconsultant.app.data.remote.firebase.UserProfileRemoteRepository
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The profile self-repair: a registration whose Firestore write failed gets uploaded on the next sync. */
class UserRepositoryTest {

    private val user = User(id = "uid-1", email = "t@example.com", username = "t", birthdayEpochDay = 1L, gender = Gender.MALE)

    @Test
    fun uploadsLocalProfileWhenFirestoreHasNone() = runBlocking {
        val dao = FakeUserDao()
        val remote = FakeRemote()
        val repository = UserRepositoryImpl(dao, remote)
        remote.failSaves = true
        repository.save(user)
        assertNull(remote.stored[user.id])

        remote.failSaves = false
        repository.refreshFromRemote(user.id)

        assertEquals(user, remote.stored[user.id])
    }

    @Test
    fun firestoreProfileWinsWhenItExists() = runBlocking {
        val dao = FakeUserDao()
        val remote = FakeRemote()
        val repository = UserRepositoryImpl(dao, remote)
        repository.save(user)
        val edited = user.copy(gender = Gender.FEMALE)
        remote.stored[user.id] = edited
        remote.saveCount = 0

        repository.refreshFromRemote(user.id)

        assertEquals(edited, repository.get(user.id))
        assertEquals(0, remote.saveCount)
    }

    @Test
    fun saveReportsFirestoreFailureButKeepsLocalCopy() = runBlocking {
        val dao = FakeUserDao()
        val remote = FakeRemote().apply { failSaves = true }
        val repository = UserRepositoryImpl(dao, remote)

        val result = repository.save(user)

        assertTrue(result.isFailure)
        assertEquals(user, repository.get(user.id))
    }

    @Test
    fun nothingToRepairWhenProfileExistsNowhere() = runBlocking {
        val remote = FakeRemote()
        UserRepositoryImpl(FakeUserDao(), remote).refreshFromRemote(user.id)
        assertEquals(0, remote.saveCount)
    }

    private class FakeRemote : UserProfileRemoteRepository {
        val stored = mutableMapOf<String, User>()
        var failSaves = false
        var saveCount = 0

        override suspend fun fetch(userId: String): User? = stored[userId]

        override suspend fun save(user: User) {
            saveCount++
            if (failSaves) error("PERMISSION_DENIED")
            stored[user.id] = user
        }
    }

    private class FakeUserDao : UserDao {
        private val rows = MutableStateFlow<Map<String, UserEntity>>(emptyMap())

        override suspend fun upsert(user: UserEntity) {
            rows.value = rows.value + (user.id to user)
        }

        override fun observe(userId: String): Flow<UserEntity?> = rows.map { it[userId] }

        override suspend fun get(userId: String): UserEntity? = rows.value[userId]

        override suspend fun delete(userId: String) {
            rows.value = rows.value - userId
        }
    }
}
