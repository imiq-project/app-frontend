package com.example.imiq

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Adapts Cognitive Passport v2 to the 11-need routing contract without adding
 * values that were not reported by the user.
 *
 * Availability comes from the questionnaire's explicit binary responses. It
 * is never inferred from comparative mode weights or past-use frequency.
 */
object PassportAdapter {
    private val gson = Gson()

    private val requiredNeeds = listOf(
        "pro_env",
        "physical",
        "privacy",
        "autonomy",
        "cost",
        "speed",
        "safety_accident",
        "safety_crime",
        "comfort",
        "reliable",
        "health_infection",
    )

    private val requiredModes = listOf("car", "bike", "pt", "walk")
    private val supportedPassportSchemas = setOf("2.0", "2.1")
    private val environmentalToleranceKeys = listOf(
        "rain", "crowding", "darkness", "traffic", "temperature",
    )

    /** Read the optional tolerance contract without altering the routing payload. */
    fun environmentalTolerances(storedJson: String): Map<String, Double?> {
        val root = gson.fromJson(storedJson, JsonObject::class.java)
        val cp = if (root.has("cognitive_passport") && root.get("cognitive_passport").isJsonObject)
            root.getAsJsonObject("cognitive_passport") else root
        val profile = cp.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject
        val tolerances = profile?.get("environmental_tolerances")
            ?.takeIf { it.isJsonObject }?.asJsonObject
        return environmentalToleranceKeys.associateWith { key ->
            val element = tolerances?.get(key)
            if (element == null || element.isJsonNull) null else {
                val value = runCatching { element.asDouble }.getOrElse {
                    throw IllegalArgumentException("Environmental tolerance '$key' is not numeric.")
                }
                require(value.isFinite() && value in 0.0..1.0) {
                    "Environmental tolerance '$key' is outside [0,1]."
                }
                value
            }
        }
    }

    /** Returns `{id, values{11}, beliefs{3}, availability{4}, mode_weights{4}}`. */
    fun toEnginePassport(storedJson: String): JsonObject {
        val root = gson.fromJson(storedJson, JsonObject::class.java)
        val cp = if (root.has("cognitive_passport") && root.get("cognitive_passport").isJsonObject)
            root.getAsJsonObject("cognitive_passport") else root

        val schemaVersion = cp.stringOrNull("schema_version")
        require(schemaVersion in supportedPassportSchemas) {
            "Routing requires a supported Cognitive Passport v2 schema (2.0 or 2.1)."
        }
        val provenance = cp.requiredObject("input_provenance")
        require(provenance.stringOrNull("policy") == "current_user_responses_only") {
            "Routing requires current-user-only input provenance."
        }
        require(!provenance.requiredBoolean("imputation_used")) {
            "Routing rejects passports that used missing-value substitution."
        }
        require(!provenance.requiredBoolean("population_or_synthetic_values_used")) {
            "Routing rejects passports that used population or synthetic values."
        }
        val observedCounts = provenance.requiredObject("observed_counts")
        require(
            observedCounts.requiredNumber("needs") == 11.0 &&
                observedCounts.requiredNumber("beliefs") == 44.0 &&
                observedCounts.requiredNumber("valences") == 4.0 &&
                observedCounts.requiredNumber("availability") == 4.0
        ) { "Routing requires all 63 observed questionnaire inputs." }
        val profile = cp.requiredObject("profile")
        val needs = profile.requiredObject("needs")
        val availability = profile.requiredObject("availability")
        val routing = cp.requiredObject("routing_parameters")
        val modeWeights = routing.requiredObject("mode_weights")

        val elevenValues = JsonObject().apply {
            requiredNeeds.forEach { key ->
                val value = needs.requiredNumber(key)
                require(value in 0.0..1.0) { "Cognitive Passport need '$key' is outside [0,1]." }
                addProperty(key, value)
            }
        }
        val availabilityOut = JsonObject().apply {
            requiredModes.forEach { key -> addProperty(key, availability.requiredBoolean(key)) }
        }
        val weightsOut = JsonObject().apply {
            requiredModes.forEach { key ->
                val value = modeWeights.requiredNumber(key)
                require(value in 0.0..1.0) { "Cognitive Passport mode weight '$key' is outside [0,1]." }
                addProperty(key, value)
            }
        }

        val beliefs = JsonObject().apply {
            addProperty("owns_car", availability.requiredBoolean("car"))
            addProperty("owns_bike", availability.requiredBoolean("bike"))
            addProperty("has_pt_access", availability.requiredBoolean("pt"))
        }

        val id = cp.stringOrNull("agent_id")
            ?: error("Cognitive Passport v2 is missing agent_id.")

        return JsonObject().apply {
            addProperty("id", id)
            add("values", elevenValues)
            add("beliefs", beliefs)
            add("availability", availabilityOut)
            add("mode_weights", weightsOut)
        }
    }

    private fun JsonObject.requiredObject(key: String): JsonObject {
        require(has(key) && get(key).isJsonObject) { "Cognitive Passport is missing object '$key'." }
        return getAsJsonObject(key)
    }

    private fun JsonObject.requiredNumber(key: String): Double {
        require(has(key) && !get(key).isJsonNull) { "Cognitive Passport is missing '$key'." }
        return runCatching { get(key).asDouble }
            .getOrElse { throw IllegalArgumentException("Cognitive Passport field '$key' is not numeric.") }
    }

    private fun JsonObject.requiredBoolean(key: String): Boolean {
        require(has(key) && !get(key).isJsonNull) { "Cognitive Passport is missing '$key'." }
        return runCatching { get(key).asBoolean }
            .getOrElse { throw IllegalArgumentException("Cognitive Passport field '$key' is not boolean.") }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asString }.getOrNull() else null
}
