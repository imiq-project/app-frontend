package com.example.imiq

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch


private data class AdaptiveNeedSpec(
    val key: String,
    val en: String,
    val de: String,
)


private val ADAPTIVE_UI_NEEDS =
    listOf(
        AdaptiveNeedSpec(
            "comfort_physical",
            "Travel comfort",
            "Reisekomfort",
        ),
        AdaptiveNeedSpec(
            "reliable",
            "Reliability",
            "Zuverlässigkeit",
        ),
        AdaptiveNeedSpec(
            "flex",
            "Flexibility and autonomy",
            "Flexibilität und Autonomie",
        ),
        AdaptiveNeedSpec(
            "cost",
            "Cost",
            "Kosten",
        ),
        AdaptiveNeedSpec(
            "safety_crime",
            "Personal security",
            "Persönliche Sicherheit",
        ),
        AdaptiveNeedSpec(
            "health_activity",
            "Physical activity",
            "Körperliche Aktivität",
        ),
        AdaptiveNeedSpec(
            "time",
            "Time saving",
            "Zeitersparnis",
        ),
        AdaptiveNeedSpec(
            "health_infection",
            "Health protection",
            "Gesundheitsschutz",
        ),
        AdaptiveNeedSpec(
            "crowding",
            "Privacy and space",
            "Privatsphäre und Platz",
        ),
        AdaptiveNeedSpec(
            "safety_accident",
            "Traffic safety",
            "Verkehrssicherheit",
        ),
        AdaptiveNeedSpec(
            "env",
            "Eco-friendliness",
            "Umweltfreundlichkeit",
        ),
    )


private val ADAPTIVE_MODES =
    listOf(
        "walk",
        "bike",
        "pt",
        "car",
    )


