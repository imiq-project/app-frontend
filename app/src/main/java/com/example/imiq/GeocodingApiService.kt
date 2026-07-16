package com.example.imiq

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Forward-geocoding for destination entry, via the in-house Simple Engine
// Geocoding Service (Nominatim-backed, same family as the routing engine):
//   POST https://imiq-public.et.uni-magdeburg.de/api/geocode/geocode
//   {"address": "...", "lang": "de|en", "limit": n}   (verified live 2026-07-16)
// Replaces the public photon.komoot.io dependency — all location resolution
// now stays on IMIQ infrastructure.
//
// The service searches GLOBALLY (no server-side bounding), so results are
// still filtered to the Magdeburg region client-side, because that's the only
// area the routing engine + GraphHopper cover (out-of-region trips come back
// empty).
//
// NOTE: Nominatim does no prefix matching — a partial word ("hauptbah")
// returns nothing until the word is complete. The search overlay's debounce
// is tuned for that: suggestions appear when the user pauses after a word.
// ---------------------------------------------------------------------------

/** A resolved place the rest of the app can route to. */
data class GeoResult(
    val label: String,   // primary name, e.g. "Alter Markt"
    val sub: String,     // context line, e.g. "Schwertfegergasse · Magdeburg"
    val lat: Double,
    val lon: Double
)

// --- Simple Engine geocode DTOs (only the fields we use; Gson ignores the rest) ---
private data class GeocodeRequest(
    val address: String,
    val lang: String = "de",
    val limit: Int = 10
)

private data class GeocodeCandidate(
    val lat: Double? = null,
    val lon: Double? = null,
    val display_name: String? = null,
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val state: String? = null
)

private interface GeocodeApi {
    @POST("geocode")
    suspend fun geocode(@Body req: GeocodeRequest): List<GeocodeCandidate>
}

object GeocodingApiService {
    private const val BASE_URL = "https://imiq-public.et.uni-magdeburg.de/api/geocode/"

    // Magdeburg region box (~the 15 km the router serves, incl. Schönebeck
    // to the south). The geocoder itself is global, so this stays load-bearing.
    private val LAT_RANGE = 52.00..52.25
    private val LON_RANGE = 11.45..11.80

    // In-memory cache: (lang, normalized query) -> results. Avoids re-hitting
    // the server while the user edits/retypes. Process-lifetime, small.
    private val cache = HashMap<String, List<GeoResult>>()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val api: GeocodeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodeApi::class.java)
    }

    /**
     * Resolve free text to candidate places, Magdeburg-bounded.
     * Returns an empty list on short/blank input or any failure — never throws.
     */
    suspend fun search(query: String): List<GeoResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val lang = if (LanguageState.current == AppLanguage.DE) "de" else "en"
        val key = "$lang|${q.lowercase()}"
        cache[key]?.let { return it }
        return try {
            val results = api.geocode(GeocodeRequest(address = q, lang = lang))
                .mapNotNull { it.toGeoResult() }
                .filter { it.lat in LAT_RANGE && it.lon in LON_RANGE }   // keep only what the router can reach
                .take(6)
            cache[key] = results
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun GeocodeCandidate.toGeoResult(): GeoResult? {
        val la = lat ?: return null
        val lo = lon ?: return null
        val label = name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(street, housenumber).joinToString(" ").takeIf { it.isNotBlank() }
            ?: display_name?.substringBefore(",")?.takeIf { it.isNotBlank() }
            ?: "Selected place"
        val sub = listOfNotNull(
            street?.takeIf { it.isNotBlank() && it != label },
            city?.takeIf { it.isNotBlank() }
        ).distinct().take(2).joinToString(" · ")
            .ifBlank { state?.takeIf { it.isNotBlank() } ?: "Magdeburg" }
        return GeoResult(label = label, sub = sub, lat = la, lon = lo)
    }
}
