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
import kotlinx.coroutines.withTimeout

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

    /**
     * Brings this device and Firestore back in line: downloads the Firestore profile when there
     * is one, or, when Firestore has none but this device does (a registration whose Firestore
     * write failed), uploads the local copy so the account is repaired. Throws if Firestore can't
     * be reached, so callers decide whether that matters.
     */
    suspend fun refreshFromRemote(userId: String)

    /**
     * Saves locally, then to Firestore. The local save always happens; the result reports
     * whether the Firestore write succeeded, so callers can tell the user instead of the
     * profile silently existing on this device only.
     */
    suspend fun save(user: User): Result<Unit>
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
        val remoteUser = remote.fetch(userId)
        if (remoteUser != null) {
            userDao.upsert(remoteUser.toEntity())
            return
        }
        val localUser = userDao.get(userId) ?: return
        Log.w(TAG, "Profile $userId is missing from Firestore but exists on this device; uploading it")
        remote.save(localUser.toDomain())
        Log.i(TAG, "Repaired missing Firestore profile $userId")
    }

    private suspend fun tryRefreshFromRemote(userId: String) {
        runCatching { refreshFromRemote(userId) }
            .onFailure { Log.w(TAG, "Couldn't sync the profile with Firestore; using the local copy", it) }
    }

    override suspend fun save(user: User): Result<Unit> {
        userDao.upsert(user.toEntity())
        // Offline, a Firestore write only completes once the device reconnects; bound the wait
        // so registration reports the failure instead of spinning forever.
        return runCatching { withTimeout(REMOTE_SAVE_TIMEOUT_MILLIS) { remote.save(user) } }
            .onFailure { Log.e(TAG, "Couldn't save profile ${user.id} to Firestore; it's only on this device", it) }
    }

    private companion object {
        const val TAG = "UserRepository"
        const val REMOTE_SAVE_TIMEOUT_MILLIS = 15_000L
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
