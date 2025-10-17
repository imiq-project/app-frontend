package com.example.imiq

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    // This will request location permissions from the user
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions result will be handled here
        // For now, we just request them
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request location permissions when app starts
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        TokenManager.init(applicationContext)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ImiqApp()
                }
            }
        }
    }
}

@Composable
fun ImiqApp() {
    var currentScreen by remember { mutableStateOf("signup") }

    // Handle Android back button
    BackHandler(enabled = currentScreen != "signup") {
        currentScreen = when (currentScreen) {
            "routing" -> "profile"
            "profile" -> "signup"
            else -> currentScreen
        }
    }

    // Navigate back function
    fun navigateBack() {
        currentScreen = when (currentScreen) {
            "routing" -> "profile"
            "profile" -> "signup"
            else -> currentScreen
        }
    }

    when (currentScreen) {
        "signup" -> SignUpScreen(
            onSignUpComplete = {
                currentScreen = "profile"
            }
        )
        "profile" -> ProfileSetupScreen(
            onProfileComplete = {
                currentScreen = "routing"
            },
            onBackClick = { navigateBack() }
        )
        "routing" -> RoutingScreen(
            onBackClick = { navigateBack() }
        )
        else -> RoutingScreen(
            onBackClick = { navigateBack() }
        )
    }
}