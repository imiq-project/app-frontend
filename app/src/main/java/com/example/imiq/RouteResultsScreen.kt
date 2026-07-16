package com.example.imiq

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.google.gson.JsonObject
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun RouteResultsScreen(
    destination: DemoRoutingData.Place = DemoRoutingData.destination,
    userProfile: ClassificationResult? = null,
    onBackClick: () -> Unit = {}
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }

    // Real ranking from the engine (demo fallback) + GraphHopper geometry for the
    // map + a gpt-5.4 "why now" insight informed by live FIWARE conditions. Every
    // network step degrades gracefully so the screen never breaks.
    var engineRoutes by remember { mutableStateOf<List<RankedRoute>>(emptyList()) }
    var origin by remember { mutableStateOf(DemoRoutingData.origin.lat to DemoRoutingData.origin.lon) }
    var originLabel by remember { mutableStateOf(DemoRoutingData.origin.label) }
    var geometry by remember { mutableStateOf<Map<String, RouteGeo>>(emptyMap()) }
    var llmPick by remember { mutableStateOf<DemoRoutingData.AiPick?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(destination) {
        val loc = try { locationHelper.getCurrentLocation() } catch (e: Exception) { null }
        val start = loc ?: origin
        origin = start
        if (loc != null) originLabel = s.yourLocation

        val passport = PassportStore.load()?.let { runCatching { PassportAdapter.toEnginePassport(it) }.getOrNull() }
        val ranked = fetchRankedRoutes(passport, start, destination)
        engineRoutes = ranked
        val routesForLlm = ranked.ifEmpty { DemoRoutingData.demoRoutes() }
        geometry = fetchRouteGeometry(start, destination, routesForLlm.map { it.mode_key })

        // AI insight: live FIWARE conditions + gpt-5.4. Bounded so the card never
        // spins forever; on timeout/failure we keep the data-driven template.
        withTimeoutOrNull(8000) {
            val env = runCatching { EnvironmentService.fetch(destination.lat, destination.lon) }.getOrNull()
            explainRoute(passport, routesForLlm, env, originLabel, destination)?.let { llmPick = it }
        }
        loaded = true
    }

    // Cards: ranking/utility/chips from the engine, distance/time from the drawn
    // GraphHopper route. The AI card prefers gpt-5.4's pick, else the template.
    val baseRoutes = engineRoutes.ifEmpty { DemoRoutingData.demoRoutes() }
    val mapped = baseRoutes.map { r -> geometry[r.mode_key]?.let { r.copy(summary = it.summary) } ?: r }
    val displayRoutes = mapped.filter { it.available }.ifEmpty { mapped }
    val template = remember(engineRoutes) { recommendationFor(engineRoutes) }
    val ai = llmPick ?: template
    // Demo-friendly "match" score: the recommended (AI) mode always reads top (~96),
    // everything else scales to [60,94] off the engine utility — no stark 0s, and the
    // badge + the highest number always agree.
    val maxUtil = displayRoutes.maxOfOrNull { it.score?.utility ?: 0.0 }?.takeIf { it > 0.0 } ?: 1.0
    fun matchScore(r: RankedRoute): Int =
        if (r.mode_key == ai.modeKey) 96
        else (60 + ((r.score?.utility ?: 0.0) / maxUtil).coerceIn(0.0, 1.0) * 34).roundToInt()

    var selectedMode by remember { mutableStateOf(ai.modeKey) }
    LaunchedEffect(ai.modeKey) { selectedMode = ai.modeKey }
    var showDirections by remember { mutableStateOf(false) }

    val selectedRoute = displayRoutes.firstOrNull { it.mode_key == selectedMode } ?: displayRoutes.first()
    val accent = Mob.modeColor(selectedMode)
    val routePolyline = geometry[selectedMode]?.polyline ?: listOf(origin, destination.lat to destination.lon)

    Column(Modifier.fillMaxSize().background(Mob.bg)) {

        // Map (top ~42%)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.42f)
        ) {
            MobMap(
                modifier = Modifier.fillMaxSize(),
                route = routePolyline,
                routeColor = accent
            )
            // top gradient + controls
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Mob.bg.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Mob.glass)
                        .border(1.dp, Mob.border, CircleShape)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Mob.glass)
                        .border(1.dp, Mob.border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(originLabel, color = Mob.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.ArrowForward, null, tint = accent, modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
                    Text(destination.label, color = Mob.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Sheet (bottom ~58%)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(0.58f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Mob.surface)
                .border(1.dp, Mob.border, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
                Spacer(Modifier.height(16.dp))

                AiHero(ai = ai, analyzing = !loaded)

                Spacer(Modifier.height(18.dp))
                MobSectionLabel(s.chooseHowToGo)
                Spacer(Modifier.height(10.dp))

                displayRoutes.forEach { route ->
                    ModeRow(
                        route = route,
                        selected = route.mode_key == selectedMode,
                        isAiPick = route.mode_key == ai.modeKey,
                        matchPct = matchScore(route),
                        onClick = { selectedMode = route.mode_key }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            // Sticky Start button
            Column(
                Modifier
                    .background(Mob.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                MobButton(
                    text = "${s.start} · ${s.modeLabels[selectedRoute.mode_key.lowercase()] ?: selectedRoute.mode_label} · ${DemoRoutingData.formatDuration(selectedRoute.summary?.duration_seconds ?: 0.0)}",
                    leadingIcon = Icons.Default.Navigation,
                    color = accent,
                    onClick = {
                        // In-app turn-by-turn for the routable modes; transit (or no
                        // step data) hands off to Google Maps instead.
                        val steps = geometry[selectedMode]?.instructions ?: emptyList()
                        if (selectedMode.equals("pt", true) || steps.isEmpty())
                            launchNavigation(context, origin, destination, selectedMode)
                        else showDirections = true
                    }
                )
            }
        }

        if (showDirections) {
            DirectionsSheet(
                modeKey = selectedMode,
                modeLabel = s.modeLabels[selectedMode.lowercase()] ?: selectedRoute.mode_label,
                originLabel = originLabel,
                destination = destination,
                summary = selectedRoute.summary,
                steps = geometry[selectedMode]?.instructions ?: emptyList(),
                onOpenMaps = { showDirections = false; launchNavigation(context, origin, destination, selectedMode) },
                onClose = { showDirections = false },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Ranking from the engine + geometry from GraphHopper.
//  - fetchRankedRoutes: POST the adapted 9-dim passport + start/stop to the
//    ranked-routes engine; returns its ranked List<RankedRoute> (utility,
//    value-fit, summary). Empty on no-passport or failure -> caller uses demo.
//  - fetchRouteGeometry: one GraphHopper path per routable mode (parallel), for
//    the map polyline + a distance/time that matches the drawn line. A mode with
//    no GraphHopper profile (e.g. pt) or a failed call is omitted. GraphHopper
//    returns [lon, lat]; MobMap wants (lat, lon).
// ---------------------------------------------------------------------------
private data class RouteGeo(
    val polyline: List<Pair<Double, Double>>,
    val summary: RouteSummary,
    val instructions: List<GhInstruction> = emptyList(),
)

// Hands off to Google Maps for real turn-by-turn navigation in the chosen mode.
// IMIQ picks the best mode for the user's cognitive passport; Maps drives the trip.
// Uses the universal Maps "directions" URL so it works for car/bike/walk AND transit,
// opening the Maps app if installed (else the browser). Never throws.
private fun launchNavigation(
    context: android.content.Context,
    origin: Pair<Double, Double>,
    destination: DemoRoutingData.Place,
    modeKey: String,
) {
    val url = "https://www.google.com/maps/dir/?api=1" +
        "&origin=${origin.first},${origin.second}" +
        "&destination=${destination.lat},${destination.lon}" +
        "&travelmode=${googleTravelMode(modeKey)}"
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun googleTravelMode(modeKey: String): String = when (modeKey.lowercase()) {
    "car", "car_pt" -> "driving"
    "bike", "bike_pt" -> "bicycling"
    "foot", "walk" -> "walking"
    "pt" -> "transit"
    else -> "driving"
}

private suspend fun fetchRankedRoutes(
    passport: JsonObject?,
    start: Pair<Double, Double>,
    destination: DemoRoutingData.Place
): List<RankedRoute> {
    if (passport == null) return emptyList()
    return try {
        RankedRoutesApiService.api.rankedRoutes(
            RankedRoutesRequest(
                cognitive_passport = passport,
                start = GeoPoint2(start.first, start.second),
                stop = GeoPoint2(destination.lat, destination.lon),
                datetime = null,
                include_unavailable = false
            )
        ).routes
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun fetchRouteGeometry(
    start: Pair<Double, Double>,
    destination: DemoRoutingData.Place,
    modeKeys: List<String>
): Map<String, RouteGeo> = coroutineScope {
    modeKeys.distinct().mapNotNull { mode ->
        val profile = GraphHopperApiService.profileForMode(mode) ?: return@mapNotNull null
        async {
            try {
                val resp = GraphHopperApiService.api.route(
                    points = listOf(
                        GraphHopperApiService.point(start.first, start.second),
                        GraphHopperApiService.point(destination.lat, destination.lon)
                    ),
                    profile = profile,
                    locale = if (LanguageState.current == AppLanguage.DE) "de" else "en"
                )
                val path = resp.paths.firstOrNull() ?: return@async null
                val poly = path.points?.coordinates
                    ?.mapNotNull { if (it.size >= 2) it[1] to it[0] else null }
                    ?: emptyList()
                if (poly.isEmpty()) null
                else mode to RouteGeo(poly, RouteSummary(path.time / 1000.0, path.distance, 0), path.instructions)
            } catch (e: Exception) {
                null
            }
        }
    }.awaitAll().filterNotNull().toMap()
}

/** Builds the "AI recommendation" card from the engine's top-ranked route. Falls
 *  back to the demo pick when the engine returned nothing. (The narrative is a
 *  data-driven template, not an LLM — that's the still-pending /route/explain.) */
private fun recommendationFor(engineRoutes: List<RankedRoute>): DemoRoutingData.AiPick {
    val s = appStrings()
    val best = (engineRoutes.firstOrNull { it.available } ?: engineRoutes.firstOrNull())
        ?: return DemoRoutingData.aiRecommendation()
    val labels = best.top_matching_values.take(3)
        .map { DemoRoutingData.valueMeta(it.dimension).label }
        .filter { it.isNotBlank() }
    val why = if (labels.isEmpty()) s.rankedHighest
    else String.format(s.topsRanking, labels.joinToString(", "))
    return DemoRoutingData.AiPick(
        modeKey = best.mode_key,
        confidence = 92,  // recommendation confidence (not the engine's raw value-fit)
        headline = String.format(s.bestMatch, s.modeLabels[best.mode_key.lowercase()] ?: best.mode_label),
        whyNow = why
    )
}

/** Shapes passport + options + live env into a prompt, calls gpt-5.4, and maps
 *  the model's pick to an AiPick. The chosen mode is validated against the
 *  offered routes (snaps to rank-1 if the model names something unexpected).
 *  Returns null if the model gave no usable answer -> caller keeps the template. */
private suspend fun explainRoute(
    passport: JsonObject?,
    routes: List<RankedRoute>,
    env: EnvSummary?,
    origin: String,
    destination: DemoRoutingData.Place
): DemoRoutingData.AiPick? {
    if (routes.isEmpty()) return null
    val valuesStr = runCatching {
        passport?.getAsJsonObject("values")?.entrySet()
            ?.joinToString(", ") { "${it.key}=${it.value.asString}" }
    }.getOrNull() ?: "unknown"
    val optionsStr = routes.joinToString("\n") { r ->
        val match = r.top_matching_values.take(3).joinToString("/") { it.dimension }.ifBlank { "—" }
        "- ${r.mode_key} | $match | " +
            "${DemoRoutingData.formatDuration(r.summary?.duration_seconds ?: 0.0)}, " +
            DemoRoutingData.formatDistance(r.summary?.distance_meters ?: 0.0)
    }
    val envStr = env?.asPromptLines() ?: "No live conditions available."
    val now = java.time.LocalDateTime.now()
    val part = when (now.hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "night" }
    val day = now.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() }
    val timeContext = "%s %02d:%02d, %s".format(day, now.hour, now.minute, part)
    val pick = RouteExplainerService.explain(valuesStr, optionsStr, envStr, origin, destination.label, timeContext)
        ?: return null
    val mode = routes.firstOrNull { it.mode_key.equals(pick.mode, ignoreCase = true) }?.mode_key
        ?: routes.first().mode_key
    val route = routes.firstOrNull { it.mode_key == mode }
    val s = appStrings()
    return DemoRoutingData.AiPick(
        modeKey = mode,
        confidence = 94,  // AI's confidence in the recommendation, not raw value-fit
        headline = String.format(s.bestForNow, s.modeLabels[mode.lowercase()] ?: route?.mode_label ?: mode),
        whyNow = pick.why
    )
}

@Composable
private fun AiHero(ai: DemoRoutingData.AiPick, analyzing: Boolean) {
    val s = LocalStrings.current
    val accent = Mob.modeColor(ai.modeKey)
    MobGlassCard(color = accent.copy(alpha = 0.10f), border = accent.copy(alpha = 0.35f), radius = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.aiRecommendation, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            if (!analyzing) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, null, tint = accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${ai.confidence}%", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = analyzing,
            transitionSpec = { fadeIn(tween(350)).togetherWith(fadeOut(tween(200))) },
            label = "reveal"
        ) { isAnalyzing ->
            if (isAnalyzing) {
                AnalyzingRow()
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(DemoRoutingData.modeIcon(ai.modeKey), null, tint = accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(ai.headline, color = Mob.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(ai.whyNow, color = Mob.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun AnalyzingRow() {
    val s = LocalStrings.current
    val t = rememberInfiniteTransition(label = "an")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Sensors, null, tint = Mob.primary.copy(alpha = a), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            s.analyzing,
            color = Mob.textPrimary.copy(alpha = a), fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ModeRow(
    route: RankedRoute,
    selected: Boolean,
    isAiPick: Boolean,
    matchPct: Int,
    onClick: () -> Unit
) {
    val s = LocalStrings.current
    val accent = Mob.modeColor(route.mode_key)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Mob.surfaceHi)
            .border(1.dp, if (selected) accent.copy(alpha = 0.6f) else Mob.border, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleGlyph(DemoRoutingData.modeIcon(route.mode_key), accent, diameter = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.modeLabels[route.mode_key.lowercase()] ?: route.mode_label, color = Mob.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (isAiPick) {
                        Text(
                            s.aiPick, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier.background(accent.copy(alpha = 0.18f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${DemoRoutingData.formatDuration(route.summary?.duration_seconds ?: 0.0)} · ${DemoRoutingData.formatDistance(route.summary?.distance_meters ?: 0.0)}",
                    color = Mob.textSecondary, fontSize = 13.sp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreRing(
                    progress = matchPct / 100f,
                    color = accent,
                    size = 50.dp
                )
                Spacer(Modifier.height(2.dp))
                Text(s.match, color = Mob.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }

        // value chips
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            route.top_matching_values.take(4).forEach { ds ->
                val meta = DemoRoutingData.valueMeta(ds.dimension)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(meta.color.copy(alpha = 0.14f))
                        .border(1.dp, meta.color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(meta.icon, null, tint = meta.color, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(meta.label, color = Mob.textPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// In-app turn-by-turn directions, from GraphHopper's instructions. Shown for the
// routable modes (car/bike/walk); a button hands off to Google Maps for live nav.
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionsSheet(
    modeKey: String,
    modeLabel: String,
    originLabel: String,
    destination: DemoRoutingData.Place,
    summary: RouteSummary?,
    steps: List<GhInstruction>,
    onOpenMaps: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalStrings.current
    val accent = Mob.modeColor(modeKey)
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Mob.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleGlyph(DemoRoutingData.modeIcon(modeKey), accent, diameter = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.directionsTitle, color = Mob.textMuted, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                    Text(modeLabel, color = Mob.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${DemoRoutingData.formatDuration(summary?.duration_seconds ?: 0.0)} · " +
                        DemoRoutingData.formatDistance(summary?.distance_meters ?: 0.0),
                    color = Mob.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("$originLabel → ${destination.label}", color = Mob.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Mob.border))

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                steps.forEachIndexed { i, step ->
                    StepRow(i + 1, step.text, step.distance, accent)
                    if (i < steps.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Mob.border.copy(alpha = 0.5f)))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            MobButton(s.openInMaps, leadingIcon = Icons.Default.Map, color = accent, onClick = onOpenMaps)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepRow(num: Int, text: String, distanceMeters: Double, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$num", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text.ifBlank { "—" }, color = Mob.textPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            if (distanceMeters >= 1.0) {
                Text(DemoRoutingData.formatDistance(distanceMeters), color = Mob.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07090D, heightDp = 820)
@Composable
private fun RouteResultsPreview() {
    RouteResultsScreen()
}
