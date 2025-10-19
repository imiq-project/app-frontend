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

// Question Types matching Python QuestionType enum
enum class QuestionType {
    SCALE,   // 1-5 rating scale
    CHOICE   // Multiple choice (single answer)
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

// Question data class matching Python Question
@Immutable
data class ProfileQuestion(
    val id: String,
    val question: String,
    val type: QuestionType,
    val options: List<String>,
    val weights: Map<ProfileType, Int>? = null,      // For scale questions
    val scoring: Map<Int, Map<ProfileType, Int>>? = null  // For choice questions
)

// Classification Result matching Python ClassificationResult
data class ClassificationResult(
    val profileType: ProfileType,
    val profile: MobilityProfile,
    val scores: Map<ProfileType, Float>,
    val confidence: Float,  // 0-100
    val explanations: List<String>
)

// Contribution data for explanations
data class Contribution(
    val question: String,
    val answer: String,
    val profile: ProfileType,
    val points: Float
)