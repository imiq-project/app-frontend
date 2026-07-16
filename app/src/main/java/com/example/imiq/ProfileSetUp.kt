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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

// =====================================================================
//  DYCONET Cognitive Passport Questionnaire
//  Replaces the old chat-style profile setup. Collects survey answers
//  matching the LimeSurvey schema, POSTs to the Spark backend, and
//  saves the returned cognitive passport on the device.
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

// v1 (boss decision 2026-06-05): only 4 aggregated modes. These keys are the
// DYCONET parser's canonical JSON keys, so the SAME key works for frequencies
// (s1_/s2_), valences (emoval) AND beliefs.
private val MODES = listOf(
    ModeSpec("walk", "Walking", "🚶"),
    ModeSpec("bike", "Bicycle / E-Bike", "🚴"),
    ModeSpec("pt",   "Public Transport (Bus / Tram)", "🚌"),
    ModeSpec("car",  "Car (Driver)", "🚗"),
)

// =====================================================================
//  Main composable — multi-step orchestrator
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileComplete: (ClassificationResult) -> Unit,
    onBackClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    // -- Internal step state --
    var step by remember { mutableStateOf(0) }
    val totalSteps = 5

    // -- Answer state --
    val needsAnswers = remember {
        mutableStateMapOf<String, Float>().apply { NEEDS.forEach { put(it.key, 4f) } }
    }
    val topPriorities = remember { mutableStateListOf<String>() }  // ordered list of need keys, max 3
    val frequencies = remember {
        mutableStateMapOf<String, Float>().apply { MODES.forEach { put(it.key, 1f) } }
    }
    val valences = remember {
        mutableStateMapOf<String, Float>().apply { MODES.forEach { put(it.key, 4f) } }
    }
    val valencesNA = remember {
        mutableStateMapOf<String, Boolean>().apply { MODES.forEach { put(it.key, false) } }
    }
    // beliefs[modeKey][needKey] = rating 1..7 (only the user's Top-3 needs are asked).
    // beliefsNA[modeKey] = "I never use / no opinion" → mode omitted, model imputes it.
    val beliefs = remember { mutableStateMapOf<String, MutableMap<String, Float>>() }
    val beliefsNA = remember {
        mutableStateMapOf<String, Boolean>().apply { MODES.forEach { put(it.key, false) } }
    }

    // -- Generation state --
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var passportSummary by remember { mutableStateOf<PassportSummary?>(null) }
    var aiPassportRead by remember { mutableStateOf<String?>(null) }
    // True once the AI read has FINISHED (success or failure) — gates "Continue".
    var aiReady by remember { mutableStateOf(false) }

    fun goBackOrExit() {
        if (step == 0) onBackClick() else step--
    }

    fun submit() {
        generating = true
        error = null
        aiReady = false
        scope.launch {
            try {
                val json = buildSurveyJson(
                    needs = needsAnswers,
                    top3 = topPriorities,
                    frequencies = frequencies,
                    valences = valences,
                    valencesNA = valencesNA,
                    beliefs = beliefs,
                    beliefsNA = beliefsNA,
                )
                val passport = PassportApiService.generatePassport(json)
                PassportStore.save(passport)
                passportSummary = summarizePassport(passport)
                step = 7 // result screen
                // gpt-5.4 reads the whole passport for the result screen. The result
                // step blocks "Continue" until this finishes (aiReady). Capped at 20s
                // so a slow/failed call never traps the user on this screen.
                scope.launch {
                    aiPassportRead = withTimeoutOrNull(20_000) {
                        runCatching { RouteExplainerService.explainPassport(passport) }.getOrNull()
                    }
                    aiReady = true
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not reach the server"
            } finally {
                generating = false
            }
        }
    }

    // Offline fallback: if the DYCONET server is unreachable, complete onboarding
    // with the bundled sample passport so the user is never hard-blocked. Uses the
    // user's real slider answers for the local ProfileType derivation; only the
    // generated cognitive passport is the bundled baseline_1.0 sample.
    fun useOfflineTemplate() {
        TokenManager.saveCognitivePassportFromTemplate()
        val tpl = PassportStore.load()
        passportSummary = tpl?.let { summarizePassport(it) }
        aiReady = false
        if (tpl != null) {
            scope.launch {
                aiPassportRead = withTimeoutOrNull(20_000) {
                    runCatching { RouteExplainerService.explainPassport(tpl) }.getOrNull()
                }
                aiReady = true
            }
        } else {
            aiReady = true
        }
        error = null
        step = 7
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stepTitleFor(s, step), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (step in 1..5) {
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
                canAdvance = canAdvance(step, needsAnswers, topPriorities),
                generating = generating,
                aiReady = aiReady,
                onBack = { goBackOrExit() },
                onNext = {
                    // Step 5 (mode feelings) is the last data-collection step.
                    // Tapping "Generate passport" moves to the loading step
                    // AND kicks off the network request.
                    if (step == 5) {
                        step = 6
                        submit()
                    } else {
                        step++
                    }
                },
                onFinish = {
                    val classification = passportSummary?.let {
                        derivedClassification(needsAnswers, it)
                    } ?: fallbackClassification()
                    // Mark profile as completed so the next app launch skips
                    // setup and lands on main_menu directly.
                    val name = TokenManager.getUserName() ?: ""
                    val age = TokenManager.getUserAge() ?: ""
                    TokenManager.saveUserProfile(name, age, classification.profileType.value)
                    onProfileComplete(classification)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (step in 1..5) {
                LinearProgressIndicator(
                    progress = step / 5f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when (step) {
                0 -> WelcomeStep()
                1 -> NeedsStep(needsAnswers)
                2 -> TopPrioritiesStep(needsAnswers, topPriorities)
                3 -> BeliefsStep(topPriorities, beliefs, beliefsNA)
                4 -> FrequenciesStep(frequencies)
                5 -> EmovalStep(valences, valencesNA)
                6 -> GeneratingStep(generating = generating, error = error, onRetry = { submit() }, onOffline = { useOfflineTemplate() })
                7 -> ResultStep(aiPassportRead, aiReady)
            }
        }
    }
}

private fun stepTitleFor(s: Strings, step: Int): String = when (step) {
    0 -> s.stepWelcome
    1 -> s.stepNeeds
    2 -> s.stepPriorities
    3 -> s.stepBeliefs
    4 -> s.stepFrequencies
    5 -> s.stepEmoval
    6 -> s.stepGenerating
    7 -> s.stepResult
    else -> ""
}

private fun canAdvance(
    step: Int,
    needs: Map<String, Float>,
    top3: List<String>,
): Boolean = when (step) {
    2 -> top3.size == 3  // must pick exactly 3
    else -> true
}

// =====================================================================
//  Step 0 — Welcome
// =====================================================================

@Composable
private fun WelcomeStep() {
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
            s.welcomeBody,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        InfoRow("1", s.welcomeInfo1)
        InfoRow("2", s.welcomeInfo2)
        InfoRow("3", s.welcomeInfo3)
        InfoRow("4", s.welcomeInfo4)
        InfoRow("5", s.welcomeInfo5)
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
//  Step 1 — Needs (11 sliders, 1–7)
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
            NeedSliderCard(
                label = s.needLabels[need.key] ?: need.label,
                help = s.needHelps[need.key] ?: need.help,
                value = answers[need.key] ?: 4f,
                onChange = { answers[need.key] = it }
            )
        }
        Spacer(Modifier.height(80.dp))  // leave room above bottom bar
    }
}

@Composable
private fun NeedSliderCard(label: String, help: String, value: Float, onChange: (Float) -> Unit) {
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
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        value.toInt().toString(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Slider(value = value, onValueChange = onChange, valueRange = 1f..7f, steps = 5)
        }
    }
}

