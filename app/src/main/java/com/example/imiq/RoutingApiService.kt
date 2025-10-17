package com.example.imiq

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// Data classes for API request and response
data class RouteRequest(
    val start: List<Double>,
    val destination: List<Double>,
    val profile: Map<String, String> = emptyMap(),
    val token: String = "ai39efn092344nlasdfd"
)

data class RouteResponse(
    val points: List<List<Double>>
)

// Retrofit API interface
interface RoutingApi {
    @POST("api/dayplanner/route")
    suspend fun getRoute(@Body request: RouteRequest): RouteResponse
}

// Singleton object to create API instance
object RoutingApiService {
    private const val BASE_URL = "https://imiq-public.et.uni-magdeburg.de/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: RoutingApi = retrofit.create(RoutingApi::class.java)
}