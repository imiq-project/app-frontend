package com.example.imiq

import kotlin.math.min
class MobilityClassifier {
    private val profiles: Map<ProfileType, MobilityProfile> = initializeProfiles()
    private val questions: List<ProfileQuestion> = initializeQuestions()

    private fun initializeProfiles(): Map<ProfileType, MobilityProfile> {
        return mapOf(
            ProfileType.ECO_WARRIOR to MobilityProfile(
                profileType = ProfileType.ECO_WARRIOR,
                name = "Eco-Warrior 🌱",
                description = "Environmental sustainability is your top priority.",
                detailedDescription = "You are deeply committed to reducing your carbon footprint. " +
                        "You prefer public transportation, cycling, and walking whenever possible. " +
                        "You're willing to sacrifice some convenience for environmental benefits. " +
                        "Car ownership is minimal or avoided entirely.",
                recommendations = listOf(
                    "Bicycle - Zero emissions, healthy",
                    "Tram - Electric, efficient",
                    "Bus - Shared transportation",
                    "E-bike - Electric assist for longer distances",
                    "Walking - For short trips"
                ),
                icon = "🌱",
                characteristics = listOf(
                    "High environmental awareness",
                    "Prefers active/public transportation",
                    "Cost-conscious",
                    "Avoids private cars",
                    "Community-oriented"
                )
            ),

            ProfileType.COMFORT_SEEKER to MobilityProfile(
                profileType = ProfileType.COMFORT_SEEKER,
                name = "Comfort Seeker 💺",
                description = "Comfort and convenience drive your transportation choices.",
                detailedDescription = "You value personal space, climate control, and stress-free travel. " +
                        "You're willing to pay more for comfort and convenience. " +
                        "Crowded public transport is not appealing. " +
                        "You prefer door-to-door service without weather concerns.",
                recommendations = listOf(
                    "Private Car - Maximum comfort and control",
                    "Electric Car - Comfort + eco-friendly",
                    "Carsharing - Flexibility without ownership",
                    "Taxi/Rideshare - Convenient on-demand",
                    "Premium train/bus services"
                ),
                icon = "💺",
                characteristics = listOf(
                    "Values personal space",
                    "Dislikes crowds",
                    "Willing to pay for comfort",
                    "Weather-sensitive",
                    "Privacy-focused"
                )
            ),

            ProfileType.TIME_OPTIMIZER to MobilityProfile(
                profileType = ProfileType.TIME_OPTIMIZER,
                name = "Time Optimizer ⚡",
                description = "Speed and efficiency are what matter most.",
                detailedDescription = "Your time is valuable, and you optimize every trip for speed. " +
                        "You choose the fastest route regardless of cost or mode. " +
                        "You're comfortable with multi-modal combinations if they save time. " +
                        "Waiting and delays frustrate you.",
                recommendations = listOf(
                    "Express Train - Fast intercity",
                    "E-scooter - Quick for short distances",
                    "Motorcycle - Fast through traffic",
                    "Express Bus - Dedicated lanes",
                    "Carsharing - Flexibility for speed"
                ),
                icon = "⚡",
                characteristics = listOf(
                    "Time is priority #1",
                    "Route-optimizer",
                    "Dislikes waiting",
                    "Uses fastest available mode",
                    "Tech-savvy with apps"
                )
            ),

            ProfileType.BUDGET_CONSCIOUS to MobilityProfile(
                profileType = ProfileType.BUDGET_CONSCIOUS,
                name = "Budget Conscious 💰",
                description = "You prioritize cost-effectiveness in all travel decisions.",
                detailedDescription = "You carefully consider transportation costs and seek the best value. " +
                        "You're willing to invest more time or effort to save money. " +
                        "Monthly passes, bike ownership, and walking are attractive options. " +
                        "You avoid expensive private transportation when possible.",
                recommendations = listOf(
                    "Bus - Affordable public transport",
                    "Bicycle - Low cost after initial investment",
                    "Walking - Free and healthy",
                    "Tram - Cost-effective for daily commute",
                    "Ridepooling - Shared cost savings"
                ),
                icon = "💰",
                characteristics = listOf(
                    "Cost-conscious decision maker",
                    "Plans ahead for savings",
                    "Uses passes/subscriptions",
                    "Prefers owned modes (bike)",
                    "Value-focused"
                )
            ),

            ProfileType.FLEXIBLE_PRAGMATIST to MobilityProfile(
                profileType = ProfileType.FLEXIBLE_PRAGMATIST,
                name = "Flexible Pragmatist 🔄",
                description = "You adapt your transportation to fit each situation.",
                detailedDescription = "You don't have a single preferred mode - you choose based on context. " +
                        "Weather, distance, time of day, and purpose all factor into your decisions. " +
                        "You're comfortable with multi-modal trips and trying new options. " +
                        "You balance all factors: cost, time, comfort, and environment.",
                recommendations = listOf(
                    "Multi-modal: Bus + Bike combinations",
                    "Carsharing - Use when needed",
                    "E-scooter - Flexible short trips",
                    "Walking - When convenient",
                    "Train - For longer distances"
                ),
                icon = "🔄",
                characteristics = listOf(
                    "Context-dependent choices",
                    "Open to all modes",
                    "Balanced priorities",
                    "Adapts to situations",
                    "Multi-modal user"
                )
            )
        )
    }

