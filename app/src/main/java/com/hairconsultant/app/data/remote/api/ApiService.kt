package com.hairconsultant.app.data.remote.api

import com.hairconsultant.app.data.remote.api.dto.AnalyticsEventDto
import com.hairconsultant.app.data.remote.api.dto.FeedbackDto
import com.hairconsultant.app.data.remote.api.dto.HaircutDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Backend API contract: the Android app never talks to PostgreSQL directly, it goes through this
 * service so credentials/queries stay server-side. Point [com.hairconsultant.app.BuildConfig.BACKEND_BASE_URL]
 * at the real backend once it exists; until then callers fall back to local sample/cached data.
 */
interface ApiService {
    @GET("api/haircuts")
    suspend fun getHaircuts(): List<HaircutDto>

    @POST("api/feedback")
    suspend fun submitFeedback(@Body feedback: FeedbackDto)

    @POST("api/analytics/events")
    suspend fun logEvent(@Body event: AnalyticsEventDto)
}
