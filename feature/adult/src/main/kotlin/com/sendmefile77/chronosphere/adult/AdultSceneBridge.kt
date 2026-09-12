package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.SceneIntent
import com.sendmefile77.chronosphere.scene.SceneResolver
import com.sendmefile77.chronosphere.scene.WardrobeState

/**
 * No-argument public bridge from the adult module onto `core:scene`.
 * Load by class name from the app without a compile-time dependency on this module:
 * `com.sendmefile77.chronosphere.adult.AdultSceneBridge`.
 */
class AdultSceneBridge() {
    private val module = DeterministicAdultModule()
    private val recipes = AdultVisualRecipeRegistry.bundled()
    private val cards = AdultCharacterCardVisuals(recipes)

    fun dressedCharacterCard(request: AdultEventRequest): ResolvedScene =
        card(request, AdultWardrobeState.DRESSED, SceneIntent.PORTRAIT)

    fun undressedCharacterCard(request: AdultEventRequest): ResolvedScene {
        require(AdultUndressPolicy.allowsParticipants(request.participants)) {
            "Adult module accepts adults only"
        }
        return card(request, AdultWardrobeState.UNDRESSED, SceneIntent.CHARACTER_UNDRESS)
    }

    /** Semantic descriptor for the same deterministic dressed card recipe. */
    fun dressedCharacterVisual(request: AdultEventRequest): AdultVisualSceneDescriptor =
        cardVisual(request, AdultWardrobeState.DRESSED, "portrait")

    /** Semantic descriptor for the same deterministic undressed card recipe. */
    fun undressedCharacterVisual(request: AdultEventRequest): AdultVisualSceneDescriptor {
        require(AdultUndressPolicy.allowsParticipants(request.participants)) {
            "Adult module accepts adults only"
        }
        return cardVisual(request, AdultWardrobeState.UNDRESSED, "character_undress")
    }

    fun eventScene(request: AdultEventRequest): ResolvedScene {
        val result = module.evaluate(request)
        val recipeId = result.mediaCue?.assetKey?.removePrefix("adult://recipe/") ?: SAFE_VISUAL_FALLBACK.id
        val recipe = AdultSceneMapper.findAdultRecipe(recipeId) ?: SAFE_VISUAL_FALLBACK
        return resolve(request, recipe, SceneIntent.EVENT, "event:${result.eventCode}:${recipe.id}")
    }

    /**
     * Structured adult event intent for visual backends such as AI Horde.
     * The backend receives the actual deterministic event/recipe rather than inferring a scene
     * from a generic UNDRESSED flag.
     */
    fun eventVisual(request: AdultEventRequest): AdultVisualSceneDescriptor {
        val result = module.evaluate(request)
        val recipeId = result.mediaCue?.assetKey?.removePrefix("adult://recipe/") ?: SAFE_VISUAL_FALLBACK.id
        val recipe = AdultSceneMapper.findAdultRecipe(recipeId) ?: SAFE_VISUAL_FALLBACK
        return visualDescriptor(
            request = request,
            intent = "event",
            eventCode = result.eventCode,
            recipe = recipe,
            mediaTags = result.mediaCue?.tags.orEmpty(),
        )
    }

    private fun card(
        request: AdultEventRequest,
        state: AdultWardrobeState,
        intent: SceneIntent,
    ): ResolvedScene {
        cards.resolve(request, state)
        val fingerprint = AdultFingerprint.of(request)
        val recipe = recipes.selectCard(request, state, fingerprint)
        return resolve(request, recipe, intent, "card:${state.name.lowercase()}:${recipe.id}")
    }

    private fun cardVisual(
        request: AdultEventRequest,
        state: AdultWardrobeState,
        intent: String,
    ): AdultVisualSceneDescriptor {
        val cue = cards.resolve(request, state)
        val recipe = recipes.selectCard(request, state, AdultFingerprint.of(request))
        return visualDescriptor(
            request = request,
            intent = intent,
            eventCode = AdultUndressPolicy.CARD_EVENT,
            recipe = recipe,
            mediaTags = cue.tags,
        )
    }

    private fun visualDescriptor(
        request: AdultEventRequest,
        intent: String,
        eventCode: String,
        recipe: AdultVisualRecipe,
        mediaTags: Set<String>,
    ): AdultVisualSceneDescriptor = AdultVisualSceneDescriptor(
        requestId = request.requestId,
        intent = intent,
        eventCode = eventCode,
        participants = request.participants,
        recipeId = recipe.id,
        sceneFamily = recipe.sceneFamily,
        rigLayout = recipe.rigLayout,
        poseKey = recipe.poseKey,
        wardrobeKey = recipe.wardrobeKey,
        settingKey = recipe.settingKey,
        cameraKey = recipe.cameraKey,
        lightingKey = recipe.lightingKey,
        explicitness = AdultCulture.tone(request.context.cultureTags).explicitness,
        effectTags = recipe.effectTags.map { it.lowercase() }.toSet(),
        mediaTags = mediaTags.map { it.lowercase() }.toSet(),
    )

    private fun resolve(
        request: AdultEventRequest,
        recipe: AdultVisualRecipe,
        intent: SceneIntent,
        eventId: String,
    ): ResolvedScene {
        val morph = AdultMorphologyParser.parse(request)
        val pack = AdultSceneMapper.packFor(recipe, morph, intent)
        val sceneRequest = AdultSceneMapper.sceneRequest(request, recipe, intent, eventId)
        val resolved = SceneResolver(pack).resolve(sceneRequest)
        check(resolved.recipeId == recipe.id || resolved.fallbackUsed) {
            "core:scene replaced adult recipe ${recipe.id} with ${resolved.recipeId}"
        }
        if (intent == SceneIntent.CHARACTER_UNDRESS) {
            check(resolved.wardrobeState == WardrobeState.UNDRESSED) {
                "undressed card lost UNDRESSED wardrobe state"
            }
        }
        if (AdultSceneMapper.isFallback(recipe)) {
            check(resolved.fallbackUsed) { "adult fallback lost fallbackUsed flag" }
        }
        return resolved
    }
}