@Composable
fun AdaptiveProfileSetupScreen(
    onProfileComplete: () -> Unit,
    onBackClick: () -> Unit,
) {

    val de =
        LanguageState.current ==
            AppLanguage.DE

    val scope =
        rememberCoroutineScope()

    var step by remember {
        mutableStateOf(0)
    }

    var participantId by remember {
        mutableStateOf("")
    }

    val needs =
        remember {
            mutableStateMapOf<
                String,
                Float
            >()
        }

    val availability =
        remember {
            mutableStateMapOf<
                String,
                Boolean
            >()
        }

    val valences =
        remember {
            mutableStateMapOf<
                String,
                Float
            >()
        }

    var startJson by remember {
        mutableStateOf<String?>(null)
    }

    var questions by remember {
        mutableStateOf<
            List<
                AdaptiveOnboardingQuestion
            >
        >(
            emptyList()
        )
    }

    val beliefAnswers =
        remember {
            mutableStateMapOf<
                String,
                Int
            >()
        }

    val environmentalTolerances =
        remember {
            mutableStateMapOf<
                String,
                Int
            >()
        }

    var working by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var passportReady by remember {
        mutableStateOf(false)
    }


    fun goBack() {

        if (
            working
        ) {
            return
        }

        if (
            step == 0
        ) {
            onBackClick()
        } else {
            step--
            error = null
        }
    }


    fun requestAdaptiveQuestions() {

        working = true
        error = null

        val idSnapshot =
            participantId

        val needsSnapshot =
            needs.toMap()

        val valenceSnapshot =
            valences.toMap()

        scope.launch {

            try {

                val response =
                    AdaptiveOnboardingService
                        .start(
                            participantId =
                                idSnapshot,

                            needs =
                                needsSnapshot,

                            valences =
                                valenceSnapshot,
                        )

                val parsed =
                    AdaptiveOnboardingService
                        .parseQuestions(
                            response
                        )

                beliefAnswers.clear()

                startJson =
                    response

                questions =
                    parsed

                step = 4

            } catch (
                _: Exception
            ) {

                error =
                    if (de)
                        "Die vier Kalibrierungsfragen konnten nicht geladen werden. Bitte versuche es erneut."
                    else
                        "The four calibration questions could not be loaded. Please try again."

            } finally {

                working = false
            }
        }
    }


    fun generatePassport() {

        val start =
            startJson
                ?: return

        working = true
        error = null

        val answersSnapshot =
            beliefAnswers.toMap()

        val availabilitySnapshot =
            availability.toMap()

        val toleranceSnapshot:
            Map<String, Int?> =
                environmentalTolerances
                    .mapValues {
                        (_, value) ->
                        value
                    }

        scope.launch {

            try {

                val completed =
                    AdaptiveOnboardingService
                        .complete(
                            startJson =
                                start,

                            answers =
                                answersSnapshot,
                        )

                val passport =
                    AdaptiveOnboardingService
                        .bootstrap(
                            completedJson =
                                completed,

                            availability =
                                availabilitySnapshot,

                            environmentalTolerances =
                                toleranceSnapshot,
                        )

                require(
                    isValidAdaptivePassportV2(
                        passport
                    )
                )

                PassportStore.save(
                    passport
                )

                passportReady =
                    true

                step = 6

            } catch (
                _: Exception
            ) {

                error =
                    if (de)
                        "Der Adaptive Cognitive Passport konnte nicht erstellt werden. Bitte versuche es erneut."
                    else
                        "The Adaptive Cognitive Passport could not be created. Please try again."

            } finally {

                working = false
            }
        }
    }


    val canAdvance =
        when (
            step
        ) {

            0 ->
                participantId
                    .trim()
                    .isNotEmpty()

            1 ->
                ADAPTIVE_UI_NEEDS
                    .all {
                        need ->

                        needs[
                            need.key
                        ]?.let {
                            value ->

                            value.isFinite() &&
                                value in 1f..7f &&
                                value % 1f == 0f
                        } == true
                    }

            2 ->
                ADAPTIVE_MODES
                    .all {
                        it in availability
                    } &&
                    ADAPTIVE_MODES
                        .any {
                            availability[
                                it
                            ] == true
                        }

            3 ->
                ADAPTIVE_MODES
                    .all {
                        mode ->

                        valences[
                            mode
                        ]?.let {
                            value ->

                            value.isFinite() &&
                                value in 1f..7f &&
                                value % 1f == 0f
                        } == true
                    }

            4 ->
                questions.size == 4 &&
                    questions.all {
                        question ->

                        beliefAnswers[
                            question.cellId
                        ]?.let {
                            value ->

                            value in 1..7
                        } == true
                    }

            5 ->
                ENVIRONMENTAL_TOLERANCE_KEYS
                    .all {
                        key ->

                        environmentalTolerances[
                            key
                        ]?.let {
                            value ->

                            value in 1..7
                        } == true
                    }

            else ->
                true
        }


    Scaffold(

        topBar = {

            Surface(
                tonalElevation = 2.dp,
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(
                                horizontal =
                                    16.dp,

                                vertical =
                                    12.dp,
                            ),

                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {

                    TextButton(
                        onClick = {
                            goBack()
                        },

                        enabled =
                            !working,
                    ) {

                        Text(
                            if (de)
                                "Zurück"
                            else
                                "Back"
                        )
                    }

                    Spacer(
                        Modifier.width(
                            8.dp
                        )
                    )

                    Column {

                        Text(
                            adaptiveStepTitle(
                                step,
                                de,
                            ),

                            fontWeight =
                                FontWeight.SemiBold,

                            fontSize =
                                17.sp,
                        )

                        if (
                            step in 1..5
                        ) {

                            Text(
                                if (de)
                                    "Schritt $step von 5"
                                else
                                    "Step $step of 5",

                                fontSize =
                                    11.sp,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },


        bottomBar = {

            if (
                step <= 5
            ) {

                Surface(
                    tonalElevation =
                        3.dp,
                ) {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    16.dp
                                ),
                    ) {

                        error?.let {

                            Text(
                                it,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,

                                fontSize =
                                    12.sp,

                                modifier =
                                    Modifier.padding(
                                        bottom =
                                            8.dp
                                    ),
                            )
                        }

                        Button(
                            modifier =
                                Modifier.fillMaxWidth(),

                            enabled =
                                canAdvance &&
                                    !working,

                            onClick = {

                                when (
                                    step
                                ) {

                                    3 ->
                                        requestAdaptiveQuestions()

                                    5 ->
                                        generatePassport()

                                    else ->
                                        step++
                                }
                            },
                        ) {

                            if (
                                working
                            ) {

                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.size(
                                            18.dp
                                        ),

                                    strokeWidth =
                                        2.dp,
                                )

                                Spacer(
                                    Modifier.width(
                                        8.dp
                                    )
                                )
                            }

                            Text(
                                when (
                                    step
                                ) {

                                    3 ->
                                        if (de)
                                            "Kalibrierungsfragen laden"
                                        else
                                            "Load calibration questions"

                                    5 ->
                                        if (de)
                                            "Cognitive Passport erstellen"
                                        else
                                            "Create Cognitive Passport"

                                    else ->
                                        if (de)
                                            "Weiter"
                                        else
                                            "Continue"
                                }
                            )
                        }
                    }
                }
            }
        },
    ) {
        padding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        padding
                    )
        ) {

            if (
                step in 1..5
            ) {

                LinearProgressIndicator(
                    progress =
                        step / 5f,

                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }

            when (
                step
            ) {

                0 ->
                    AdaptiveWelcomeStep(
                        participantId =
                            participantId,

                        onParticipantIdChange = {
                            participantId =
                                it
                        },

                        de =
                            de,
                    )

                1 ->
                    AdaptiveNeedsStep(
                        answers =
                            needs,

                        de =
                            de,
                    )

                2 ->
                    AdaptiveAvailabilityStep(
                        answers =
                            availability,

                        de =
                            de,
                    )

                3 ->
                    AdaptiveValenceStep(
                        answers =
                            valences,

                        de =
                            de,
                    )

                4 ->
                    AdaptiveBeliefStep(
                        questions =
                            questions,

                        answers =
                            beliefAnswers,

                        de =
                            de,
                    )

                5 ->
                    AdaptiveToleranceStep(
                        answers =
                            environmentalTolerances,

                        de =
                            de,
                    )

                6 ->
                    AdaptiveResultStep(
                        de =
                            de,

                        ready =
                            passportReady,

                        onContinue = {

                            check(
                                PassportStore
                                    .hasPassport()
                            )

                            TokenManager
                                .markProfileCompleted(
                                    participantId
                                        .trim()
                                )

                            onProfileComplete()
                        },
                    )
            }
        }
    }
}


@Composable
private fun AdaptiveWelcomeStep(
    participantId: String,
    onParticipantIdChange:
        (String) -> Unit,
    de: Boolean,
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    24.dp
                ),

        verticalArrangement =
            Arrangement.Center,

        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {

        Text(
            if (de)
                "Dein Cognitive Passport"
            else
                "Your Cognitive Passport",

            fontSize =
                26.sp,

            fontWeight =
                FontWeight.Bold,

            textAlign =
                TextAlign.Center,
        )

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Text(
            if (de)
                "Der kurze Einstieg misst deine Bedürfnisse, Bewertungen und vier gezielte Zusammenhänge. Die übrigen Verbindungen werden zunächst statistisch geschätzt und später durch kurze direkte Messungen verfeinert."
            else
                "The short setup measures your needs, evaluations and four targeted relationships. Remaining relationships are initially estimated statistically and can later be refined through short direct measurements.",

            textAlign =
                TextAlign.Center,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,

            lineHeight =
                20.sp,
        )

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        OutlinedTextField(
            value =
                participantId,

            onValueChange =
                onParticipantIdChange,

            modifier =
                Modifier.fillMaxWidth(),

            singleLine =
                true,

            label = {
                Text(
                    if (de)
                        "Teilnehmer-ID"
                    else
                        "Participant ID"
                )
            },
        )

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        Text(
            if (de)
                "Keine beobachtete Antwort wird durch eine geschätzte Rohantwort ersetzt."
            else
                "No observed answer is replaced by a fabricated raw response.",

            fontSize =
                12.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,

            textAlign =
                TextAlign.Center,
        )
    }
}


