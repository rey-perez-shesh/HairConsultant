package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.hairconsultant.app.domain.model.FeedbackReport
import kotlinx.coroutines.tasks.await

/** Firestore-backed sync of star ratings + written reviews submitted from the Profile screen. */
interface FeedbackRemoteRepository {
    suspend fun submit(report: FeedbackReport)
}

class FirestoreFeedbackRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : FeedbackRemoteRepository {

    private val collection get() = firestore.collection("feedback")

    override suspend fun submit(report: FeedbackReport) {
        collection.document(report.id).set(
            mapOf(
                "userId" to report.userId,
                "rating" to report.rating,
                "comment" to report.comment,
                "createdAtEpochMillis" to report.createdAtEpochMillis
            )
        ).await()
    }
}

/** In-memory stand-in used until a Firebase project is attached. */
class MockFeedbackRemoteRepository : FeedbackRemoteRepository {
    private val store = mutableMapOf<String, FeedbackReport>()

    override suspend fun submit(report: FeedbackReport) {
        store[report.id] = report
    }
}
