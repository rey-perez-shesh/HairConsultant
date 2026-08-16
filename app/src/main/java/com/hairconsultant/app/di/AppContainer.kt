package com.hairconsultant.app.di

import android.content.Context
import androidx.room.Room
import com.hairconsultant.app.data.analysis.FaceAnalyzer
import com.hairconsultant.app.data.analysis.MockFaceAnalyzer
import com.hairconsultant.app.data.local.AppDatabase
import com.hairconsultant.app.data.remote.api.NetworkModule
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.remote.firebase.ConsultationRemoteRepository
import com.hairconsultant.app.data.remote.firebase.MediaStorageRepository
import com.hairconsultant.app.data.remote.firebase.MockAuthRepository
import com.hairconsultant.app.data.remote.firebase.MockConsultationRemoteRepository
import com.hairconsultant.app.data.remote.firebase.MockMediaStorageRepository
import com.hairconsultant.app.data.remote.firebase.MockUserProfileRepository
import com.hairconsultant.app.data.remote.firebase.UserProfileRemoteRepository
import com.hairconsultant.app.data.remote.gemini.GeminiImageRepository
import com.hairconsultant.app.data.remote.gemini.GeminiImageRepositoryImpl
import com.hairconsultant.app.data.repository.ConsultationRepository
import com.hairconsultant.app.data.repository.ConsultationRepositoryImpl
import com.hairconsultant.app.data.repository.FeedbackRepository
import com.hairconsultant.app.data.repository.FeedbackRepositoryImpl
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.data.repository.HaircutRepositoryImpl
import com.hairconsultant.app.data.repository.UserRepository
import com.hairconsultant.app.data.repository.UserRepositoryImpl

/**
 * Lightweight manual service locator (no Hilt) so the dependency graph stays easy to read while
 * the backend integrations below are still being built out.
 *
 * Every repository is exposed behind an interface. Auth/Firestore/Storage default to their Mock
 * implementations, which behave like a real backend (in-memory) but need no setup. To switch to
 * the real thing:
 *  1. Add app/google-services.json from your Firebase project.
 *  2. Uncomment the "com.google.gms.google-services" plugin in app/build.gradle.kts.
 *  3. Swap the Mock* constructors below for Firebase*Repository().
 */
class AppContainer(private val appContext: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
    }

    private val apiService by lazy { NetworkModule.apiService }

    // --- Firebase-backed repositories (Mock until a Firebase project is attached) ---
    val authRepository: AuthRepository by lazy { MockAuthRepository() }
    private val userProfileRemoteRepository: UserProfileRemoteRepository by lazy { MockUserProfileRepository() }
    private val consultationRemoteRepository: ConsultationRemoteRepository by lazy { MockConsultationRemoteRepository() }
    val mediaStorageRepository: MediaStorageRepository by lazy { MockMediaStorageRepository() }

    // --- Gemini (image-upload AR try-on generation) ---
    val geminiImageRepository: GeminiImageRepository by lazy { GeminiImageRepositoryImpl(appContext) }

    // --- Face shape / hair length / hair texture detection (mocked until a real model is trained) ---
    val faceAnalyzer: FaceAnalyzer by lazy { MockFaceAnalyzer() }

    // --- Repositories consumed by the UI layer (offline-first via Room) ---
    val userRepository: UserRepository by lazy { UserRepositoryImpl(database.userDao(), userProfileRemoteRepository) }
    val haircutRepository: HaircutRepository by lazy { HaircutRepositoryImpl(database.haircutDao(), apiService) }
    val consultationRepository: ConsultationRepository by lazy {
        ConsultationRepositoryImpl(database.consultationDao(), database.haircutDao(), consultationRemoteRepository)
    }
    val feedbackRepository: FeedbackRepository by lazy { FeedbackRepositoryImpl(database.feedbackDao(), apiService) }
}
