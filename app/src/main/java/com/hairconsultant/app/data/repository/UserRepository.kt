package com.hairconsultant.app.data.repository

import android.util.Log
import com.hairconsultant.app.data.local.dao.UserDao
import com.hairconsultant.app.data.local.entity.UserEntity
import com.hairconsultant.app.data.remote.firebase.UserProfileRemoteRepository
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.TreatmentPreference
import com.hairconsultant.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface UserRepository {
    /**
     * The locally stored profile, kept current: collecting it also re-downloads the profile from
     * Firestore in the background, so a login on a new device (or edits made on another one)
     * shows up without waiting on anything.
     */
    fun observe(userId: String): Flow<User?>

    /**
     * The profile right now, downloading it from Firestore first when this device has no copy
     * yet — e.g. just after logging in on a device the account wasn't registered on. Use this
     * instead of `observe(userId).first()`, which returns null in exactly that case. Null only
     * when the profile exists nowhere or can't be fetched (offline, Firestore rules).
     */
    suspend fun get(userId: String): User?
    suspend fun refreshFromRemote(userId: String)
    suspend fun save(user: User)
}

class UserRepositoryImpl(
    private val userDao: UserDao,
    private val remote: UserProfileRemoteRepository
) : UserRepository {

    override fun observe(userId: String): Flow<User?> = channelFlow {
        launch { tryRefreshFromRemote(userId) }
        userDao.observe(userId).map { it?.toDomain() }.collect { send(it) }
    }

    override suspend fun get(userId: String): User? {
        userDao.observe(userId).first()?.let { return it.toDomain() }
        tryRefreshFromRemote(userId)
        return userDao.observe(userId).first()?.toDomain()
    }

    override suspend fun refreshFromRemote(userId: String) {
        remote.fetch(userId)?.let { userDao.upsert(it.toEntity()) }
    }

    private suspend fun tryRefreshFromRemote(userId: String) {
        runCatching { refreshFromRemote(userId) }
            .onFailure { Log.w(TAG, "Couldn't download the profile from Firestore; using the local copy", it) }
    }

    override suspend fun save(user: User) {
        userDao.upsert(user.toEntity())
        runCatching { remote.save(user) }
    }

    private companion object {
        const val TAG = "UserRepository"
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
