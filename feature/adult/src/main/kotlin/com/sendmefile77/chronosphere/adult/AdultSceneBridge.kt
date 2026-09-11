package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
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

    fun eventScene(request: AdultEventRequest): ResolvedScene {
        val result = module.evaluate(request)
        val recipeId = result.mediaCue?.assetKey?.removePrefix("adult://recipe/") ?: SAFE_VISUAL_FALLBACK.id
        val recipe = AdultSceneMapper.findAdultRecipe(recipeId) ?: SAFE_VISUAL_FALLBACK
        return resolve(request, recipe, SceneIntent.EVENT, "event:${result.eventCode}:${recipe.id}")
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
