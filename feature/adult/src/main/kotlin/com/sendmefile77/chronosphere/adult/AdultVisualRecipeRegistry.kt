package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.MediaCue

internal class AdultVisualRecipeRegistry(
    recipes: List<AdultVisualRecipe>,
    private val fallback: AdultVisualRecipe = SAFE_VISUAL_FALLBACK,
) {
    val recipes: List<AdultVisualRecipe> = AdultVisualRecipeValidator.validateOrThrow(recipes, fallback)

    fun compatible(event: AdultEventRule, request: AdultEventRequest): List<AdultVisualRecipe> {
        val tags = AdultCulture.normalizedTags(request.context.cultureTags)
        val count = request.participants.size
        return recipes.filter { recipe ->
            event.code in recipe.eventCodes &&
                count in recipe.minParticipants..recipe.maxParticipants &&
                tags.containsAll(recipe.requiredTags.map { it.lowercase() }) &&
                tags.none { it in recipe.forbiddenTags.map { tag -> tag.lowercase() } }
        }
    }

    fun select(event: AdultEventRule, request: AdultEventRequest, fingerprint: Long): AdultVisualRecipe {
        val pool = compatible(event, request)
        if (pool.isEmpty()) return fallback
        val weighted = pool.sortedBy { it.id }.map { it to it.weight.coerceAtLeast(0.0) }
        val total = weighted.sumOf { it.second }
        if (total <= 0.0) return fallback
        val target = AdultFingerprint.unit01(fingerprint, 41L) * total
        var acc = 0.0
        for ((recipe, weight) in weighted) {
            acc += weight
            if (target < acc) return recipe
        }
        return weighted.last().first
    }

    fun mediaCue(event: AdultEventRule, request: AdultEventRequest, fingerprint: Long): MediaCue {
        val recipe = select(event, request, fingerprint)
        return encode(recipe, event, request)
    }

    fun encode(recipe: AdultVisualRecipe, event: AdultEventRule, request: AdultEventRequest): MediaCue {
        val tone = AdultCulture.tone(request.context.cultureTags)
        val cultures = request.context.cultureTags.map { it.lowercase() }.sorted()
        val tags = buildSet {
            add("recipe:${recipe.id}")
            add("family:${recipe.sceneFamily}")
            add("rig:${recipe.rigLayout}")
            add("pose:${recipe.poseKey}")
            add("wardrobe:${recipe.wardrobeKey}")
            add("setting:${recipe.settingKey}")
            add("camera:${recipe.cameraKey}")
            add("light:${recipe.lightingKey}")
            add("event:${event.code.lowercase()}")
            add("pack-setting:${event.setting}")
            add("explicitness:${tone.explicitness}")
            add("participants_${request.participants.size.coerceAtMost(12)}")
            addAll(recipe.effectTags.map { it.lowercase() })
            addAll(event.mediaTags.map { it.lowercase() })
            addAll(cultures.take(4))
        }
        return MediaCue(assetKey = "adult://recipe/${recipe.id}", tags = tags)
    }

    companion object {
        fun bundled(): AdultVisualRecipeRegistry = AdultVisualRecipeRegistry(BundledVisualRecipes.all)
    }
}
