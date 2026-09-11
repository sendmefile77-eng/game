package com.sendmefile77.chronosphere.adult

internal object AdultCardRecipes {
    val all: List<AdultVisualRecipe> = listOf(
        card("card.dressed.baseline", AdultWardrobeState.DRESSED, "wardrobe.clothed", RigPlan.BASELINE),
        card("card.undressed.baseline", AdultWardrobeState.UNDRESSED, "wardrobe.undressed", RigPlan.BASELINE, extra = setOf("nude", "undressed")),
        card("card.dressed.hybrid", AdultWardrobeState.DRESSED, "wardrobe.clothed", RigPlan.HYBRID, hybrid = true, maxDiv = 0.85),
        card("card.undressed.hybrid", AdultWardrobeState.UNDRESSED, "wardrobe.undressed", RigPlan.HYBRID, extra = setOf("nude", "undressed", "hybrid"), hybrid = true, maxDiv = 0.85),
        card("card.undressed.quad", AdultWardrobeState.UNDRESSED, "wardrobe.undressed", RigPlan.DIVERGENT, extra = setOf("nude", "undressed", "divergent"), minA = 3, maxA = 6, noTail = false, minDiv = 0.2, maxDiv = 1.0),
        card("card.undressed.tailed", AdultWardrobeState.UNDRESSED, "wardrobe.undressed", RigPlan.DIVERGENT, extra = setOf("nude", "undressed", "tail"), needTail = true, noTail = false, minDiv = 0.15, maxDiv = 1.0),
        card("card.undressed.scaled", AdultWardrobeState.UNDRESSED, "wardrobe.undressed", RigPlan.DIVERGENT, extra = setOf("nude", "undressed", "scales"), covering = "scales", noTail = false, minDiv = 0.2, maxDiv = 1.0),
        card("card.dressed.scaled", AdultWardrobeState.DRESSED, "wardrobe.clothed", RigPlan.DIVERGENT, extra = setOf("scales"), covering = "scales", noTail = false, minDiv = 0.2, maxDiv = 1.0),
    )

    private fun card(
        id: String,
        state: AdultWardrobeState,
        wardrobe: String,
        plan: RigPlan,
        extra: Set<String> = emptySet(),
        covering: String = "",
        minA: Int = 2,
        maxA: Int = 2,
        needTail: Boolean = false,
        noTail: Boolean = true,
        minDiv: Double? = null,
        maxDiv: Double? = 0.44,
        hybrid: Boolean = false,
    ) = AdultVisualRecipe(
        id = id,
        eventCodes = setOf(AdultUndressPolicy.CARD_EVENT),
        sceneFamily = "character-card",
        rigLayout = if (plan == RigPlan.BASELINE) "solo-bust" else "${plan.name.lowercase()}-bust",
        poseKey = "pose.card-idle",
        wardrobeKey = wardrobe,
        settingKey = "set.card",
        cameraKey = "cam.portrait",
        lightingKey = "light.soft",
        effectTags = setOf("character-card") + extra,
        minParticipants = 1,
        maxParticipants = 8,
        rigPlan = plan,
        requiredCovering = covering,
        minArms = minA,
        maxArms = maxA,
        requireTail = needTail,
        forbidTail = noTail,
        minDivergence = minDiv,
        maxDivergence = maxDiv,
        requireHybrid = hybrid,
        wardrobeState = state,
        weight = 1.0,
    )
}
