package com.example.imiq

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

/**
 * Self-contained dummy data for the intelligent-routing design.
 *
 * Everything here is shaped exactly like the real backend output (it reuses the
 * RankedRoute / DimensionScore / RouteSummary DTOs from RankedRoutesApiService),
 * so it drops in wherever the live engine/GraphHopper response is unavailable.
 *
 * Scenario: Universitätsplatz (OVGU campus) → Magdeburg Hauptbahnhof. The
 * cognitive passport leans BIKE (active / eco / cost-aware), so the engine ranks
 * bike #1 even though the car is faster — that contrast is the personalization story.
 */
object DemoRoutingData {

    data class Place(val label: String, val sub: String, val lat: Double, val lon: Double)

    val origin = Place("Universitätsplatz", "OVGU Campus", 52.13915, 11.67696)
    val destination = Place("Hauptbahnhof", "Magdeburg Central Station", 52.13030, 11.62590)

    /** Quick-pick destinations for the picker. */
    val landmarks = listOf(
        Place("Hauptbahnhof", "Central Station", 52.13030, 11.62590),
        Place("Alter Markt", "Old Town", 52.13139, 11.63916),
        Place("Allee-Center", "Shopping", 52.12089, 11.63170),
        Place("Elbauenpark", "Jahrtausendturm", 52.13639, 11.66480),
        Place("Universitätsplatz", "OVGU Campus", 52.13915, 11.67696),
        Place("Domplatz", "Cathedral", 52.12468, 11.63489)
    )

    // ---- The ranked options (engine-shaped) ------------------------------------
    // Sorted by utility. Bike wins on passport-fit; car wins on raw speed.
    fun demoRoutes(): List<RankedRoute> = listOf(
        RankedRoute(
            rank = 1,
            mode_key = "bike",
            mode_label = "Bike",
            available = true,
            score = RouteScore(utility = 92.0),
            summary = RouteSummary(duration_seconds = 1222.0, distance_meters = 4886.0, transfers = 0),
            top_matching_values = listOf(
                dim("physical_activity", 0.90, 0.71),
                dim("cost_saving", 0.85, 0.84),
                dim("pro_environment", 0.70, 0.66),
                dim("autonomy", 0.80, 0.54)
            ),
            top_conflicting_values = listOf(
                dim("comfort", 0.50, -0.20),
                dim("safety", 0.60, -0.12)
            )
        ),
        RankedRoute(
            rank = 2,
            mode_key = "car",
            mode_label = "Car",
            available = true,
            score = RouteScore(utility = 71.0),
            summary = RouteSummary(duration_seconds = 509.0, distance_meters = 5757.0, transfers = 0),
            top_matching_values = listOf(
                dim("speed", 1.00, 0.89),
                dim("comfort", 0.50, 0.74),
                dim("autonomy", 0.80, 0.60)
            ),
            top_conflicting_values = listOf(
                dim("cost_saving", 0.85, -0.51),
                dim("physical_activity", 0.90, -0.60),
                dim("pro_environment", 0.70, -0.55)
            )
        ),
        RankedRoute(
            rank = 3,
            mode_key = "foot",
            mode_label = "Walk",
            available = true,
            score = RouteScore(utility = 58.0),
            summary = RouteSummary(duration_seconds = 4173.0, distance_meters = 5793.0, transfers = 0),
            top_matching_values = listOf(
                dim("cost_saving", 0.85, 1.00),
                dim("pro_environment", 0.70, 0.60),
                dim("physical_activity", 0.90, 0.60)
            ),
            top_conflicting_values = listOf(
                dim("speed", 1.00, -0.67),
                dim("comfort", 0.50, -0.20)
            )
        )
    )

    /** The stateless LLM layer (dummy): which mode is best *right now*, and why. */
    data class AiPick(val modeKey: String, val confidence: Int, val whyNow: String, val headline: String)

    fun aiRecommendation(weatherSummary: String? = null): AiPick {
        val s = appStrings()
        val highlights = listOf("physical_activity", "cost_saving", "pro_environment")
            .joinToString(", ") { s.valueLabels[it] ?: it }
        return AiPick(
            modeKey = "bike",
            confidence = 88,
            headline = String.format(s.bestMatch, s.modeLabels["bike"] ?: "Bike"),
            whyNow = String.format(s.topsRanking, highlights)
        )
    }

    // ---- Value-dimension display metadata --------------------------------------
    data class ValueMeta(val label: String, val blurb: String, val icon: ImageVector, val color: Color)

    fun valueMeta(dimension: String): ValueMeta {
        val base = when (dimension) {
            "physical_activity" -> ValueMeta("Active", "Keeps you moving", Icons.Default.DirectionsRun, Color(0xFF84CC16))
            "cost_saving" -> ValueMeta("Saves money", "No fuel or fare", Icons.Default.Savings, Color(0xFF38BDF8))
            "pro_environment" -> ValueMeta("Eco", "Near-zero emissions", Icons.Default.Eco, Color(0xFF22C55E))
            "speed" -> ValueMeta("Fast", "Shortest travel time", Icons.Default.Bolt, Color(0xFFF59E0B))
            "comfort" -> ValueMeta("Comfort", "Relaxed, sheltered", Icons.Default.Weekend, Color(0xFFA78BFA))
            "autonomy" -> ValueMeta("Freedom", "Go on your schedule", Icons.Default.Explore, Color(0xFFFB7185))
            "safety" -> ValueMeta("Safety", "Lower accident risk", Icons.Default.Shield, Color(0xFF60A5FA))
            "privacy" -> ValueMeta("Privacy", "Your own space", Icons.Default.Lock, Color(0xFF94A3B8))
            "reliable" -> ValueMeta("Reliable", "Predictable arrival", Icons.Default.Schedule, Color(0xFF34D399))
            "hedonism" -> ValueMeta("Enjoyment", "A pleasant trip", Icons.Default.Favorite, Color(0xFFF472B6))
            else -> ValueMeta(dimension.replace('_', ' '), "", Icons.Default.Star, Color(0xFFD4408A))
        }
        // Localize the chip label when a translation exists for this dimension.
        return appStrings().valueLabels[dimension]?.let { base.copy(label = it) } ?: base
    }

    fun modeIcon(modeKey: String): ImageVector = when (modeKey.lowercase()) {
        "car", "car_pt" -> Icons.Default.DirectionsCar
        "bike", "bike_pt" -> Icons.Default.DirectionsBike
        "foot", "walk" -> Icons.Default.DirectionsWalk
        "pt" -> Icons.Default.DirectionsBus
        else -> Icons.Default.Navigation
    }

    /** "Xh Ym" / "Y min" from seconds. */
    fun formatDuration(seconds: Double): String {
        val mins = (seconds / 60.0).toInt()
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min"
    }

    fun formatDistance(meters: Double): String =
        if (meters >= 1000) String.format("%.1f km", meters / 1000.0) else "${meters.toInt()} m"

    private fun dim(dimension: String, agentWeight: Double, modeFit: Double) =
        DimensionScore(
            dimension = dimension,
            agent_weight = agentWeight,
            mode_fit = modeFit,
            contribution = agentWeight * modeFit
        )

}
