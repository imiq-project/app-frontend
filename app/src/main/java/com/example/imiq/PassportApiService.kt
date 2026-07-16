package com.example.imiq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to the DYCONET passport service deployed on the imiq-app server.
 * Replaces the old Spark-over-Tailscale interface — no Tailscale needed anymore.
 */
object PassportApiService {
    // DYCONET deployed on the imiq-app server (verified live 2026-07-16):
    // POST /api/dyconet -> baseline_1.0 passport in ~0.3 s. HTTPS, so it works
    // from any phone on any network — no adb reverse / laptop rig needed.
    // Local fallback for offline demos: http://127.0.0.1:8077 with
    // `adb reverse tcp:8077 tcp:8077` (start_dyconet.bat).
    private const val BASE_URL = "https://imiq-app.et.uni-magdeburg.de"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * POST one LimeSurvey-shape survey response.
     * Returns the cognitive passport JSON as a String.
     */
    suspend fun generatePassport(surveyJson: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/dyconet")
            .post(surveyJson.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("Server error ${response.code}: $body")
            }
            body
        }
    }

}