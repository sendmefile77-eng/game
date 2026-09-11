package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.scene.SceneIntent
import com.sendmefile77.chronosphere.scene.ScenePack
import com.sendmefile77.chronosphere.scene.SceneParticipant
import com.sendmefile77.chronosphere.scene.SceneRecipe
import com.sendmefile77.chronosphere.scene.SceneRequest
import com.sendmefile77.chronosphere.scene.WardrobeState

internal object AdultSceneMapper {
    const val PACK_ID = "adult-visual"
    const val PACK_VERSION = 1
    const val STYLE_ID = "adult-oil"

    fun wardrobeOf(recipe: AdultVisualRecipe): WardrobeState {
        if (recipe.wardrobeState == AdultWardrobeState.UNDRESSED) return WardrobeState.UNDRESSED
        if (recipe.wardrobeState == AdultWardrobeState.DRESSED) return WardrobeState.DRESSED
        val key = recipe.wardrobeKey.lowercase()
        return when {
            "undressed" in key || "nude" in key || key.endsWith(".none") -> WardrobeState.UNDRESSED
            "ripped" in key || "torn" in key || "straps" in key -> WardrobeState.DAMAGED
            "half" in key || "parted" in key || "hiked" in key || "sheer" in key || "open" in key -> WardrobeState.PARTIAL
            else -> WardrobeState.DRESSED
        }
    }

    fun isFallback(recipe: AdultVisualRecipe): Boolean =
        recipe.rigPlan == RigPlan.SILHOUETTE || recipe.effectTags.any { it.equals("fallback", ignoreCase = true) }

    fun rigFamily(recipe: AdultVisualRecipe, morph: AdultMorphology): String {
        if (recipe.rigPlan == RigPlan.SILHOUETTE || isFallback(recipe)) return "silhouette"
        return when (recipe.rigPlan) {
            RigPlan.BASELINE -> "human"
            RigPlan.HYBRID -> "hybrid"
            RigPlan.DIVERGENT -> when {
                morph.arms >= 3 -> "divergent-quad"
                morph.tail -> "divergent-tailed"
                morph.covering == "scales" -> "divergent-scaled"
                morph.posture == "semi_upright" -> "divergent-semi"
                else -> "divergent"
            }
            RigPlan.SILHOUETTE -> "silhouette"
        }
    }

    fun toSceneRecipe(recipe: AdultVisualRecipe, morph: AdultMorphology, intent: SceneIntent): SceneRecipe {
        val wardrobe = if (intent == SceneIntent.CHARACTER_UNDRESS) WardrobeState.UNDRESSED else wardrobeOf(recipe)
        val family = rigFamily(recipe, morph)
        val fallback = isFallback(recipe)
        return SceneRecipe(
            id = recipe.id,
            packId = PACK_ID,
            packVersion = PACK_VERSION,
            styleId = STYLE_ID,
            intents = setOf(intent),
            minParticipants = recipe.minParticipants.coerceAtLeast(1),
            maxParticipants = recipe.maxParticipants.coerceAtLeast(recipe.minParticipants),
            supportedRigFamilies = setOf(family),
            wardrobeState = wardrobe,
            bodyRigKey = recipe.rigLayout,
            poseKey = recipe.poseKey,
            backgroundKey = recipe.settingKey,
            cameraKey = recipe.cameraKey,
            lightingKey = recipe.lightingKey,
            layerKeys = buildList {
                add("recipe:${recipe.id}")
                add("wardrobe:${recipe.wardrobeKey}")
                addAll(recipe.effectTags.map { it.lowercase() }.sorted())
                if (fallback) add("morph-safe")
            },
            weight = if (fallback) 0.0 else 1.0,
            fallbackPriority = if (fallback) 100 else 0,
        )
    }

    fun fallbackRecipe(intent: SceneIntent, wardrobe: WardrobeState): SceneRecipe {
        val source = when {
            intent == SceneIntent.CHARACTER_UNDRESS || wardrobe == WardrobeState.UNDRESSED -> SAFE_UNDRESSED_FALLBACK
            else -> SAFE_MORPH_FALLBACK
        }
        return toSceneRecipe(source, AdultMorphology(signaled = false), intent).copy(
            id = source.id,
            wardrobeState = if (intent == SceneIntent.CHARACTER_UNDRESS) WardrobeState.UNDRESSED else wardrobe,
            supportedRigFamilies = emptySet(),
            weight = 0.0,
            fallbackPriority = 100,
            layerKeys = listOf("recipe:${source.id}", "morph-safe", "fallback"),
        )
    }

    fun packFor(recipe: AdultVisualRecipe, morph: AdultMorphology, intent: SceneIntent): ScenePack {
        val primary = toSceneRecipe(recipe, morph, intent)
        val fallback = fallbackRecipe(intent, primary.wardrobeState)
        val recipes = if (primary.id == fallback.id) listOf(fallback) else listOf(primary, fallback)
        return ScenePack(id = PACK_ID, version = PACK_VERSION, styleId = STYLE_ID, recipes = recipes)
    }

    fun sceneRequest(
        request: AdultEventRequest,
        recipe: AdultVisualRecipe,
        intent: SceneIntent,
        eventId: String,
    ): SceneRequest {
        val morph = AdultMorphologyParser.parse(request)
        val family = rigFamily(recipe, morph)
        val wardrobe = if (intent == SceneIntent.CHARACTER_UNDRESS) WardrobeState.UNDRESSED else wardrobeOf(recipe)
        val numeric = request.context.numericContext.filterValues { it.isFinite() }.filterKeys { it.isNotBlank() }
        val tags = buildSet {
            addAll(AdultCulture.normalizedTags(request.context.cultureTags))
            add("recipe:${recipe.id}")
            if (morph.signaled) {
                add("covering:${morph.covering}")
                add("posture:${morph.posture}")
                add("arms:${morph.arms}")
            }
        }
        return SceneRequest(
            worldSeed = request.context.worldSeed,
            eventId = eventId,
            rngToken = AdultFingerprint.of(request),
            intent = intent,
            participants = request.participants.map { person ->
                SceneParticipant(
                    entityId = person.entityId,
                    ageYears = person.ageYears,
                    rigFamily = family,
                    tags = tags,
                    numeric = numeric.filterKeys { it.startsWith("morph_") },
                )
            },
            sceneTags = tags,
            numericContext = numeric,
            requestedWardrobeState = if (intent == SceneIntent.CHARACTER_UNDRESS) WardrobeState.UNDRESSED else wardrobe,
        )
    }

    fun findAdultRecipe(id: String): AdultVisualRecipe? =
        (BundledVisualRecipes.all + AdultCardRecipes.all + listOf(SAFE_VISUAL_FALLBACK, SAFE_MORPH_FALLBACK, SAFE_UNDRESSED_FALLBACK))
            .firstOrNull { it.id == id }
}
