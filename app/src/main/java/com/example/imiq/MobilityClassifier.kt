package com.example.imiq

class MobilityClassifier {
    private val profiles: Map<ProfileType, MobilityProfile> = initializeProfiles()

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

    fun getProfile(profileType: ProfileType): MobilityProfile? = profiles[profileType]
}