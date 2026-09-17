package com.example.imiq

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// =====================================================================
//  DYCONET Cognitive Passport Questionnaire
//  Phase 1 availability architecture:
//  - every HOTCO-CT input is an explicit current-user response;
//  - availability is asked before mode representations and becomes q;
//  - mode-use frequency is not collected or sent to HOTCO-CT;
//  - unavailable modes are still rated for beliefs/valence so their latent
//    representation is measured, while q keeps them inactive in this run.
// =====================================================================

private data class NeedSpec(val key: String, val label: String, val help: String)
private data class ModeSpec(val key: String, val label: String, val emoji: String)

private val NEEDS = listOf(
    NeedSpec("comfort_physical", "Travel comfort",     "Comfortable seats, cleanliness, pleasant temperature, quiet"),
    NeedSpec("reliable",         "Reliability",        "Punctuality, predictability, no cancellations"),
    NeedSpec("flex",             "Flexibility",        "Spontaneous departures, independence from schedules"),
    NeedSpec("cost",             "Cost",               "Low ticket prices, low maintenance, low fuel costs"),
    NeedSpec("safety_crime",     "Personal security",  "Protection from harassment or crime, feeling safe at night"),
    NeedSpec("health_activity",  "Physical activity",  "Active transport, fitness, fresh air"),
    NeedSpec("time",             "Time saving",        "Fastest route, short travel time, no traffic jams"),
    NeedSpec("health_infection", "Health protection",  "Low infection risk, hygiene, virus-free"),
    NeedSpec("crowding",         "Privacy / space",    "No crowding, personal space, distance from others"),
    NeedSpec("safety_accident",  "Traffic safety",     "Protection from accidents, safe technology, safe driving"),
    NeedSpec("env",              "Eco-friendliness",   "Low CO2 emissions, climate protection, clean air"),
)

// Phase 1 keeps the current four HOTCO actions fixed. Later availability
// providers can expose additional services without changing this questionnaire
// contract until the HOTCO topology itself is deliberately extended.
private val MODES = listOf(
    ModeSpec("walk", "Walking", "🚶"),
    ModeSpec("bike", "Bicycle / E-Bike", "🚴"),
    ModeSpec("pt",   "Public Transport (Bus / Tram)", "🚌"),
    ModeSpec("car",  "Car (Driver)", "🚗"),
)

internal val ENVIRONMENTAL_TOLERANCE_KEYS = listOf(
    "rain", "crowding", "darkness", "traffic", "temperature",
)

private fun phase1WelcomeBody(): String = when (LanguageState.current) {
    AppLanguage.DE -> "Ein Fragebogen in sechs Teilen erfasst die aktuellen Eingaben für HOTCO-CT. Verfügbarkeit und Umweltverträglichkeit werden ausdrücklich von dir angegeben; Nutzungshäufigkeit wird weder erhoben noch als Ersatz für Verfügbarkeit oder Präferenz verwendet."
    AppLanguage.EN -> "A six-part questionnaire collects the current inputs for HOTCO-CT. Availability and environmental tolerance are declared explicitly by you; mode-use frequency is neither collected nor used as a proxy for availability or preference."
}

private fun phase1AvailabilityIntro(): String = when (LanguageState.current) {
    AppLanguage.DE -> "Auf welche Verkehrsmittel hast du im Alltag derzeit tatsächlich Zugriff?"
    AppLanguage.EN -> "Which transport modes do you currently have access to in everyday life?"
}

private fun phase1AvailabilityHint(): String = when (LanguageState.current) {
    AppLanguage.DE -> "Gib strukturellen Zugang an, nicht ob ein Verkehrsmittel für einen einzelnen Weg gerade bequem ist. Diese Antworten werden als binäre HOTCO-CT-Aktionsgates verwendet; Wetter, Distanz und Routing kommen erst in einer späteren Kontextschicht hinzu."
    AppLanguage.EN -> "Report structural access, not whether a mode is convenient for one specific trip. These answers become binary HOTCO-CT action gates; weather, distance, and routing belong to a later context layer."
}

private fun unavailableMeasurementNote(): String = when (LanguageState.current) {
    AppLanguage.DE -> "Dieses Verkehrsmittel ist für die aktuelle Simulation deaktiviert. Die folgenden Antworten erfassen nur deine Vorstellung davon und aktivieren es nicht."
    AppLanguage.EN -> "This mode is gated out of the current simulation. The following answers only measure your representation of it and do not make it available."
}

