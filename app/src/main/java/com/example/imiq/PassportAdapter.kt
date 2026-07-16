package com.example.imiq

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Adapts a DYCONET `baseline_1.0` passport (11 needs) into the OLD 9-dimension
 * shape the DEPLOYED routing engine still expects. Verified live 2026-06-04:
 * /api/routing/ranked-routes reads top-level id / values{9} / beliefs{3}; the
 * 11-need build (commit 2e2fc77) is committed but not deployed, so sending the
 * raw baseline_1.0 passport yields all-zero scores and a foot-only result.
 *
 * The mapping is lossy by necessity:
 *  - safety_accident + safety_crime collapse into one `safety` (the old single dim);
 *  - `reliable` and `health_infection` have no 9-dim slot and are dropped;
 *  - the old `hedonism` NEED has no 11-need source, so we borrow the `hedonic`
 *    value orientation as the closest available signal.
 * Beliefs are inferred from routing_parameters.mode_weights (>0.01 => owns/has),
 * mirroring the engine's own 11-need inference rule.
 */
object PassportAdapter {
    private val gson = Gson()

    private const val MODE_WEIGHT_THRESHOLD = 0.01

    /**
     * Builds the engine `cognitive_passport` request field from the stored passport
     * JSON (as returned by [PassportStore.load]). Returns the unwrapped 9-dim object
     * `{ id, values{9}, beliefs{3} }` that the deployed engine reads directly.
     */
    fun toEnginePassport(storedJson: String): JsonObject {
        val root = gson.fromJson(storedJson, JsonObject::class.java)
        // PassportStore holds {"cognitive_passport": {...}}; unwrap one level if present.
        val cp = if (root.has("cognitive_passport") && root.get("cognitive_passport").isJsonObject)
            root.getAsJsonObject("cognitive_passport") else root

        val profile = cp.getAsJsonObjectOrNull("profile")
        val needs = profile?.getAsJsonObjectOrNull("needs") ?: JsonObject()
        val values = profile?.getAsJsonObjectOrNull("values") ?: JsonObject()
        val weights = cp.getAsJsonObjectOrNull("routing_parameters")
            ?.getAsJsonObjectOrNull("mode_weights") ?: JsonObject()

        fun need(key: String, default: Double = 0.0) = needs.numberOr(key, default)
        fun value(key: String, default: Double = 0.5) = values.numberOr(key, default)
        fun weight(key: String) = weights.numberOr(key, 0.0)

        val nineValues = JsonObject().apply {
            addProperty("pro_environment", need("pro_env"))
            addProperty("physical_activity", need("physical"))
            addProperty("privacy", need("privacy"))
            addProperty("autonomy", need("autonomy"))
            addProperty("hedonism", value("hedonic"))                       // no 11-need source
            addProperty("cost_saving", need("cost"))
            addProperty("speed", need("speed"))
            addProperty("safety", (need("safety_accident") + need("safety_crime")) / 2.0)
            addProperty("comfort", need("comfort"))
        }

        val beliefs = JsonObject().apply {
            addProperty("owns_car", weight("car") > MODE_WEIGHT_THRESHOLD)
            addProperty("owns_bike", weight("bike") > MODE_WEIGHT_THRESHOLD)
            addProperty("has_pt_access", weight("pt") > MODE_WEIGHT_THRESHOLD)
        }

        val id = cp.stringOrNull("agent_id") ?: cp.stringOrNull("id") ?: "app-user"

        return JsonObject().apply {
            addProperty("id", id)
            add("values", nineValues)
            add("beliefs", beliefs)
        }
    }

    private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? =
        if (has(key) && get(key).isJsonObject) getAsJsonObject(key) else null

    private fun JsonObject.numberOr(key: String, default: Double): Double =
        if (has(key) && !get(key).isJsonNull) {
            runCatching { get(key).asDouble }.getOrDefault(default)
        } else default

    private fun JsonObject.stringOrNull(key: String): String? =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asString }.getOrNull() else null
}
