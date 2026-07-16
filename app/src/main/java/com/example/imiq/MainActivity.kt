package com.example.imiq

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.imiq.ui.theme.IMIQTheme

class MainActivity : ComponentActivity() {
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        TokenManager.init(applicationContext)
        PassportStore.init(applicationContext)
        LanguageState.init()
        setContent {
            // Reading LanguageState.current here subscribes setContent to it, so
            // toggling the language in Settings recomposes the whole app instantly.
            val strings = if (LanguageState.current == AppLanguage.DE) DeStrings else EnStrings
            IMIQTheme {
                CompositionLocalProvider(LocalStrings provides strings) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var showSplash by remember { mutableStateOf(true) }
                        if (showSplash) {
                            SplashScreen(onDone = { showSplash = false })
                        } else {
                            ImiqApp()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ImiqApp() {
    // Determine start screen based on saved state
    val startScreen = remember {
        when {
            PassportStore.hasPassport() -> "home"
            TokenManager.isLoggedIn() -> "profile"
            else -> "signup"
        }
    }

    var currentScreen by remember { mutableStateOf(startScreen) }
    var previousScreen by remember { mutableStateOf("") }

    // Safety net: any time we end up on main_menu but no passport exists,
    // redirect to the profile setup (which now generates the passport).
    LaunchedEffect(currentScreen) {
        if (currentScreen == "home" && !PassportStore.hasPassport()) {
            previousScreen = "home"
            currentScreen = "profile"
        }
    }
    var userProfile by remember { mutableStateOf<ClassificationResult?>(null) }
    var tripDestination by remember { mutableStateOf(DemoRoutingData.destination) }
    var userName by remember { mutableStateOf(TokenManager.getUserName() ?: "") }

    // Restore profile from saved data if returning user
    LaunchedEffect(Unit) {
        if (TokenManager.isProfileCompleted() && userProfile == null) {
            val profileTypeStr = TokenManager.getProfileType()
            val profileType = ProfileType.values().find { it.value == profileTypeStr }
            if (profileType != null) {
                val classifier = MobilityClassifier()
                val profile = classifier.getProfile(profileType)
                if (profile != null) {
                    userProfile = ClassificationResult(
                        profileType = profileType,
                        profile = profile,
                        scores = ProfileType.values().associate { it to 0f },
                        confidence = 85f,
                        explanations = listOf("Profile restored from saved data.")
                    )
                }
            }
            userName = TokenManager.getUserName() ?: ""
        }
    }

    BackHandler(enabled = currentScreen != "signup" && currentScreen != "home") {
        val target = when (currentScreen) {
            "results" -> "home"
            "settings" -> "home"
            "passport_detail" -> "home"
            "profile" -> if (TokenManager.isProfileCompleted()) "home" else "signup"
            else -> currentScreen
        }
        previousScreen = currentScreen
        currentScreen = target
    }

    fun navigateTo(screen: String) {
        previousScreen = currentScreen
        currentScreen = screen
    }

    fun navigateBack() {
        val target = when (currentScreen) {
            "home" -> "home"
            "results" -> "home"
            "settings" -> "home"
            "passport_detail" -> "home"
            "profile" -> if (TokenManager.isProfileCompleted()) "home" else "signup"
            else -> currentScreen
        }
        previousScreen = currentScreen
        currentScreen = target
    }

    val screenOrder = listOf(
        "signup", "profile", "home", "results", "settings", "passport_detail"
    )
    val isForward = screenOrder.indexOf(currentScreen) >= screenOrder.indexOf(previousScreen)

    AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (isForward) {
                    (slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(350)))
                        .togetherWith(slideOutHorizontally(tween(350)) { -it / 3 } + fadeOut(tween(250)))
                } else {
                    (slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350)))
                        .togetherWith(slideOutHorizontally(tween(350)) { it / 3 } + fadeOut(tween(250)))
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                "signup" -> SignUpScreen(
                    onSignUpComplete = {
                        navigateTo("profile")
                    }
                )

                "profile" -> ProfileSetupScreen(
                    onProfileComplete = { result ->
                        userProfile = result
                        userName = TokenManager.getUserName() ?: ""
                        navigateTo("home")
                    },
                    onBackClick = { navigateBack() }
                )

                "home" -> MobilityHomeScreen(
                    userProfile = userProfile,
                    userName = userName,
                    onPlanTrip = { dest ->
                        tripDestination = dest
                        navigateTo("results")
                    },
                    onOpenProfile = { navigateTo("passport_detail") },
                    onOpenSettings = { navigateTo("settings") }
                )

                "passport_detail" -> PassportDetailScreen(
                    onBack = { navigateBack() }
                )

                "results" -> RouteResultsScreen(
                    destination = tripDestination,
                    userProfile = userProfile,
                    onBackClick = { navigateBack() }
                )

                "settings" -> SettingsScreen(
                    onRecreateProfile = {
                        TokenManager.clearProfile()
                        PassportStore.clear()
                        userProfile = null
                        userName = ""
                        navigateTo("profile")
                    },
                    onBackClick = { navigateBack() }
                )

                else -> SignUpScreen(
                    onSignUpComplete = {
                        navigateTo("profile")
                    }
                )
            }
        }
}
