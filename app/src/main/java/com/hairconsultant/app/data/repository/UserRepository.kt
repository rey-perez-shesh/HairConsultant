package com.hairconsultant.app.data.repository

import com.hairconsultant.app.data.local.dao.UserDao
import com.hairconsultant.app.data.local.entity.UserEntity
import com.hairconsultant.app.data.remote.firebase.UserProfileRemoteRepository
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.TreatmentPreference
import com.hairconsultant.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserRepository {
    fun observe(userId: String): Flow<User?>
    suspend fun refreshFromRemote(userId: String)
    suspend fun save(user: User)
}

class UserRepositoryImpl(
    private val userDao: UserDao,
    private val remote: UserProfileRemoteRepository
) : UserRepository {

    override fun observe(userId: String): Flow<User?> = userDao.observe(userId).map { it?.toDomain() }

    override suspend fun refreshFromRemote(userId: String) {
        remote.fetch(userId)?.let { userDao.upsert(it.toEntity()) }
    }

    override suspend fun save(user: User) {
        userDao.upsert(user.toEntity())
        runCatching { remote.save(user) }
    }
}

private fun UserEntity.toDomain() = User(
    id = id,
    email = email,
    username = username,
    birthdayEpochDay = birthdayEpochDay,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.PREFER_NOT_TO_SAY),
    photoUrl = photoUrl,
    createdAtEpochMillis = createdAtEpochMillis,
    preferredHairLength = preferredHairLength?.let { runCatching { HairLength.valueOf(it) }.getOrNull() },
    preferredHairTexture = preferredHairTexture?.let { runCatching { HairTexture.valueOf(it) }.getOrNull() },
    preferredTreatment = preferredTreatment?.let { runCatching { TreatmentPreference.valueOf(it) }.getOrNull() }
)

private fun User.toEntity() = UserEntity(
    id = id,
    email = email,
    username = username,
    birthdayEpochDay = birthdayEpochDay,
    gender = gender.name,
    photoUrl = photoUrl,
    createdAtEpochMillis = createdAtEpochMillis,
    preferredHairLength = preferredHairLength?.name,
    preferredHairTexture = preferredHairTexture?.name,
    preferredTreatment = preferredTreatment?.name
)