private fun phase1BeliefsIntro(s: Strings): String = s.beliefsIntro + when (LanguageState.current) {
    AppLanguage.DE -> " Auch nicht verfügbare Verkehrsmittel werden bewertet, damit Überzeugungen und Zugang getrennt bleiben."
    AppLanguage.EN -> " Unavailable modes are still rated so beliefs remain separate from access."
}

private fun phase1ValenceIntro(s: Strings): String = s.emovalIntro + when (LanguageState.current) {
    AppLanguage.DE -> " Bewerte auch nicht verfügbare Verkehrsmittel; die Valenz beschreibt deine Bewertung, nicht deinen aktuellen Zugang."
    AppLanguage.EN -> " Rate unavailable modes too; valence describes your evaluation, not your current access."
}

// =====================================================================
//  Main composable — multi-step orchestrator
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileComplete: () -> Unit,
    onBackClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    var step by remember { mutableStateOf(0) }
    val totalSteps = 6
    var participantId by remember { mutableStateOf("") }

    // Empty maps are intentional: a neutral-looking UI position is not a user
    // response. Every required model input must be explicitly selected.
    val needsAnswers = remember { mutableStateMapOf<String, Float>() }
    val topPriorities = remember { mutableStateListOf<String>() }
    val availability = remember { mutableStateMapOf<String, Boolean>() }
    val valences = remember { mutableStateMapOf<String, Float>() }
    val beliefs = remember { mutableStateMapOf<String, MutableMap<String, Float>>() }
    val environmentalTolerances = remember { mutableStateMapOf<String, Int>() }

    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiPassportRead by remember { mutableStateOf<String?>(null) }
    var aiReady by remember { mutableStateOf(false) }

    fun goBackOrExit() {
        if (step == 0) onBackClick() else step--
    }

    fun submit() {
        generating = true
        error = null
        aiReady = false

        val participantIdSnapshot = participantId
        val needsSnapshot = needsAnswers.toMap()
        val topPrioritiesSnapshot = topPriorities.toList()
        val availabilitySnapshot = availability.toMap()
        val valencesSnapshot = valences.toMap()
        val beliefsSnapshot = beliefs.mapValues { (_, ratings) -> ratings.toMap() }
        val environmentalTolerancesSnapshot = environmentalTolerances.toMap()

        scope.launch {
            try {
                val json = buildSurveyJson(
                    participantId = participantIdSnapshot,
                    needs = needsSnapshot,
                    top3 = topPrioritiesSnapshot,
                    valences = valencesSnapshot,
                    beliefs = beliefsSnapshot,
                    availability = availabilitySnapshot,
                    environmentalTolerances = environmentalTolerancesSnapshot,
                )
                val passport = PassportApiService.generatePassport(json)
                require(isValidPassportV2(passport)) {
                    "Server returned an invalid Cognitive Passport v2."
                }
                PassportStore.save(passport)
                step = 8
                // Passport creation is complete. LLM narration is a separate,
                // server-side, user-triggered feature and is not part of setup.
                aiReady = true
            } catch (e: Exception) {
                error = if (LanguageState.current == AppLanguage.DE)
                    "Der Cognitive Passport konnte nicht erstellt werden. Bitte versuche es erneut."
                else "The Cognitive Passport couldn't be created. Please try again."
            } finally {
                generating = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stepTitleFor(s, step), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (step in 1..6) {
                            Text(
                                String.format(s.stepXofY, step, totalSteps),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { goBackOrExit() }, enabled = !generating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomBar(
                step = step,
                canAdvance = canAdvance(
                    step,
                    participantId,
                    needsAnswers,
                    topPriorities,
                    beliefs,
                    valences,
                    availability,
                    environmentalTolerances,
                ),
                generating = generating,
                aiReady = aiReady,
                onBack = { goBackOrExit() },
                onNext = {
                    if (step == 6) {
                        step = 7
                        submit()
                    } else {
                        step++
                    }
                },
                onFinish = {
                    check(PassportStore.hasPassport()) {
                        "A validated HOTCO-CT passport is required before continuing."
                    }
                    TokenManager.markProfileCompleted(participantId.trim())
                    onProfileComplete()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (step in 1..6) {
                LinearProgressIndicator(
                    progress = step / 6f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when (step) {
                0 -> WelcomeStep(
                    participantId = participantId,
                    onParticipantIdChange = { participantId = it },
                )
                1 -> NeedsStep(needsAnswers)
                2 -> TopPrioritiesStep(needsAnswers, topPriorities)
                3 -> AvailabilityStep(availability)
                4 -> BeliefsStep(beliefs, availability)
                5 -> EmovalStep(valences, availability)
                6 -> EnvironmentalToleranceStep(environmentalTolerances)
                7 -> GeneratingStep(generating = generating, error = error, onRetry = { submit() })
                8 -> ResultStep(aiPassportRead, aiReady)
            }
        }
    }
}

private fun stepTitleFor(s: Strings, step: Int): String = when (step) {
    0 -> s.stepWelcome
    1 -> s.stepNeeds
    2 -> s.stepPriorities
    3 -> s.stepAvailability
    4 -> s.stepBeliefs
    5 -> s.stepEmoval
    6 -> s.stepEnvironmentalTolerance
    7 -> s.stepGenerating
    8 -> s.stepResult
    else -> ""
}

private fun canAdvance(
    step: Int,
    participantId: String,
    needs: Map<String, Float>,
    top3: List<String>,
    beliefs: Map<String, Map<String, Float>>,
    valences: Map<String, Float>,
    availability: Map<String, Boolean>,
    environmentalTolerances: Map<String, Int>,
): Boolean = when (step) {
    0 -> participantId.isNotBlank()
    1 -> hasCompleteIntegerRatings(needs, NEEDS.map { it.key }, 1..7)
    2 -> top3.size == 3 && top3.distinct().size == 3 &&
        top3.all { key -> NEEDS.any { it.key == key } }
    3 -> MODES.all { it.key in availability } &&
        MODES.any { availability[it.key] == true }
    4 -> MODES.all { mode ->
        hasCompleteIntegerRatings(beliefs[mode.key].orEmpty(), NEEDS.map { it.key }, 1..7)
    }
    5 -> hasCompleteIntegerRatings(valences, MODES.map { it.key }, 1..7)
    6 -> hasCompleteEnvironmentalToleranceRatings(environmentalTolerances)
    else -> true
}

internal fun hasCompleteEnvironmentalToleranceRatings(answers: Map<String, Int>): Boolean =
    ENVIRONMENTAL_TOLERANCE_KEYS.all { answers[it] in 1..7 }

private fun hasCompleteIntegerRatings(
    answers: Map<String, Float>,
    requiredKeys: Collection<String>,
    range: IntRange,
): Boolean = requiredKeys.all { key ->
    val value = answers[key] ?: return@all false
    value.isFinite() &&
        value >= range.first.toFloat() &&
        value <= range.last.toFloat() &&
        value % 1f == 0f
}

// =====================================================================
//  Step 0 — Welcome
// =====================================================================

@Composable
private fun WelcomeStep(
    participantId: String,
    onParticipantIdChange: (String) -> Unit,
) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            s.welcomeHeadline,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            phase1WelcomeBody(),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = participantId,
            onValueChange = onParticipantIdChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(s.participantId) },
            supportingText = { Text(s.participantIdHint) },
        )
        Spacer(Modifier.height(32.dp))
        InfoRow("1", s.welcomeInfo1)
        InfoRow("2", s.welcomeInfo2)
        InfoRow("3", s.welcomeInfo6)
        InfoRow("4", s.welcomeInfo3)
        InfoRow("5", s.welcomeInfo5)
        InfoRow("6", s.stepEnvironmentalTolerance)
    }
}

@Composable
private fun InfoRow(num: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp)
    }
}

