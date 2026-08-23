package com.hairconsultant.app.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.database.FirebaseDatabase
import com.hairconsultant.app.data.analysis.FaceAnalyzer
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.data.analysis.LandmarkFaceAnalyzer
import com.hairconsultant.app.data.analysis.MlKitFaceMeshVerifier
import com.hairconsultant.app.data.analysis.StillImageFaceLandmarker
import com.hairconsultant.app.data.analysis.StillImageHairSegmenter
import com.hairconsultant.app.data.local.AppDatabase
import com.hairconsultant.app.data.remote.api.NetworkModule
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.remote.firebase.ConsultationRemoteRepository
import com.hairconsultant.app.data.remote.firebase.FirebaseAuthRepository
import com.hairconsultant.app.data.remote.firebase.FirebaseMediaStorageRepository
import com.hairconsultant.app.data.remote.firebase.FirestoreConsultationRepository
import com.hairconsultant.app.data.remote.firebase.FirestoreUserProfileRepository
import com.hairconsultant.app.data.remote.firebase.MediaStorageRepository
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
 * Lightweight manual service locator (no Hilt) so the dependency graph stays easy to read.
 *
 * Firebase project: app/google-services.json is attached and the Google Services plugin is
 * applied, so every repository below talks to the real backend:
 *  - Auth -> [FirebaseAuthRepository] (login/register).
 *  - Firestore -> [FirestoreUserProfileRepository] (profile + preferences) and
 *    [FirestoreConsultationRepository] (consultation history + favorites).
 *  - Storage -> [FirebaseMediaStorageRepository] (consultation photos + try-on results).
 *  - Realtime Database -> connected via [realtimeDatabase] only; no data model lives there yet.
 */
class AppContainer(private val appContext: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    private val apiService by lazy { NetworkModule.apiService }

    // --- Firebase-backed repositories ---
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository() }
    private val userProfileRemoteRepository: UserProfileRemoteRepository by lazy { FirestoreUserProfileRepository() }
    private val consultationRemoteRepository: ConsultationRemoteRepository by lazy { FirestoreConsultationRepository() }
    val mediaStorageRepository: MediaStorageRepository by lazy { FirebaseMediaStorageRepository() }

    /**
     * Realtime Database is connected to the Firebase project but intentionally unused for now —
     * Firestore is the source of truth for profiles/consultations/favorites. Enable Realtime
     * Database for this project in the Firebase console (which adds a `databaseURL` to
     * google-services.json) before touching this, or FirebaseDatabase.getInstance() will throw.
     */
    val realtimeDatabase: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    // --- Gemini (image-upload AR try-on generation) ---
    val geminiImageRepository: GeminiImageRepository by lazy { GeminiImageRepositoryImpl(appContext) }

    // --- Face + hair: MediaPipe live landmarker/hair mask + ML Kit second check ---
    // Swap LandmarkFaceAnalyzer(...) for MockFaceAnalyzer() to restore random results.
    val faceLandmarkStore = FaceLandmarkStore()
    private val stillFaceLandmarker by lazy { StillImageFaceLandmarker(appContext) }
    private val mlKitFaceMeshVerifier by lazy { MlKitFaceMeshVerifier(appContext) }
    private val stillHairSegmenter by lazy { StillImageHairSegmenter(appContext) }
    val faceAnalyzer: FaceAnalyzer by lazy {
        LandmarkFaceAnalyzer(
            appContext,
            faceLandmarkStore,
            stillFaceLandmarker,
            mlKitFaceMeshVerifier,
            stillHairSegmenter
        )
    }

    // --- Repositories consumed by the UI layer (offline-first via Room) ---
    val userRepository: UserRepository by lazy { UserRepositoryImpl(database.userDao(), userProfileRemoteRepository) }
    val haircutRepository: HaircutRepository by lazy { HaircutRepositoryImpl(database.haircutDao(), apiService) }
    val consultationRepository: ConsultationRepository by lazy {
        ConsultationRepositoryImpl(database.consultationDao(), database.haircutDao(), consultationRemoteRepository)
    }
    val feedbackRepository: FeedbackRepository by lazy { FeedbackRepositoryImpl(database.feedbackDao(), apiService) }
}