@Composable
private fun AdaptiveNeedsStep(
    answers:
        MutableMap<String, Float>,
    de: Boolean,
) {

    AdaptiveScrollableStep {

        Text(
            if (de)
                "Wie wichtig sind dir diese Aspekte beim Reisen im Allgemeinen?"
            else
                "How important are these aspects to you when travelling in general?",

            fontSize =
                14.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Text(
            if (de)
                "1 = überhaupt nicht wichtig · 7 = sehr wichtig"
            else
                "1 = not important at all · 7 = very important",

            fontSize =
                12.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        ADAPTIVE_UI_NEEDS
            .forEach {
                need ->

                AdaptiveRatingCard(
                    title =
                        if (de)
                            need.de
                        else
                            need.en,

                    value =
                        answers[
                            need.key
                        ]?.toInt(),

                    onSelect = {
                        value ->

                        answers[
                            need.key
                        ] =
                            value.toFloat()
                    },
                )
            }
    }
}


@Composable
private fun AdaptiveAvailabilityStep(
    answers:
        MutableMap<String, Boolean>,
    de: Boolean,
) {

    AdaptiveScrollableStep {

        Text(
            if (de)
                "Auf welche Verkehrsmittel hast du im Alltag strukturell Zugang?"
            else
                "Which transport modes do you structurally have access to in everyday life?",

            fontSize =
                14.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Text(
            if (de)
                "Dies beschreibt Zugang, nicht ob das Verkehrsmittel für einen bestimmten Weg gerade optimal ist."
            else
                "This describes access, not whether a mode is optimal for one specific trip.",

            fontSize =
                12.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        ADAPTIVE_MODES
            .forEach {
                mode ->

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    ) {

                        Text(
                            adaptiveModeLabel(
                                mode,
                                de,
                            ),

                            fontWeight =
                                FontWeight.Medium,
                        )

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                ),
                        ) {

                            if (
                                answers[
                                    mode
                                ] == true
                            ) {

                                Button(
                                    onClick = {
                                        answers[
                                            mode
                                        ] = true
                                    },
                                ) {

                                    Text(
                                        if (de)
                                            "Verfügbar"
                                        else
                                            "Available"
                                    )
                                }

                            } else {

                                OutlinedButton(
                                    onClick = {
                                        answers[
                                            mode
                                        ] = true
                                    },
                                ) {

                                    Text(
                                        if (de)
                                            "Verfügbar"
                                        else
                                            "Available"
                                    )
                                }
                            }


                            if (
                                answers[
                                    mode
                                ] == false
                            ) {

                                Button(
                                    onClick = {
                                        answers[
                                            mode
                                        ] = false
                                    },
                                ) {

                                    Text(
                                        if (de)
                                            "Nicht verfügbar"
                                        else
                                            "Not available"
                                    )
                                }

                            } else {

                                OutlinedButton(
                                    onClick = {
                                        answers[
                                            mode
                                        ] = false
                                    },
                                ) {

                                    Text(
                                        if (de)
                                            "Nicht verfügbar"
                                        else
                                            "Not available"
                                    )
                                }
                            }
                        }
                    }
                }
            }

        if (
            ADAPTIVE_MODES.all {
                it in answers
            } &&
            ADAPTIVE_MODES.none {
                answers[
                    it
                ] == true
            }
        ) {

            Text(
                if (de)
                    "Mindestens ein Verkehrsmittel muss verfügbar sein."
                else
                    "At least one transport mode must be available.",

                color =
                    MaterialTheme
                        .colorScheme
                        .error,

                fontSize =
                    12.sp,
            )
        }
    }
}


