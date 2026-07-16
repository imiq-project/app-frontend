package com.example.imiq

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// gpt-5.4 called directly from the app (no server). Two jobs:
//   • explain(): pick the best mode for NOW given passport + options + LIVE
//     conditions (weather, darkness/time, air quality, traffic, parking) and
//     say why — may override the engine on live conditions.
//   • explainPassport(): a warm, insightful read of the user's whole cognitive
//     passport for the onboarding result screen.
// Returns null on missing key / failure -> callers fall back.
// ---------------------------------------------------------------------------

private data class ChatMessage(val role: String, val content: String)
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.4
)
private data class ChatResponse(val choices: List<Choice> = emptyList())
private data class Choice(val message: ChatMessage? = null)

/** The model's structured route answer: which mode to take now + a short reason. */
data class RoutePick(val mode: String = "", val why: String = "")

object RouteExplainerService {
    private const val BASE_URL = "https://api.openai.com/v1/"
    private const val MODEL = "gpt-5.4"

    private val gson = Gson()

    private val ROUTE_SYSTEM = """
        You are the IMIQ Magdeburg mobility assistant. You are given a traveller's
        cognitive-passport values, the ranked travel options, the current time, and
        LIVE local conditions. Pick the ONE best mode for RIGHT NOW and explain why
        in two short sentences.
        Weigh EVERYTHING that's relevant, not just one factor: the time of day and
        darkness (after dark, lean toward well-lit, faster, safer modes and respect
        the traveller's safety needs), rain and wind, air quality (poor air makes
        cycling/walking less pleasant), traffic, and parking pressure — alongside the
        traveller's own values. You MAY differ from the engine's top rank when the
        conditions justify it, but only choose from the modes offered.
        Be concrete and warm, and make it feel like you genuinely weighed the whole
        picture for them. Do NOT ask follow-up questions. Respond ONLY as compact
        JSON, no prose, no code fences:
        {"mode":"<one of the offered mode keys>","why":"<two sentences>"}
    """.trimIndent()

    private val PASSPORT_SYSTEM = """
        You are the IMIQ mobility assistant. You're shown a person's "cognitive
        passport" — a model of what they value and how they decide when they travel,
        built from a short questionnaire. Write 2-3 warm, insightful, slightly
        surprising sentences describing them AS A TRAVELLER: what really drives their
        choices, and any interesting tension in how they weigh things. Talk to them
        ("you…"). Do NOT just restate their top mode or list numbers back at them —
        give them a little self-insight they'd nod at. No headings, no follow-up
        questions, no bullet points.
    """.trimIndent()

    private val NEED_LABELS = mapOf(
        "pro_env" to "protecting the environment", "physical" to "staying active",
        "privacy" to "personal space", "autonomy" to "freedom and flexibility",
        "cost" to "saving money", "speed" to "speed and saving time",
        "safety_accident" to "traffic safety", "safety_crime" to "feeling safe",
        "comfort" to "comfort", "reliable" to "reliability",
        "health_infection" to "health and hygiene",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val api: OpenAiApi by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(OpenAiApi::class.java)
    }

    /** Tells the model to answer in German when the app language is DE. */
    private fun langSuffix(): String =
        if (LanguageState.current == AppLanguage.DE)
            "\n\nIMPORTANT: Write all natural-language text you return (the \"why\" field / your " +
                "description) in GERMAN, using the informal \"du\". Keep any JSON keys in English."
        else ""

    private suspend fun complete(system: String, user: String): String? {
        val key = BuildConfig.OPENAI_API_KEY
        if (key.isBlank()) return null
        return try {
            api.chat("Bearer $key", ChatRequest(MODEL, listOf(
                ChatMessage("system", system), ChatMessage("user", user)
            ))).choices.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun explain(
        passportValues: String,
        options: String,
        environment: String,
        origin: String,
        destination: String,
        timeContext: String,
    ): RoutePick? {
        val user = """
            Trip: $origin -> $destination
            Now: $timeContext
            Traveller values (0..1): $passportValues
            Options (mode | top value matches | time, distance):
            $options
            Live conditions:
            $environment
        """.trimIndent()
        return parsePick(complete(ROUTE_SYSTEM + langSuffix(), user) ?: return null)
    }

    /** Warm personality read of the whole passport for the result screen. */
    suspend fun explainPassport(passportJson: String): String? {
        val facts = passportFacts(passportJson) ?: return null
        return complete(PASSPORT_SYSTEM + langSuffix(), "Here is the traveller's cognitive passport:\n$facts")
    }

    private fun parsePick(content: String): RoutePick? {
        val start = content.indexOf('{'); val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            gson.fromJson(content.substring(start, end + 1), RoutePick::class.java)
                ?.takeIf { it.mode.isNotBlank() && it.why.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Distil the passport JSON into a few plain-language facts for the prompt. */
    private fun passportFacts(passportJson: String): String? = try {
        val cp = gson.fromJson(passportJson, JsonObject::class.java).getAsJsonObject("cognitive_passport")
        val needs = cp.getAsJsonObject("profile").getAsJsonObject("needs")
        val topNeeds = needs.entrySet()
            .map { it.key to it.value.asDouble }
            .sortedByDescending { it.second }.take(3)
            .joinToString(", ") { NEED_LABELS[it.first] ?: it.first }
        val delib = cp.getAsJsonObject("deliberation")
        val probs = delib.getAsJsonObject("probabilities").entrySet()
            .map { it.key to it.value.asDouble }
            .sortedByDescending { it.second }
            .joinToString(", ") { "${it.first} ${(it.second * 100).roundToInt()}%" }
        val finalChoice = delib.get("final_choice").asString
        val basePref = cp.getAsJsonObject("dissonance_triad")?.get("base_preference")?.asString ?: ""
        val difficulty = delib.get("decision_difficulty")?.asString ?: ""
        buildString {
            append("They care most about: $topNeeds.\n")
            append("Mode tendency: $probs.\n")
            append("Most likely choice: $finalChoice")
            if (basePref.isNotBlank() && !basePref.equals(finalChoice, true))
                append(" — though their gut instinct is $basePref, so there's real tension")
            append(".\n")
            if (difficulty.isNotBlank()) append("Decision style: $difficulty.")
        }
    } catch (e: Exception) {
        null
    }
}

private interface OpenAiApi {
    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body req: ChatRequest
    ): ChatResponse
}