// =====================================================================
//  Step 1 — Needs (11 explicit ratings, 1–7)
// =====================================================================

@Composable
private fun NeedsStep(answers: MutableMap<String, Float>) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            s.needsIntro,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            s.needsScaleHint,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        NEEDS.forEach { need ->
            NeedRatingCard(
                label = s.needLabels[need.key] ?: need.label,
                help = s.needHelps[need.key] ?: need.help,
                value = answers[need.key]?.toInt(),
                onChange = { answers[need.key] = it.toFloat() }
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun NeedRatingCard(label: String, help: String, value: Int?, onChange: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        help,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    value?.toString() ?: "—",
                    fontWeight = FontWeight.Bold,
                    color = if (value == null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            RatingRow(value = value, range = 1..7, onSelect = onChange)
        }
    }
}

// =====================================================================
//  Step 2 — Top 3 Priorities
// =====================================================================

@Composable
private fun TopPrioritiesStep(
    needsAnswers: Map<String, Float>,
    top3: MutableList<String>,
) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            s.prioritiesIntro,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PyramidSlot(rank = 1, needKey = top3.getOrNull(0), onRemove = { top3.removeAt(0) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PyramidSlot(rank = 2, needKey = top3.getOrNull(1), onRemove = { top3.removeAt(1) })
            PyramidSlot(rank = 3, needKey = top3.getOrNull(2), onRemove = { top3.removeAt(2) })
        }
        Spacer(Modifier.height(8.dp))
        Text(s.prioritiesTapHint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        NEEDS.forEach { need ->
            val isPicked = top3.contains(need.key)
            val rank = top3.indexOf(need.key).takeIf { it >= 0 }?.plus(1)
            NeedPickRow(
                label = s.needLabels[need.key] ?: need.label,
                rating = requireNotNull(needsAnswers[need.key]) {
                    "Top-priority selection requires all 11 need ratings."
                }.toInt(),
                picked = isPicked,
                rank = rank,
                enabled = !isPicked && top3.size < 3,
                onClick = {
                    if (!isPicked && top3.size < 3) top3.add(need.key)
                    else if (isPicked) top3.remove(need.key)
                }
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun PyramidSlot(rank: Int, needKey: String?, onRemove: () -> Unit) {
    val s = LocalStrings.current
    val needLabel = needKey?.let { s.needLabels[it] ?: NEEDS.firstOrNull { n -> n.key == it }?.label }
    val filled = needKey != null
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(width = 110.dp, height = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (filled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = filled) { onRemove() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "#$rank",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                needLabel ?: s.empty,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = if (filled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NeedPickRow(
    label: String,
    rating: Int,
    picked: Boolean,
    rank: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    val containerColor = when {
        picked -> MaterialTheme.colorScheme.primaryContainer
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (picked && rank != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        rank.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(
                String.format(s.ratingLabel, rating),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================================
//  Step 3 — Explicit structural availability
// =====================================================================

@Composable
private fun AvailabilityStep(availability: MutableMap<String, Boolean>) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            phase1AvailabilityIntro(),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            phase1AvailabilityHint(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MODES.forEach { mode ->
            AvailabilityCard(
                mode = mode,
                value = availability[mode.key],
                onValue = { availability[mode.key] = it },
            )
        }
        if (availability.size == MODES.size && availability.values.none { it }) {
            Text(s.availabilityAtLeastOne, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AvailabilityCard(mode: ModeSpec, value: Boolean?, onValue: (Boolean) -> Unit) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mode.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    s.onbModeLabels[mode.key] ?: mode.label,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = value == true,
                    onClick = { onValue(true) },
                    label = { Text(s.available) },
                )
                FilterChip(
                    selected = value == false,
                    onClick = { onValue(false) },
                    label = { Text(s.unavailable) },
                )
            }
        }
    }
}

// =====================================================================
//  Step 4 — Beliefs: all 4 modes × all 11 needs (44 explicit ratings)
// =====================================================================

@Composable
private fun BeliefsStep(
    beliefs: MutableMap<String, MutableMap<String, Float>>,
    availability: Map<String, Boolean>,
) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            phase1BeliefsIntro(s),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            s.beliefsScaleHint,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        MODES.forEach { mode ->
            BeliefModeCard(
                mode = mode,
                available = availability[mode.key],
                ratingOf = { needKey -> beliefs[mode.key]?.get(needKey)?.toInt() },
                onRate = { needKey, value ->
                    beliefs.getOrPut(mode.key) { mutableStateMapOf() }[needKey] = value.toFloat()
                },
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BeliefModeCard(
    mode: ModeSpec,
    available: Boolean?,
    ratingOf: (String) -> Int?,
    onRate: (String, Int) -> Unit,
) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mode.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    s.onbModeLabels[mode.key] ?: mode.label,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                available?.let {
                    Text(
                        if (it) s.available else s.unavailable,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (it) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (available == false) {
                Spacer(Modifier.height(6.dp))
                Text(
                    unavailableMeasurementNote(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NEEDS.forEach { need ->
                Spacer(Modifier.height(14.dp))
                Text(
                    s.needLabels[need.key] ?: need.label,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                RatingRow(
                    value = ratingOf(need.key),
                    range = 1..7,
                    onSelect = { onRate(need.key, it) }
                )
            }
        }
    }
}

/** Explicit selectable response buttons; null means the user has not answered. */
@Composable
private fun RatingRow(value: Int?, range: IntRange, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        range.forEach { n ->
            val selected = value == n
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(n) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    n.toString(),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// =====================================================================
//  Step 5 — Action valence (four explicit bipolar ratings)
// =====================================================================

@Composable
private fun EmovalStep(
    valences: MutableMap<String, Float>,
    availability: Map<String, Boolean>,
) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            phase1ValenceIntro(s),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            s.emovalHint,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        MODES.forEach { mode ->
            ValenceCard(
                emoji = mode.emoji,
                label = s.onbModeLabels[mode.key] ?: mode.label,
                available = availability[mode.key],
                value = valences[mode.key]?.toInt(),
                onValue = { valences[mode.key] = it.toFloat() },
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ValenceCard(
    emoji: String,
    label: String,
    available: Boolean?,
    value: Int?,
    onValue: (Int) -> Unit,
) {
    val s = LocalStrings.current
    val faceEmoji = when {
        value == null -> "❔"
        value <= 2 -> "😡"
        value <= 3 -> "🙁"
        value <= 4 -> "😐"
        value <= 5 -> "🙂"
        value <= 6 -> "😊"
        else -> "😍"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Medium)
                    if (available == false) {
                        Text(
                            s.unavailable,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(faceEmoji, fontSize = 26.sp)
            }
            if (available == false) {
                Spacer(Modifier.height(6.dp))
                Text(
                    unavailableMeasurementNote(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            RatingRow(value = value, range = 1..7, onSelect = onValue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(s.negative, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.positive, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// =====================================================================
//  Step 6 — Explicit environmental tolerance self-report
// =====================================================================

@Composable
private fun EnvironmentalToleranceStep(answers: MutableMap<String, Int>) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            s.environmentalToleranceIntro,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${s.environmentalToleranceLow} · ${s.environmentalToleranceHigh}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ENVIRONMENTAL_TOLERANCE_KEYS.forEach { key ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.environmentalToleranceQuestions.getValue(key),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                        Text(
                            answers[key]?.toString() ?: "—",
                            fontWeight = FontWeight.Bold,
                            color = if (answers[key] == null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    RatingRow(
                        value = answers[key],
                        range = 1..7,
                        onSelect = { answers[key] = it },
                    )
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

// =====================================================================
//  Step 7 — Generating
// =====================================================================

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun GeneratingStep(generating: Boolean, error: String?, onRetry: () -> Unit) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (generating) {
            BrainPulseAnimation()
            Spacer(Modifier.height(40.dp))

            val stages = listOf(
                s.genStage1, s.genStage2, s.genStage3, s.genStage4, s.genStage5, s.genStage6,
            )
            var stageIdx by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(900)
                    if (stageIdx < stages.size - 1) stageIdx++
                    else break
                }
            }
            AnimatedContent(
                targetState = stages[stageIdx],
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 })
                        .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 })
                },
                label = "stage_text"
            ) { text ->
                Text(
                    text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            ThinkingDots()
            Spacer(Modifier.height(20.dp))
            Text(
                s.generatingServer,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (error != null) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(s.genErrTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                s.genErrHelp,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(s.tryAgain) }
        }
    }
}

@Composable
private fun BrainPulseAnimation() {
    val infinite = rememberInfiniteTransition(label = "brain")
    val scale1 by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scale1",
    )
    val scale2 by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scale2",
    )
    val alpha1 by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "alpha1",
    )
    val alpha2 by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "alpha2",
    )
    val coreScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "core",
    )

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale1)
                .clip(CircleShape)
                .background(primary.copy(alpha = alpha1)),
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale2)
                .clip(CircleShape)
                .background(primary.copy(alpha = alpha2)),
        )
        Box(
            modifier = Modifier
                .size(86.dp)
                .scale(coreScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("🧠", fontSize = 44.sp)
        }
    }
}

@Composable
private fun ThinkingDots() {
    val infinite = rememberInfiniteTransition(label = "dots")
    val dotCount = 3
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until dotCount) {
            val scale by infinite.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(i * 150),
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// =====================================================================
//  Step 8 — Result
// =====================================================================

@Composable
private fun ResultStep(aiRead: String?, aiReady: Boolean) {
    val s = LocalStrings.current
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear = true }
    val badgeScale by animateFloatAsState(
        targetValue = if (appear) 1f else 0.5f,
        animationSpec = tween(600, easing = LinearOutSlowInEasing), label = "badge"
    )
    val fade by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = tween(700), label = "fade"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(badgeScale)
                .clip(CircleShape)
                .background(Mob.brandGradient),
            contentAlignment = Alignment.Center
        ) {
            Text("🪪", fontSize = 46.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            s.resultReady,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(fade)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            s.resultSubtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(fade)
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth().alpha(fade),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    s.travelPersonality,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                when {
                    aiRead != null -> Text(
                        aiRead,
                        fontSize = 14.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    !aiReady -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            s.readingProfile,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    else -> Text(
                        s.resultSubtitle,
                        fontSize = 14.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(fade)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                s.resultTailored,
                fontSize = 13.sp, lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(60.dp))
    }
}

// =====================================================================
//  Bottom navigation bar
// =====================================================================

@Composable
private fun BottomBar(
    step: Int,
    canAdvance: Boolean,
    generating: Boolean,
    aiReady: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val s = LocalStrings.current
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step in 1..6 && !generating) {
                OutlinedButton(onClick = onBack) { Text(s.back) }
            }
            Spacer(Modifier.weight(1f))
            when (step) {
                0 -> Button(onClick = onNext, enabled = canAdvance) { Text(s.letsBegin) }
                in 1..6 -> Button(onClick = onNext, enabled = canAdvance) {
                    Text(if (step == 6) s.generatePassport else s.nextBtn)
                }
                7 -> { /* generating / retry is rendered inside the step */ }
                8 -> if (aiReady) {
                    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text(s.continueToApp)
                    }
                } else {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(s.readingProfile)
                    }
                }
            }
        }
    }
}

// =====================================================================
//  Strict HOTCO-CT v4.3 request builder — input schema 2.1
// =====================================================================

internal fun buildSurveyJson(
    participantId: String,
    needs: Map<String, Float>,
    top3: List<String>,
    valences: Map<String, Float>,
    beliefs: Map<String, Map<String, Float>>,
    availability: Map<String, Boolean>,
    environmentalTolerances: Map<String, Int?> = emptyMap(),
): String {
    val agentId = participantId.trim()
    require(agentId.isNotEmpty()) { "A user-provided participant identifier is required." }
    val needKeys = NEEDS.map { it.key }.toSet()
    val modeKeys = MODES.map { it.key }.toSet()
    fun Float.isIntegerRating(range: ClosedFloatingPointRange<Float>): Boolean =
        isFinite() && this in range && this % 1f == 0f

    fun ratingIssues(
        answers: Map<String, Float>,
        requiredKeys: Set<String>,
        range: ClosedFloatingPointRange<Float>,
    ): Pair<List<String>, List<String>> {
        val missing = requiredKeys.filter { answers[it] == null }.sorted()
        val invalid = requiredKeys.filter { key ->
            answers[key]?.let { !it.isIntegerRating(range) } == true
        }.sorted()
        return missing to invalid
    }

    val (missingNeeds, invalidNeeds) = ratingIssues(needs, needKeys, 1f..7f)
    require(missingNeeds.isEmpty() && invalidNeeds.isEmpty()) {
        buildString {
            append("All 11 need ratings must be explicitly selected from 1 to 7.")
            if (missingNeeds.isNotEmpty()) append(" Missing: ${missingNeeds.joinToString()}.")
            if (invalidNeeds.isNotEmpty()) append(" Invalid: ${invalidNeeds.joinToString()}.")
        }
    }

    require(
        top3.size == 3 && top3.distinct().size == 3 && top3.all { it in needKeys }
    ) { "Exactly three distinct, recognized ranked priorities are required." }

    require(modeKeys.all { it in availability } && modeKeys.any { availability[it] == true }) {
        "All four explicit availability responses and at least one available mode are required."
    }

    val beliefIssues = modeKeys.flatMap { mode ->
        val modeBeliefs = beliefs[mode].orEmpty()
        val (missing, invalid) = ratingIssues(modeBeliefs, needKeys, 1f..7f)
        buildList {
            if (missing.isNotEmpty()) add("$mode missing ${missing.joinToString()}")
            if (invalid.isNotEmpty()) add("$mode invalid ${invalid.joinToString()}")
        }
    }
    require(beliefIssues.isEmpty()) {
        "All 44 need-mode ratings must be explicitly selected from 1 to 7. " +
            beliefIssues.joinToString("; ")
    }

    val (missingValences, invalidValences) = ratingIssues(valences, modeKeys, 1f..7f)
    require(missingValences.isEmpty() && invalidValences.isEmpty()) {
        "All four valence ratings must be explicitly selected from 1 to 7."
    }

    return buildJsonObject {
        put("schema_version", "hotco_ct_input_2.1")
        put("agent_id", agentId)
        put("responses", buildJsonObject {
            put("needs", buildJsonObject {
                NEEDS.forEach { need -> put(need.key, requireNotNull(needs[need.key]).toInt()) }
            })
            put("availability", buildJsonObject {
                MODES.forEach { mode -> put(mode.key, requireNotNull(availability[mode.key])) }
            })
            put("beliefs", buildJsonObject {
                MODES.forEach { mode ->
                    put(mode.key, buildJsonObject {
                        NEEDS.forEach { need ->
                            put(need.key, requireNotNull(beliefs[mode.key]?.get(need.key)).toInt())
                        }
                    })
                }
            })
            put("valences", buildJsonObject {
                MODES.forEach { mode ->
                    put(mode.key, requireNotNull(valences[mode.key]).toInt() - 4)
                }
            })
            put("top_needs_ranking", buildJsonArray { top3.forEach { add(it) } })
            put("environmental_tolerances", buildJsonObject {
                ENVIRONMENTAL_TOLERANCE_KEYS.forEach { key ->
                    val tolerance = likertToTolerance(environmentalTolerances[key])
                    if (tolerance == null) put(key, JsonNull) else put(key, tolerance)
                }
            })
        })
    }.toString()
}

internal fun likertToTolerance(value: Int?): Double? {
    if (value == null) return null
    require(value in 1..7) { "Environmental tolerance response must be within 1..7." }
    return (value - 1) / 6.0
}

// =====================================================================
//  Strict response validation before anything is stored on the device
// =====================================================================

internal fun isValidPassportV2(passportJson: String): Boolean {
    return try {
        val cp = Json.parseToJsonElement(passportJson).jsonObject["cognitive_passport"]?.jsonObject
            ?: return false
        if (cp["schema_version"]?.jsonPrimitive?.contentOrNull != "2.0") return false

        val provenance = cp["input_provenance"]?.jsonObject ?: return false
        if (provenance["source_schema"]?.jsonPrimitive?.contentOrNull != "hotco_ct_input_2.1") return false
        if (provenance["policy"]?.jsonPrimitive?.contentOrNull != "current_user_responses_only") return false
        if (provenance["imputation_used"]?.jsonPrimitive?.booleanOrNull != false) return false
        if (provenance["population_or_synthetic_values_used"]?.jsonPrimitive?.booleanOrNull != false) return false

        val availabilityResolution = provenance["availability_resolution"]?.jsonObject ?: return false
        if (availabilityResolution["policy"]?.jsonPrimitive?.contentOrNull !=
            "explicit_user_declared_access_only") return false
        if (availabilityResolution["external_provider_data_used"]?.jsonPrimitive?.booleanOrNull != false) return false
        if (availabilityResolution["frequency_or_preference_inference_used"]?.jsonPrimitive?.booleanOrNull != false) return false

        val counts = provenance["observed_counts"]?.jsonObject ?: return false
        if (counts["needs"]?.jsonPrimitive?.intOrNull != 11) return false
        if (counts["beliefs"]?.jsonPrimitive?.intOrNull != 44) return false
        if (counts["valences"]?.jsonPrimitive?.intOrNull != 4) return false
        if (counts["availability"]?.jsonPrimitive?.intOrNull != 4) return false

        val deliberation = cp["deliberation"]?.jsonObject ?: return false
        if (deliberation["terminal_tendency"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) return false
        if (deliberation["terminal_margin"]?.jsonPrimitive?.doubleOrNull == null) return false
        deliberation["comparative_readout"]?.jsonObject?.size == 4
    } catch (_: Exception) {
        false
    }
}