@Composable
private fun AdaptiveValenceStep(
    answers:
        MutableMap<String, Float>,
    de: Boolean,
) {

    AdaptiveScrollableStep {

        Text(
            if (de)
                "Wie positiv oder negativ bewertest du diese Verkehrsmittel im Allgemeinen?"
            else
                "How positively or negatively do you evaluate these transport modes in general?",

            fontSize =
                14.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Text(
            if (de)
                "1 = sehr negativ · 4 = neutral · 7 = sehr positiv"
            else
                "1 = very negative · 4 = neutral · 7 = very positive",

            fontSize =
                12.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        ADAPTIVE_MODES
            .forEach {
                mode ->

                AdaptiveRatingCard(
                    title =
                        adaptiveModeLabel(
                            mode,
                            de,
                        ),

                    value =
                        answers[
                            mode
                        ]?.toInt(),

                    onSelect = {
                        value ->

                        answers[
                            mode
                        ] =
                            value.toFloat()
                    },
                )
            }
    }
}


@Composable
private fun AdaptiveBeliefStep(
    questions:
        List<AdaptiveOnboardingQuestion>,
    answers:
        MutableMap<String, Int>,
    de: Boolean,
) {

    AdaptiveScrollableStep {

        Text(
            if (de)
                "Vier kurze Kalibrierungsfragen"
            else
                "Four short calibration questions",

            fontWeight =
                FontWeight.SemiBold,

            fontSize =
                17.sp,
        )

        Text(
            if (de)
                "Diese vier Fragen stammen aus der eingefrorenen ACP-v1-Kalibrierung. Sie werden nicht aus deinen aktuellen Antworten ausgewählt."
            else
                "These four items come from the frozen ACP-v1 calibration. They are not selected from your current answers.",

            fontSize =
                12.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Text(
            if (de)
                "1 = wirkt stark dagegen · 4 = neutral · 7 = unterstützt stark"
            else
                "1 = strongly works against it · 4 = neutral · 7 = strongly supports it",

            fontSize =
                12.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        questions.forEach {
            question ->

            AdaptiveRatingCard(
                title =
                    if (de)
                        "Wie stark unterstützt ${adaptiveModeLabel(question.mode, true)} ${adaptiveNeedLabel(question.need, true)}?"
                    else
                        "How strongly does ${adaptiveModeLabel(question.mode, false)} support ${adaptiveNeedLabel(question.need, false)}?",

                value =
                    answers[
                        question.cellId
                    ],

                onSelect = {
                    value ->

                    answers[
                        question.cellId
                    ] =
                        value
                },
            )
        }
    }
}


@Composable
private fun AdaptiveToleranceStep(
    answers:
        MutableMap<String, Int>,
    de: Boolean,
) {

    AdaptiveScrollableStep {

        Text(
            if (de)
                "Wie tolerant bist du gegenüber diesen Bedingungen beim Reisen?"
            else
                "How tolerant are you of these conditions while travelling?",

            fontSize =
                14.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Text(
            if (de)
                "1 = sehr geringe Toleranz · 7 = sehr hohe Toleranz"
            else
                "1 = very low tolerance · 7 = very high tolerance",

            fontSize =
                12.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        ENVIRONMENTAL_TOLERANCE_KEYS
            .forEach {
                key ->

                AdaptiveRatingCard(
                    title =
                        adaptiveToleranceLabel(
                            key,
                            de,
                        ),

                    value =
                        answers[
                            key
                        ],

                    onSelect = {
                        value ->

                        answers[
                            key
                        ] =
                            value
                    },
                )
            }
    }
}


@Composable
private fun AdaptiveResultStep(
    de: Boolean,
    ready: Boolean,
    onContinue: () -> Unit,
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    24.dp
                ),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center,
    ) {

        Text(
            "🪪",
            fontSize =
                58.sp,
        )

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        Text(
            if (de)
                "Dein Cognitive Passport ist bereit"
            else
                "Your Cognitive Passport is ready",

            fontSize =
                23.sp,

            fontWeight =
                FontWeight.Bold,

            textAlign =
                TextAlign.Center,
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            if (de)
                "11 Bedürfnisse, 4 Bewertungen und 4 Need–Mode-Beziehungen wurden direkt gemessen. Die übrigen 40 Beziehungen sind explizit als modellgeschätzt gekennzeichnet."
            else
                "11 needs, 4 evaluations and 4 need–mode relationships were measured directly. The remaining 40 relationships are explicitly marked as model-estimated.",

            textAlign =
                TextAlign.Center,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,

            lineHeight =
                20.sp,
        )

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        Button(
            onClick =
                onContinue,

            enabled =
                ready,

            modifier =
                Modifier.fillMaxWidth(),
        ) {

            Text(
                if (de)
                    "Zur App"
                else
                    "Continue to app"
            )
        }
    }
}


@Composable
private fun AdaptiveScrollableStep(
    content:
        @Composable ColumnScope.() -> Unit,
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    16.dp
                ),

        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            ),

        content =
            content,
    )

    Spacer(
        Modifier.height(
            80.dp
        )
    )
}


@Composable
private fun AdaptiveRatingCard(
    title: String,
    value: Int?,
    onSelect:
        (Int) -> Unit,
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),
    ) {

        Column(
            modifier =
                Modifier.padding(
                    12.dp
                )
        ) {

            Text(
                title,

                fontWeight =
                    FontWeight.Medium,

                fontSize =
                    14.sp,
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    ),
            ) {

                (1..7)
                    .forEach {
                        rating ->

                        if (
                            value == rating
                        ) {

                            Button(
                                onClick = {
                                    onSelect(
                                        rating
                                    )
                                },

                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),

                                contentPadding =
                                    PaddingValues(
                                        horizontal =
                                            0.dp
                                    ),
                            ) {

                                Text(
                                    rating
                                        .toString()
                                )
                            }

                        } else {

                            OutlinedButton(
                                onClick = {
                                    onSelect(
                                        rating
                                    )
                                },

                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),

                                contentPadding =
                                    PaddingValues(
                                        horizontal =
                                            0.dp
                                    ),
                            ) {

                                Text(
                                    rating
                                        .toString()
                                )
                            }
                        }
                    }
            }
        }
    }
}


