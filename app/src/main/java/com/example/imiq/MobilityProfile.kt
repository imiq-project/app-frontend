package com.example.imiq

import androidx.compose.runtime.Immutable

// Profile Types matching Python ProfileType enum
enum class ProfileType(val value: String) {
    ECO_WARRIOR("eco_warrior"),
    COMFORT_SEEKER("comfort_seeker"),
    TIME_OPTIMIZER("time_optimizer"),
    BUDGET_CONSCIOUS("budget_conscious"),
    FLEXIBLE_PRAGMATIST("flexible_pragmatist")
}

// Profile data class matching Python Profile
@Immutable
data class MobilityProfile(
    val profileType: ProfileType,
    val name: String,
    val description: String,
    val detailedDescription: String,
    val recommendations: List<String>,
    val icon: String,
    val characteristics: List<String>
)

// Classification Result matching Python ClassificationResult
data class ClassificationResult(
    val profileType: ProfileType,
    val profile: MobilityProfile,
    val scores: Map<ProfileType, Float>,
    val confidence: Float,  // 0-100
    val explanations: List<String>
)