// =====================================================================
//  Step 2 — Top 3 Priorities (rank pyramid)
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

        // --- Pyramid display ---
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
        Text(
            s.prioritiesTapHint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        // --- Available needs ---
        NEEDS.forEach { need ->
            val isPicked = top3.contains(need.key)
            val rank = top3.indexOf(need.key).takeIf { it >= 0 }?.plus(1)
            NeedPickRow(
                label = s.needLabels[need.key] ?: need.label,
                rating = needsAnswers[need.key]?.toInt() ?: 4,
                picked = isPicked,
                rank = rank,
                enabled = !isPicked && top3.size < 3,
                onClick = {
                    if (!isPicked && top3.size < 3) {
                        top3.add(need.key)
                    } else if (isPicked) {
                        top3.remove(need.key)
                    }
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
//  Step 3 — Beliefs: how well each mode meets your Top-3 needs (1–7)
// =====================================================================

@Composable
private fun BeliefsStep(
    top3: List<String>,
    beliefs: MutableMap<String, MutableMap<String, Float>>,
    beliefsNA: MutableMap<String, Boolean>,
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
            s.beliefsIntro,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            s.beliefsScaleHint,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (top3.isEmpty()) {
            Text(
                s.beliefsPickFirst,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(4.dp))
        MODES.forEach { mode ->
            BeliefModeCard(
                mode = mode,
                top3 = top3,
                na = beliefsNA[mode.key] ?: false,
                ratingOf = { needKey -> beliefs[mode.key]?.get(needKey)?.toInt() },
                onRate = { needKey, value ->
                    beliefs.getOrPut(mode.key) { mutableStateMapOf() }[needKey] = value.toFloat()
                },
                onNA = { isNA -> beliefsNA[mode.key] = isNA },
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BeliefModeCard(
    mode: ModeSpec,
    top3: List<String>,
    na: Boolean,
    ratingOf: (String) -> Int?,
    onRate: (String, Int) -> Unit,
    onNA: (Boolean) -> Unit,
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
                FilterChip(
                    selected = na,
                    onClick = { onNA(!na) },
                    label = { Text(s.neverUse, fontSize = 11.sp) }
                )
            }
            if (!na) {
                top3.forEachIndexed { idx, needKey ->
                    Spacer(Modifier.height(14.dp))
                    Text(s.needLabels[needKey] ?: needKey, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    if (idx == 0) {
                        Text(
                            s.mostImportantNeed,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LikertRow(value = ratingOf(needKey), onSelect = { onRate(needKey, it) })
                }
            }
        }
    }
}

/** 1–7 selectable buttons, matching the survey mockup. */
@Composable
private fun LikertRow(value: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..7).forEach { n ->
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
//  Step 4 — Mode Frequencies (4 modes, 1–5)
// =====================================================================

@Composable
private fun FrequenciesStep(frequencies: MutableMap<String, Float>) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            s.frequenciesIntro,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            s.frequenciesScaleHint,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        MODES.forEach { mode ->
            FrequencyRow(
                emoji = mode.emoji,
                label = s.onbModeLabels[mode.key] ?: mode.label,
                value = frequencies[mode.key] ?: 1f,
                onChange = { frequencies[mode.key] = it }
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun FrequencyRow(emoji: String, label: String, value: Float, onChange: (Float) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        value.toInt().toString(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Slider(value = value, onValueChange = onChange, valueRange = 1f..5f, steps = 3)
        }
    }
}

// =====================================================================
//  Step 4 — Emoval (emoji slider per mode, with "No answer")
// =====================================================================

@Composable
private fun EmovalStep(
    valences: MutableMap<String, Float>,
    valencesNA: MutableMap<String, Boolean>,
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
            s.emovalIntro,
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
                value = valences[mode.key] ?: 4f,
                na = valencesNA[mode.key] ?: false,
                onValue = { valences[mode.key] = it },
                onNA = { valencesNA[mode.key] = it }
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ValenceCard(
    emoji: String,
    label: String,
    value: Float,
    na: Boolean,
    onValue: (Float) -> Unit,
    onNA: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val faceEmoji = when {
        na          -> "😐"   // neutral
        value <= 2f -> "😡"   // angry
        value <= 3f -> "🙁"   // frown
        value <= 4f -> "😐"   // neutral
        value <= 5f -> "🙂"   // slight smile
        value <= 6f -> "😊"   // smile
        else        -> "😍"   // heart eyes
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(faceEmoji, fontSize = 26.sp)
            }
            if (!na) {
                Slider(value = value, onValueChange = onValue, valueRange = 1f..7f, steps = 5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(s.negative, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.positive, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = na, onCheckedChange = { onNA(it) })
                Text(s.noExperience, fontSize = 12.sp)
            }
        }
    }
}

// =====================================================================
//  Step 5 — Generating
// =====================================================================

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun GeneratingStep(generating: Boolean, error: String?, onRetry: () -> Unit, onOffline: () -> Unit) {
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOffline) { Text(s.useOffline) }
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
//  Step 6 — Result
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

        // ✨ gpt-5.4 personality read — shows the model's own narrative instantly,
        // then upgrades to the LLM read when it arrives (or stays if the LLM fails).
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
        // No "preferred mode" here on purpose — the personalization shows up in the
        // route suggestions, not as a single label.
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
            if (step in 1..5 && !generating) {
                OutlinedButton(onClick = onBack) { Text(s.back) }
            }
            Spacer(Modifier.weight(1f))
            when (step) {
                0 -> Button(onClick = onNext) { Text(s.letsBegin) }
                in 1..5 -> Button(onClick = onNext, enabled = canAdvance) {
                    Text(if (step == 5) s.generatePassport else s.nextBtn)
                }
                6 -> { /* no buttons during generating; retry inside step */ }
                7 -> if (aiReady) {
                    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text(s.continueToApp)
                    }
                } else {
                    // AI personality read still in flight — block Continue and show why.
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
//  Survey JSON builder — matches the LimeSurvey format the parser expects
// =====================================================================

private fun buildSurveyJson(
    needs: Map<String, Float>,
    top3: List<String>,
    frequencies: Map<String, Float>,
    valences: Map<String, Float>,
    valencesNA: Map<String, Boolean>,
    beliefs: Map<String, Map<String, Float>>,
    beliefsNA: Map<String, Boolean>,
): String {
    // APP block — ratings, ranking, beliefs (per-mode ratings for the Top-3 needs).
    val app = buildJsonObject {
        put("answers", buildJsonObject {
            put("ratings", buildJsonObject {
                needs.forEach { (k, v) -> put(k, v.toInt()) }
                put("attn_check", 7)   // hidden attention check; required by parser
            })
            put("ranking", buildJsonArray { top3.forEach { add(it) } })
            // beliefs: {mode: {top3_need: 1..7}} for every mode the user DOES use.
            // The parser reads these Top-3 ratings and imputes the remaining needs.
            put("beliefs", buildJsonObject {
                MODES.forEach { mode ->
                    if (beliefsNA[mode.key] != true) {
                        put(mode.key, buildJsonObject {
                            top3.forEach { needKey ->
                                put(needKey, (beliefs[mode.key]?.get(needKey) ?: 4f).toInt())
                            }
                        })
                    }
                }
            })
            // Modes marked "I never use / no opinion" → omitted above + imputed.
            put("skippedModes", buildJsonArray {
                MODES.forEach { if (beliefsNA[it.key] == true) add(it.key) }
            })
        })
    }

    // MOBIL block — frequencies for both scenarios (s1 = work, s2 = leisure).
    // We collect once and duplicate to both scenarios.
    val mobil = buildJsonObject {
        frequencies.forEach { (m, v) ->
            put("s1_$m", v.toInt())
            put("s2_$m", v.toInt())
        }
    }

    // emoval block — slider 1..7 → emoval -3..+3; NA stays as "NA"
    val emoval = buildJsonObject {
        put("answers", buildJsonObject {
            MODES.forEach { mode ->
                val isNA = valencesNA[mode.key] ?: false
                if (isNA) {
                    put(mode.key, "NA")
                } else {
                    val v = (valences[mode.key] ?: 4f).toInt() - 4  // -3..+3
                    put(mode.key, v)
                }
            }
        })
    }

    val survey = buildJsonObject {
        put("id", 1)
        put("PROFILE", "{\"answers\":{}}")
        put("MOBIL", mobil.toString())
        put("APP", app.toString())
        put("emoval", emoval.toString())
        // 'What matters in life' (values) question was removed. The parser still
        // requires the section to exist, so send it empty → all value orientations
        // default to neutral (values are display-only in the baseline model).
        put("values", "{\"answers\":{}}")
        put("POI", "[]")
    }
    return survey.toString()
}

// =====================================================================
//  Passport summary parsing (for the result step)
// =====================================================================

internal data class PassportSummary(
    val mode: String,
    val confidence: Double,
)

internal fun summarizePassport(passportJson: String): PassportSummary? = try {
    val cp = Json.parseToJsonElement(passportJson).jsonObject["cognitive_passport"]?.jsonObject
    val deliberation = cp?.get("deliberation")?.jsonObject
    PassportSummary(
        mode       = deliberation?.get("final_choice")?.jsonPrimitive?.content ?: "?",
        confidence = deliberation?.get("confidence")?.jsonPrimitive?.doubleOrNull ?: 0.0,
    )
} catch (_: Exception) { null }

// =====================================================================
//  Derive a ClassificationResult so MainActivity's downstream screens
//  (which still expect the old 5-category profile) can keep working.
// =====================================================================

private fun derivedClassification(
    needs: Map<String, Float>,
    summary: PassportSummary,
): ClassificationResult {
    val topNeed = needs.maxByOrNull { it.value }?.key
    val profileType = when (topNeed) {
        "env", "health_activity" -> ProfileType.ECO_WARRIOR
        "comfort_physical", "safety_crime", "crowding" -> ProfileType.COMFORT_SEEKER
        "time", "flex"                                 -> ProfileType.TIME_OPTIMIZER
        "cost"                                         -> ProfileType.BUDGET_CONSCIOUS
        else                                           -> ProfileType.FLEXIBLE_PRAGMATIST
    }
    val classifier = MobilityClassifier()
    val profile = classifier.getProfile(profileType)
        ?: classifier.getProfile(ProfileType.FLEXIBLE_PRAGMATIST)!!
    return ClassificationResult(
        profileType  = profileType,
        profile      = profile,
        scores       = ProfileType.values().associate { it to 0f },
        confidence   = (summary.confidence * 100).toFloat(),
        explanations = listOf("Derived from cognitive passport (mode: ${summary.mode}).")
    )
}

private fun fallbackClassification(): ClassificationResult {
    val classifier = MobilityClassifier()
    val profile = classifier.getProfile(ProfileType.FLEXIBLE_PRAGMATIST)!!
    return ClassificationResult(
        profileType  = ProfileType.FLEXIBLE_PRAGMATIST,
        profile      = profile,
        scores       = ProfileType.values().associate { it to 0f },
        confidence   = 50f,
        explanations = listOf("Fallback profile.")
    )
}
