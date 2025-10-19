package com.example.imiq

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    WHY_ASKING,
    QUESTIONS,
    CREATING_PROFILE,
    PROFILE_RESULT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileComplete: (ClassificationResult) -> Unit = {},  // Changed: now passes result
    onBackClick: () -> Unit = {}
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedAnswers by remember { mutableStateOf(mapOf<String, Int>()) }
    var isTransitioning by remember { mutableStateOf(false) }
    var classificationResult by remember { mutableStateOf<ClassificationResult?>(null) }

    val classifier = remember { MobilityClassifier() }
    val questions = remember { classifier.getQuestions() }

    when (currentStep) {
        OnboardingStep.WELCOME -> WelcomeScreen(
            onContinue = { currentStep = OnboardingStep.WHY_ASKING },
            onBackClick = onBackClick
        )
        OnboardingStep.WHY_ASKING -> WhyAskingScreen(
            onContinue = { currentStep = OnboardingStep.QUESTIONS },
            onBackClick = { currentStep = OnboardingStep.WELCOME }
        )
        OnboardingStep.QUESTIONS -> QuestionsScreen(
            questions = questions,
            currentQuestionIndex = currentQuestionIndex,
            selectedAnswers = selectedAnswers,
            isTransitioning = isTransitioning,
            onQuestionIndexChange = { currentQuestionIndex = it },
            onAnswerSelected = { questionId, answerIndex ->
                selectedAnswers = selectedAnswers + (questionId to answerIndex)
            },
            onTransitioningChange = { isTransitioning = it },
            onQuestionsComplete = {
                // Classify the user
                classificationResult = classifier.classify(selectedAnswers)
                currentStep = OnboardingStep.CREATING_PROFILE
            },
            onBackClick = {
                if (currentQuestionIndex > 0) {
                    currentQuestionIndex--
                } else {
                    currentStep = OnboardingStep.WHY_ASKING
                }
            }
        )
        OnboardingStep.CREATING_PROFILE -> CreatingProfileScreen(
            onComplete = {
                currentStep = OnboardingStep.PROFILE_RESULT
            }
        )
        OnboardingStep.PROFILE_RESULT -> classificationResult?.let { result ->
            ProfileResultScreen(
                result = result,
                onContinue = {
                    onProfileComplete(result)  // Pass the result when continuing
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    onBackClick: () -> Unit
) {
    val darkPurple = Color(0xFF7C4DFF)
    val mediumPurple = Color(0xFF9575CD)
    val lightPurple = Color(0xFFE1BEE7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(darkPurple, mediumPurple, lightPurple)
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    var emojiScale by remember { mutableStateOf(0.5f) }
                    LaunchedEffect(Unit) {
                        delay(200)
                        emojiScale = 1f
                    }

                    val scale by animateFloatAsState(
                        targetValue = emojiScale,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "emoji"
                    )

                    Text(
                        text = "👋",
                        fontSize = 120.sp,
                        modifier = Modifier.scale(scale)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = "Hi there!",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Welcome to your personalized mobility companion",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                }

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 18.dp)
                ) {
                    Text(
                        text = "Continue",
                        color = darkPurple,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhyAskingScreen(
    onContinue: () -> Unit,
    onBackClick: () -> Unit
) {
    val darkPurple = Color(0xFF7C4DFF)
    val mediumPurple = Color(0xFF9575CD)
    val lightPurple = Color(0xFFE1BEE7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(darkPurple, mediumPurple, lightPurple)
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🎯",
                        fontSize = 90.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Here's what we'll do",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    FeaturePoint(
                        emoji = "🤔",
                        title = "Ask 10 quick questions",
                        description = "About your travel preferences and priorities",
                        delay = 100L
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FeaturePoint(
                        emoji = "🧠",
                        title = "Analyze your mobility style",
                        description = "Match you to one of 5 unique profiles",
                        delay = 200L
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FeaturePoint(
                        emoji = "✨",
                        title = "Personalized recommendations",
                        description = "Get transport options tailored to you",
                        delay = 300L
                    )
                }

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 18.dp)
                ) {
                    Text(
                        text = "Sounds good!",
                        color = darkPurple,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturePoint(
    emoji: String,
    title: String,
    description: String,
    delay: Long = 0L
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = emoji,
                    fontSize = 32.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C4DFF)
                    )
                    Text(
                        text = description,
                        fontSize = 13.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionsScreen(
    questions: List<ProfileQuestion>,
    currentQuestionIndex: Int,
    selectedAnswers: Map<String, Int>,
    isTransitioning: Boolean,
    onQuestionIndexChange: (Int) -> Unit,
    onAnswerSelected: (String, Int) -> Unit,
    onTransitioningChange: (Boolean) -> Unit,
    onQuestionsComplete: () -> Unit,
    onBackClick: () -> Unit
) {
    val darkPurple = Color(0xFF7C4DFF)
    val mediumPurple = Color(0xFF9575CD)
    val lightPurple = Color(0xFFE1BEE7)
    val coroutineScope = rememberCoroutineScope()
    val progress = (currentQuestionIndex + 1) / questions.size.toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Question ${currentQuestionIndex + 1} of ${questions.size}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkPurple
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(darkPurple, mediumPurple, lightPurple)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(40.dp))

                AnimatedContent(
                    targetState = currentQuestionIndex,
                    transitionSpec = {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn()).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + fadeOut()
                        )
                    },
                    label = "question_animation"
                ) { questionIndex ->
                    val question = questions[questionIndex]

                    QuestionCard(
                        question = question,
                        selectedAnswerIndex = selectedAnswers[question.id],
                        onAnswerSelected = { answerIndex ->
                            onAnswerSelected(question.id, answerIndex)
                            onTransitioningChange(true)
                            coroutineScope.launch {
                                delay(500)
                                if (questionIndex < questions.size - 1) {
                                    onQuestionIndexChange(questionIndex + 1)
                                } else {
                                    onQuestionsComplete()
                                }
                                onTransitioningChange(false)
                            }
                        },
                        isTransitioning = isTransitioning
                    )
                }
            }
        }
    }
}

@Composable
fun QuestionCard(
    question: ProfileQuestion,
    selectedAnswerIndex: Int?,
    onAnswerSelected: (Int) -> Unit,
    isTransitioning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Emoji based on question type
        val emoji = when (question.id) {
            "environment" -> "🌱"
            "cost" -> "💰"
            "time_priority" -> "⚡"
            "comfort" -> "💺"
            "car_ownership" -> "🚗"
            "crowding" -> "👥"
            "physical_activity" -> "🚴"
            "weather" -> "🌦️"
            "flexibility" -> "🔄"
            "values" -> "⭐"
            else -> "❓"
        }

        val scale by animateFloatAsState(
            targetValue = if (selectedAnswerIndex != null) 1.15f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "emoji_scale"
        )

        Text(
            text = emoji,
            fontSize = 80.sp,
            modifier = Modifier.scale(scale)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = question.question,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        question.options.forEachIndexed { index, option ->
            AnimatedOptionButton(
                text = option,
                isSelected = selectedAnswerIndex == index,
                onClick = { if (!isTransitioning) onAnswerSelected(index) },
                delay = index * 50L
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun AnimatedOptionButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    delay: Long = 0L
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn()
    ) {
        val scale by animateFloatAsState(
            targetValue = if (isSelected) 1.05f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "button_scale"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected)
                    Color.White
                else
                    Color.White.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 8.dp else 2.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF7C4DFF) else Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF4CAF50), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreatingProfileScreen(
    onComplete: () -> Unit
) {
    val darkPurple = Color(0xFF7C4DFF)
    val mediumPurple = Color(0xFF9575CD)
    val lightPurple = Color(0xFFE1BEE7)

    var currentStep by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }

    val steps = listOf(
        "Analyzing your preferences" to "🧠",
        "Calculating profile scores" to "📊",
        "Matching your style" to "🎯",
        "Preparing recommendations" to "✨"
    )

    LaunchedEffect(Unit) {
        // Animate through steps
        for (i in 0..3) {
            currentStep = i
            // Animate progress bar
            val targetProgress = (i + 1) / 4f
            while (progress < targetProgress) {
                delay(30)
                progress += 0.02f
            }
            delay(600)
        }
        delay(500)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(darkPurple, mediumPurple, lightPurple)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated emoji
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = tween(300)
                    )).togetherWith(
                        fadeOut(animationSpec = tween(200)) + scaleOut(
                            targetScale = 1.2f,
                            animationSpec = tween(200)
                        )
                    )
                },
                label = "emoji_animation"
            ) { step ->
                Text(
                    text = steps[step].second,
                    fontSize = 100.sp,
                    modifier = Modifier.scale(scale)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Progress steps
            steps.forEachIndexed { index, (stepText, _) ->
                AnimatedVisibility(
                    visible = index <= currentStep,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Checkmark or loading indicator
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (index < currentStep) Color.White else Color.White.copy(alpha = 0.3f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < currentStep) {
                                Text(
                                    text = "✓",
                                    color = darkPurple,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (index == currentStep) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = darkPurple,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        Text(
                            text = stepText,
                            fontSize = 16.sp,
                            color = if (index <= currentStep) Color.White else Color.White.copy(alpha = 0.5f),
                            fontWeight = if (index == currentStep) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Progress bar
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileResultScreen(
    result: ClassificationResult,
    onContinue: () -> Unit
) {
    val darkPurple = Color(0xFF7C4DFF)
    val mediumPurple = Color(0xFF9575CD)
    val lightPurple = Color(0xFFE1BEE7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(darkPurple, mediumPurple, lightPurple)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Profile Icon with animation
                var iconScale by remember { mutableStateOf(0f) }
                LaunchedEffect(Unit) {
                    delay(200)
                    iconScale = 1f
                }

                val scale by animateFloatAsState(
                    targetValue = iconScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "icon_scale"
                )

                Text(
                    text = result.profile.icon,
                    fontSize = 120.sp,
                    modifier = Modifier.scale(scale)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Profile Name
                Text(
                    text = "You're a",
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = result.profile.name,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Confidence badge
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "${result.confidence.toInt()}% match",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Description Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = result.profile.detailedDescription,
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.8f),
                            lineHeight = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Characteristics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "✨ Your Characteristics",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkPurple
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        result.profile.characteristics.forEach { characteristic ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 16.sp,
                                    color = darkPurple,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = characteristic,
                                    fontSize = 15.sp,
                                    color = Color.Black.copy(alpha = 0.8f),
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Recommendations
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "🎯 Recommended Transport",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkPurple
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        result.profile.recommendations.forEach { recommendation ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 16.sp,
                                    color = darkPurple,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = recommendation,
                                    fontSize = 15.sp,
                                    color = Color.Black.copy(alpha = 0.8f),
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Continue Button
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 18.dp)
                ) {
                    Text(
                        text = "Let's Go!",
                        color = darkPurple,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}