private fun adaptiveStepTitle(
    step: Int,
    de: Boolean,
): String =
    when (
        step
    ) {

        0 ->
            if (de)
                "Cognitive Passport"
            else
                "Cognitive Passport"

        1 ->
            if (de)
                "Bedürfnisse"
            else
                "Needs"

        2 ->
            if (de)
                "Verfügbarkeit"
            else
                "Availability"

        3 ->
            if (de)
                "Bewertungen"
            else
                "Evaluations"

        4 ->
            if (de)
                "Kalibrierung"
            else
                "Calibration"

        5 ->
            if (de)
                "Kontexttoleranz"
            else
                "Context tolerance"

        6 ->
            if (de)
                "Bereit"
            else
                "Ready"

        else ->
            ""
    }


private fun adaptiveModeLabel(
    mode: String,
    de: Boolean,
): String =
    when (
        mode
    ) {

        "car" ->
            if (de)
                "Auto"
            else
                "car"

        "bike" ->
            if (de)
                "Fahrrad"
            else
                "cycling"

        "pt" ->
            if (de)
                "ÖPNV"
            else
                "public transport"

        "walk" ->
            if (de)
                "Zufußgehen"
            else
                "walking"

        else ->
            mode
    }


private fun adaptiveNeedLabel(
    need: String,
    de: Boolean,
): String =
    when (
        need
    ) {

        "pro_env" ->
            if (de)
                "Umweltfreundlichkeit"
            else
                "eco-friendliness"

        "physical" ->
            if (de)
                "körperliche Aktivität"
            else
                "physical activity"

        "privacy" ->
            if (de)
                "Privatsphäre und Platz"
            else
                "privacy and space"

        "autonomy" ->
            if (de)
                "Flexibilität und Autonomie"
            else
                "flexibility and autonomy"

        "cost" ->
            if (de)
                "Kosten"
            else
                "cost"

        "speed" ->
            if (de)
                "Zeitersparnis"
            else
                "time saving"

        "safety_accident" ->
            if (de)
                "Verkehrssicherheit"
            else
                "traffic safety"

        "safety_crime" ->
            if (de)
                "persönliche Sicherheit"
            else
                "personal security"

        "comfort" ->
            if (de)
                "Komfort"
            else
                "comfort"

        "reliable" ->
            if (de)
                "Zuverlässigkeit"
            else
                "reliability"

        "health_infection" ->
            if (de)
                "Gesundheitsschutz"
            else
                "health protection"

        else ->
            need
    }


private fun adaptiveToleranceLabel(
    key: String,
    de: Boolean,
): String =
    when (
        key
    ) {

        "rain" ->
            if (de)
                "Regen"
            else
                "Rain"

        "crowding" ->
            if (de)
                "Gedränge"
            else
                "Crowding"

        "darkness" ->
            if (de)
                "Dunkelheit"
            else
                "Darkness"

        "traffic" ->
            if (de)
                "Verkehrsbelastung"
            else
                "Traffic"

        "temperature" ->
            if (de)
                "Extreme Temperaturen"
            else
                "Extreme temperatures"

        else ->
            key
    }
