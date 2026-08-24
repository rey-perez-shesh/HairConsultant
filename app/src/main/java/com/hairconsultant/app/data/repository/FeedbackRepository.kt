package com.hairconsultant.app.data.repository

import com.hairconsultant.app.data.local.dao.FeedbackDao
import com.hairconsultant.app.data.local.entity.FeedbackEntity
import com.hairconsultant.app.data.remote.firebase.FeedbackRemoteRepository
import com.hairconsultant.app.domain.model.FeedbackReport
import java.util.UUID

interface FeedbackRepository {
    suspend fun submit(userId: String, rating: Int, comment: String)
    suspend fun syncPending()
}

/** Offline-first: every rating/review is queued in Room first, then pushed to Firestore. */
class FeedbackRepositoryImpl(
    private val feedbackDao: FeedbackDao,
    private val remote: FeedbackRemoteRepository
) : FeedbackRepository {

    override suspend fun submit(userId: String, rating: Int, comment: String) {
        val report = FeedbackReport(
            id = UUID.randomUUID().toString(),
            userId = userId,
            rating = rating,
            comment = comment,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        feedbackDao.insert(
            FeedbackEntity(
                id = report.id,
                userId = report.userId,
                rating = report.rating,
                comment = report.comment,
                isSynced = false,
                createdAtEpochMillis = report.createdAtEpochMillis
            )
        )
        syncPending()
    }

    override suspend fun syncPending() {
        feedbackDao.getUnsynced().forEach { pending ->
            val synced = runCatching {
                remote.submit(
                    FeedbackReport(
                        id = pending.id,
                        userId = pending.userId,
                        rating = pending.rating,
                        comment = pending.comment,
                        createdAtEpochMillis = pending.createdAtEpochMillis
                    )
                )
            }.isSuccess
            if (synced) feedbackDao.markSynced(pending.id)
        }
    }
}
