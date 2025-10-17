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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ProfileQuestion(
    val id: Int,
    val emoji: String,
    val friendlyIntro: String,
    val question: String,
    val options: List<String>
)

enum class OnboardingStep {
    WELCOME,
    WHY_ASKING,
    QUESTIONS,
    CREATING_PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileComplete: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedAnswers by remember { mutableStateOf(mapOf<Int, String>()) }
    var isTransitioning by remember { mutableStateOf(false) }

    val questions = listOf(
        ProfileQuestion(
            id = 0,
            emoji = "🎂",
            friendlyIntro = "First things first...",
            question = "How old are you?",
            options = listOf("18-20", "21-25", "26-30", "31-35", "36+")
        ),
        ProfileQuestion(
            id = 1,
            emoji = "⭐",
            friendlyIntro = "Now, let's talk priorities...",
            question = "What matters most to you?",
            options = listOf(
                "🔓 Freedom",
                "🛡️ Safety",
                "💰 Costs",
                "🌍 Environment",
                "😌 Less Stress",
                "✨ Identity"
            )
        ),
        ProfileQuestion(
            id = 2,
            emoji = "🚀",
            friendlyIntro = "Getting around the city...",
            question = "How do you usually travel?",
            options = listOf("🚶 Walking", "🚌 Bus", "🚗 Car", "🚴 Bike", "🛴 Scooter", "🚊 Train")
        ),
        ProfileQuestion(
            id = 3,
            emoji = "🎭",
            friendlyIntro = "Last one, we promise!",
            question = "What's your personality?",
            options = listOf("🤫 Introvert", "🎉 Extrovert")
        )
    )

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
            onAnswerSelected = { questionId, answer ->
                selectedAnswers = selectedAnswers + (questionId to answer)
            },
            onTransitioningChange = { isTransitioning = it },
            onQuestionsComplete = { currentStep = OnboardingStep.CREATING_PROFILE },
            onBackClick = {
                if (currentQuestionIndex > 0) {
                    currentQuestionIndex--
                } else {
                    currentStep = OnboardingStep.WHY_ASKING
                }
            }
        )
        OnboardingStep.CREATING_PROFILE -> CreatingProfileScreen(
            onComplete = onProfileComplete
        )
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
                        title = "Ask a few questions",
                        description = "Just 4 quick ones about you",
                        delay = 100L
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FeaturePoint(
                        emoji = "🧠",
                        title = "Understand your style",
                        description = "Match you with similar travelers",
                        delay = 200L
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FeaturePoint(
                        emoji = "✨",
                        title = "Suggest smart routes",
                        description = "Perfect transport options for you",
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
    selectedAnswers: Map<Int, String>,
    isTransitioning: Boolean,
    onQuestionIndexChange: (Int) -> Unit,
    onAnswerSelected: (Int, String) -> Unit,
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
                        selectedAnswer = selectedAnswers[question.id],
                        onAnswerSelected = { answer ->
                            onAnswerSelected(question.id, answer)
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
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit,
    isTransitioning: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val scale by animateFloatAsState(
            targetValue = if (selectedAnswer != null) 1.15f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "emoji_scale"
        )

        Text(
            text = question.emoji,
            fontSize = 80.sp,
            modifier = Modifier.scale(scale)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = question.friendlyIntro,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = question.question,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        question.options.forEachIndexed { index, option ->
            AnimatedOptionButton(
                text = option,
                isSelected = selectedAnswer == option,
                onClick = { if (!isTransitioning) onAnswerSelected(option) },
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
                    fontSize = 17.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF7C4DFF) else Color.Black.copy(alpha = 0.8f)
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
        "Finding your tribe" to "👥",
        "Personalizing routes" to "🗺️",
        "Almost ready" to "✨"
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