    private fun initializeQuestions(): List<ProfileQuestion> {
        return listOf(
            // Question 1: Environmental values
            ProfileQuestion(
                id = "environment",
                question = "How important is environmental sustainability to you?",
                type = QuestionType.SCALE,
                options = listOf(
                    "1 - Not important",
                    "2 - Somewhat important",
                    "3 - Important",
                    "4 - Very important",
                    "5 - Extremely important"
                ),
                weights = mapOf(
                    ProfileType.ECO_WARRIOR to 5,
                    ProfileType.BUDGET_CONSCIOUS to 2,
                    ProfileType.FLEXIBLE_PRAGMATIST to 1,
                    ProfileType.COMFORT_SEEKER to -1,
                    ProfileType.TIME_OPTIMIZER to 0
                )
            ),

            // Question 2: Budget sensitivity
            ProfileQuestion(
                id = "cost",
                question = "How much do you want to spend on daily transportation?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "Minimize costs at all costs",
                    "Budget-friendly options preferred",
                    "Moderate spending is fine",
                    "Price is not a concern"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.BUDGET_CONSCIOUS to 10, ProfileType.ECO_WARRIOR to 5, ProfileType.TIME_OPTIMIZER to -3),
                    1 to mapOf(ProfileType.BUDGET_CONSCIOUS to 7, ProfileType.FLEXIBLE_PRAGMATIST to 3),
                    2 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 5, ProfileType.TIME_OPTIMIZER to 3, ProfileType.COMFORT_SEEKER to 2),
                    3 to mapOf(ProfileType.COMFORT_SEEKER to 8, ProfileType.TIME_OPTIMIZER to 5)
                )
            ),

            // Question 3: Time priority
            ProfileQuestion(
                id = "time_priority",
                question = "How do you value your travel time?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "Speed is everything - fastest route always",
                    "Prefer fast, but willing to compromise",
                    "Time is flexible, other factors matter more"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.TIME_OPTIMIZER to 10, ProfileType.COMFORT_SEEKER to 3, ProfileType.ECO_WARRIOR to -2),
                    1 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 7, ProfileType.TIME_OPTIMIZER to 4, ProfileType.COMFORT_SEEKER to 2),
                    2 to mapOf(ProfileType.ECO_WARRIOR to 5, ProfileType.BUDGET_CONSCIOUS to 5, ProfileType.FLEXIBLE_PRAGMATIST to 3)
                )
            ),

            // Question 4: Comfort importance
            ProfileQuestion(
                id = "comfort",
                question = "How important is comfort during your commute?",
                type = QuestionType.SCALE,
                options = listOf(
                    "1 - Not important",
                    "2 - Somewhat important",
                    "3 - Important",
                    "4 - Very important",
                    "5 - Essential"
                ),
                weights = mapOf(
                    ProfileType.COMFORT_SEEKER to 5,
                    ProfileType.FLEXIBLE_PRAGMATIST to 2,
                    ProfileType.TIME_OPTIMIZER to 1,
                    ProfileType.BUDGET_CONSCIOUS to -1,
                    ProfileType.ECO_WARRIOR to 0
                )
            ),

            // Question 5: Car relationship
            ProfileQuestion(
                id = "car_ownership",
                question = "What's your relationship with private car ownership?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "I avoid cars - prefer alternatives",
                    "No car, but use occasionally (sharing/rental)",
                    "Own a car, use it regularly",
                    "Car is my primary transportation"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.ECO_WARRIOR to 10, ProfileType.BUDGET_CONSCIOUS to 5, ProfileType.COMFORT_SEEKER to -5),
                    1 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 8, ProfileType.BUDGET_CONSCIOUS to 3, ProfileType.ECO_WARRIOR to 3),
                    2 to mapOf(ProfileType.COMFORT_SEEKER to 7, ProfileType.FLEXIBLE_PRAGMATIST to 5, ProfileType.TIME_OPTIMIZER to 3),
                    3 to mapOf(ProfileType.COMFORT_SEEKER to 10, ProfileType.TIME_OPTIMIZER to 5, ProfileType.ECO_WARRIOR to -5)
                )
            ),

            // Question 6: Crowding tolerance
            ProfileQuestion(
                id = "crowding",
                question = "How do you feel about crowded public transportation?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "No problem - part of urban life",
                    "Tolerable during peak hours",
                    "Prefer to avoid if possible",
                    "Cannot stand crowds"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.ECO_WARRIOR to 7, ProfileType.BUDGET_CONSCIOUS to 7, ProfileType.FLEXIBLE_PRAGMATIST to 3),
                    1 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 6, ProfileType.BUDGET_CONSCIOUS to 4, ProfileType.ECO_WARRIOR to 3),
                    2 to mapOf(ProfileType.COMFORT_SEEKER to 6, ProfileType.TIME_OPTIMIZER to 4, ProfileType.FLEXIBLE_PRAGMATIST to 2),
                    3 to mapOf(ProfileType.COMFORT_SEEKER to 10, ProfileType.ECO_WARRIOR to -3, ProfileType.TIME_OPTIMIZER to 3)
                )
            ),

            // Question 7: Physical activity preference
            ProfileQuestion(
                id = "physical_activity",
                question = "Do you want physical activity as part of your commute?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "Yes! I love cycling/walking",
                    "Sometimes, weather permitting",
                    "Prefer to avoid physical exertion"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.ECO_WARRIOR to 8, ProfileType.BUDGET_CONSCIOUS to 6, ProfileType.TIME_OPTIMIZER to -2),
                    1 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 7, ProfileType.ECO_WARRIOR to 3, ProfileType.BUDGET_CONSCIOUS to 3),
                    2 to mapOf(ProfileType.COMFORT_SEEKER to 8, ProfileType.TIME_OPTIMIZER to 5)
                )
            ),

            // Question 8: Weather sensitivity
            ProfileQuestion(
                id = "weather",
                question = "How much does weather affect your transportation choice?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "Not much - I adapt to any weather",
                    "Somewhat - prefer covered options when bad",
                    "Significantly - need weather protection"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.ECO_WARRIOR to 5, ProfileType.FLEXIBLE_PRAGMATIST to 5, ProfileType.BUDGET_CONSCIOUS to 3),
                    1 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 7, ProfileType.TIME_OPTIMIZER to 3, ProfileType.ECO_WARRIOR to 2),
                    2 to mapOf(ProfileType.COMFORT_SEEKER to 8, ProfileType.TIME_OPTIMIZER to 4)
                )
            ),

            // Question 9: Flexibility vs routine
            ProfileQuestion(
                id = "flexibility",
                question = "Do you prefer the same route/mode daily, or variety?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "Routine - same mode every day",
                    "Mix it up based on circumstances",
                    "Whatever works best that day"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.ECO_WARRIOR to 5, ProfileType.BUDGET_CONSCIOUS to 5, ProfileType.COMFORT_SEEKER to 5),
                    1 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 10, ProfileType.TIME_OPTIMIZER to 3),
                    2 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 8, ProfileType.TIME_OPTIMIZER to 6)
                )
            ),

            // Question 10: Core values (HIGHEST WEIGHT)
            ProfileQuestion(
                id = "values",
                question = "What matters MOST in your mobility choices?",
                type = QuestionType.CHOICE,
                options = listOf(
                    "Reducing my carbon footprint",
                    "Saving money",
                    "Saving time",
                    "Personal comfort and convenience",
                    "Flexibility and having options"
                ),
                scoring = mapOf(
                    0 to mapOf(ProfileType.ECO_WARRIOR to 15),
                    1 to mapOf(ProfileType.BUDGET_CONSCIOUS to 15),
                    2 to mapOf(ProfileType.TIME_OPTIMIZER to 15),
                    3 to mapOf(ProfileType.COMFORT_SEEKER to 15),
                    4 to mapOf(ProfileType.FLEXIBLE_PRAGMATIST to 15)
                )
            )
        )
    }

    fun classify(answers: Map<String, Int>): ClassificationResult {
        // Initialize scores for all profiles
        val scores = mutableMapOf<ProfileType, Float>()
        ProfileType.values().forEach { scores[it] = 0f }

        // Track contributions for explanation
        val contributions = mutableListOf<Contribution>()

        // Calculate scores
        for (question in questions) {
            val answerIdx = answers[question.id] ?: continue

            when (question.type) {
                QuestionType.SCALE -> {
                    // Scale questions: answer_idx (0-4) maps to scale value (1-5)
                    val scaleValue = answerIdx + 1

                    question.weights?.forEach { (profileType, weight) ->
                        val contribution = scaleValue * weight
                        scores[profileType] = (scores[profileType] ?: 0f) + contribution

                        // Track significant contributions
                        if (kotlin.math.abs(contribution) >= 5) {
                            contributions.add(
                                Contribution(
                                    question = question.question,
                                    answer = question.options[answerIdx],
                                    profile = profileType,
                                    points = contribution.toFloat()
                                )
                            )
                        }
                    }
                }

                QuestionType.CHOICE -> {
                    // Choice questions: direct scoring
                    question.scoring?.get(answerIdx)?.forEach { (profileType, points) ->
                        scores[profileType] = (scores[profileType] ?: 0f) + points

                        // Track significant contributions
                        if (kotlin.math.abs(points) >= 5) {
                            contributions.add(
                                Contribution(
                                    question = question.question,
                                    answer = question.options[answerIdx],
                                    profile = profileType,
                                    points = points.toFloat()
                                )
                            )
                        }
                    }
                }
            }
        }

        // Find winning profile
        val sortedScores = scores.entries.sortedByDescending { it.value }
        val winningProfile = sortedScores[0].key
        val topScore = sortedScores[0].value
        val secondScore = if (sortedScores.size > 1) sortedScores[1].value else 0f

        // Calculate confidence (0-100%)
        val confidence = if (topScore > 0) {
            val margin = topScore - secondScore
            min(100f, (margin / topScore) * 100)
        } else {
            50f
        }

        // Generate explanations
        val explanations = generateExplanations(winningProfile, contributions, sortedScores)

        return ClassificationResult(
            profileType = winningProfile,
            profile = profiles[winningProfile]!!,
            scores = scores,
            confidence = confidence,
            explanations = explanations
        )
    }

    private fun generateExplanations(
        winningProfile: ProfileType,
        contributions: List<Contribution>,
        sortedScores: List<Map.Entry<ProfileType, Float>>
    ): List<String> {
        val explanations = mutableListOf<String>()

        // Filter contributions for winning profile and sort by points
        val winningContributions = contributions
            .filter { it.profile == winningProfile && it.points > 0 }
            .sortedByDescending { it.points }
            .take(3)

        // Top 3 reasons
        for (contrib in winningContributions) {
            explanations.add(
                "Your answer '${contrib.answer}' to '${contrib.question}' " +
                        "contributed +${contrib.points.toInt()} points"
            )
        }

        // Score comparison
        val top3Profiles = sortedScores.take(3)
        val scoreComparison = "Score comparison: " + top3Profiles.joinToString(", ") {
            "${profiles[it.key]?.name}: ${it.value.toInt()}"
        }
        explanations.add(scoreComparison)

        return explanations
    }

    fun getQuestions(): List<ProfileQuestion> = questions

    fun getProfile(profileType: ProfileType): MobilityProfile? = profiles[profileType]

    fun getAllProfiles(): Map<ProfileType, MobilityProfile> = profiles
}