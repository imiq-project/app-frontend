package com.example.imiq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Talks to the DYCONET HOTCO-CT v4.3 passport service selected at build time. */
object PassportApiService {
    // POST /api/dyconet -> HOTCO-CT v4.3 Cognitive Passport v2.
    // The server validates the complete hotco_ct_input_2.1 questionnaire,
    // including explicit user-declared availability, and never fills missing input.
    // localEmulator: http://10.0.2.2:8077
    // localUsb:      http://127.0.0.1:8077 after `adb reverse tcp:8077 tcp:8077`
    // production:    https://imiq-app.et.uni-magdeburg.de
    private val baseUrl: String = BuildConfig.DYCONET_BASE_URL.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * POST one strict hotco_ct_input_2.1 questionnaire response.
     * Returns the cognitive passport JSON as a String.
     */
    suspend fun generatePassport(surveyJson: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/dyconet")
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
