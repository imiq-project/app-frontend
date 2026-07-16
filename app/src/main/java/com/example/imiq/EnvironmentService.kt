package com.example.imiq

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Live local conditions from FIWARE Orion (NGSIv2), read directly from the app.
//   GET {base}/entities?type=Weather&options=keyValues   (header x-api-key)
// Verified 2026-06-05 attrs: Weather temperature/feelsLikeTemperature/rain/
// rainHourly/windSpeed/windGust/uvIndex/lightIntensity (darkness signal);
// Parking freeSpots/totalSpots/name; Traffic avgSpeed(nullable)/speedLimit/
// location; AirQuality no2/pm10/pm25. Best-effort: any failure -> null field.
// ---------------------------------------------------------------------------

data class EnvSummary(
    val weather: String? = null,
    val parking: String? = null,
    val traffic: String? = null,
    val airQuality: String? = null,
    val daylight: String? = null,
) {
    fun asPromptLines(): String = listOfNotNull(
        weather?.let { "Weather: $it" },
        daylight?.let { "Daylight: $it" },
        airQuality?.let { "Air quality: $it" },
        traffic?.let { "Traffic near route: $it" },
        parking?.let { "Parking near destination: $it" },
    ).joinToString("\n").ifBlank { "No live conditions available." }
}

private interface OrionApi {
    @GET("entities")
    suspend fun entities(
        @Header("x-api-key") apiKey: String,
        @Query("type") type: String,
        @Query("options") options: String = "keyValues",
        @Query("limit") limit: Int = 50
    ): List<Map<String, Any?>>
}

object EnvironmentService {
    private const val BASE_URL = "https://imiq-public.et.uni-magdeburg.de/api/orion/"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    private val api: OrionApi by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(OrionApi::class.java)
    }

    /** Best-effort live conditions near the destination. Never throws. */
    suspend fun fetch(destLat: Double, destLon: Double): EnvSummary {
        val key = BuildConfig.FIWARE_API_KEY
        if (key.isBlank()) return EnvSummary()
        val w = runCatching { api.entities(key, "Weather", limit = 1).firstOrNull() }.getOrNull()
        return EnvSummary(
            weather = w?.let { weatherText(it) },
            daylight = w?.let { daylightText(it) },
            parking = runCatching { parkingText(api.entities(key, "Parking", limit = 30), destLat, destLon) }.getOrNull(),
            traffic = runCatching { trafficText(api.entities(key, "Traffic", limit = 80), destLat, destLon) }.getOrNull(),
            airQuality = runCatching { airText(api.entities(key, "AirQuality", limit = 20), destLat, destLon) }.getOrNull(),
        )
    }

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null
    }

    private fun loc(v: Any?): Pair<Double, Double>? {
        val p = (v as? String)?.split(",") ?: return null
        if (p.size != 2) return null
        val la = p[0].trim().toDoubleOrNull() ?: return null
        val lo = p[1].trim().toDoubleOrNull() ?: return null
        return la to lo
    }

    private fun haversineM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    private fun nearest(list: List<Map<String, Any?>>, lat: Double, lon: Double): Map<String, Any?>? =
        list.mapNotNull { e -> loc(e["location"])?.let { e to haversineM(lat, lon, it.first, it.second) } }
            .minByOrNull { it.second }?.first

    private fun weatherText(w: Map<String, Any?>): String? {
        val t = num(w["temperature"]); val feels = num(w["feelsLikeTemperature"])
        val rainH = num(w["rainHourly"]) ?: num(w["rain"]); val wind = num(w["windSpeed"])
        val gust = num(w["windGust"]); val uv = num(w["uvIndex"])
        val parts = mutableListOf<String>()
        if (t != null) parts.add("${t.roundToInt()}°C" + (if (feels != null && abs(feels - t) >= 1.5) " (feels ${feels.roundToInt()}°C)" else ""))
        if (rainH != null) parts.add(if (rainH > 0.0) "raining (~$rainH mm/h)" else "dry")
        if (wind != null) {
            val g = if (gust != null && gust >= wind + 3) " gusting ${gust.roundToInt()}" else ""
            parts.add("wind ${wind.roundToInt()} m/s$g")
        }
        if (uv != null && uv >= 3) parts.add("UV ${uv.roundToInt()}")
        return parts.joinToString(", ").ifBlank { null }
    }

    /** lightIntensity (Bresser station, ~0 at night) → human darkness cue. */
    private fun daylightText(w: Map<String, Any?>): String? {
        val li = num(w["lightIntensity"]) ?: return null
        return when {
            li < 5 -> "after dark — it is night, paths may be unlit"
            li < 400 -> "low light / dusk"
            else -> "broad daylight"
        }
    }

    private fun parkingText(list: List<Map<String, Any?>>, lat: Double, lon: Double): String? {
        val p = nearest(list, lat, lon) ?: return null
        val free = num(p["freeSpots"])?.roundToInt() ?: return null
        val total = num(p["totalSpots"])?.roundToInt()
        val name = (p["name"] as? String)?.takeIf { it.isNotBlank() } ?: "nearest lot"
        val tight = if (total != null && total > 0 && free.toDouble() / total < 0.1) " — almost full" else ""
        return "$name has $free" + (total?.let { "/$it" } ?: "") + " free spots$tight"
    }

    private fun trafficText(list: List<Map<String, Any?>>, lat: Double, lon: Double): String? {
        val nearby = list.mapNotNull { e ->
            val avg = num(e["avgSpeed"]) ?: return@mapNotNull null
            val pl = loc(e["location"]) ?: return@mapNotNull null
            if (haversineM(lat, lon, pl.first, pl.second) > 1200) return@mapNotNull null
            val lim = num(e["speedLimit"])
            avg to (if (lim != null && lim > 0) avg / lim else 1.0)
        }
        if (nearby.isEmpty()) return "no live slowdowns reported nearby"
        val worst = nearby.minByOrNull { it.second }!!
        return if (worst.second < 0.6) "slow traffic nearby (~${worst.first.roundToInt()} km/h)" else "flowing normally nearby"
    }

    private fun airText(list: List<Map<String, Any?>>, lat: Double, lon: Double): String? {
        val a = nearest(list, lat, lon) ?: return null
        val pm25 = num(a["pm25"]); val pm10 = num(a["pm10"]); val no2 = num(a["no2"])
        val label = when {
            pm25 == null -> null
            pm25 < 12 -> "good"
            pm25 < 35 -> "moderate"
            else -> "poor — exertion outdoors less pleasant"
        } ?: return null
        val detail = listOfNotNull(
            pm25?.let { "PM2.5 ${it.roundToInt()}" },
            pm10?.let { "PM10 ${it.roundToInt()}" },
            no2?.let { "NO₂ ${it.roundToInt()}" },
        ).joinToString(", ")
        return "$label ($detail)"
    }
}
