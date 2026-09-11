package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.MediaCue

internal class AdultVisualRecipeRegistry(
    recipes: List<AdultVisualRecipe>,
    private val fallback: AdultVisualRecipe = SAFE_VISUAL_FALLBACK,
    private val morphFallback: AdultVisualRecipe = SAFE_MORPH_FALLBACK,
) {
    val recipes: List<AdultVisualRecipe> = AdultVisualRecipeValidator.validateOrThrow(recipes, fallback)

    fun compatible(event: AdultEventRule, request: AdultEventRequest): List<AdultVisualRecipe> {
        val tags = AdultCulture.normalizedTags(request.context.cultureTags)
        val count = request.participants.size
        val morph = AdultMorphologyParser.parse(request)
        return recipes.filter { recipe ->
            event.code in recipe.eventCodes &&
                count in recipe.minParticipants..recipe.maxParticipants &&
                tags.containsAll(recipe.requiredTags.map { it.lowercase() }) &&
                tags.none { it in recipe.forbiddenTags.map { tag -> tag.lowercase() } } &&
                AdultContextSignals.matchesEraRequirement(recipe.requiredEras, recipe.forbiddenEras, tags) &&
                AdultMorphCompatibility.matches(recipe, morph)
        }
    }

    fun recipeWeight(recipe: AdultVisualRecipe, request: AdultEventRequest): Double {
        val base = if (recipe.weight.isFinite()) recipe.weight.coerceAtLeast(0.0) else 0.0
        val eraScaled = base * AdultContextSignals.weightMultiplier(request, recipe.eraWeights, recipe.numericWeights)
        val morph = AdultMorphologyParser.parse(request)
        val scaled = eraScaled * AdultMorphCompatibility.weightBump(recipe, morph)
        return if (scaled.isFinite()) scaled.coerceAtLeast(0.0) else 0.0
    }

    fun select(event: AdultEventRule, request: AdultEventRequest, fingerprint: Long): AdultVisualRecipe {
        val pool = compatible(event, request)
        val morph = AdultMorphologyParser.parse(request)
        if (pool.isEmpty()) return if (morph.visuallyDivergent || !morph.structuralBaseline) morphFallback else fallback
        val weighted = pool.sortedBy { it.id }.map { it to recipeWeight(it, request) }
        val total = weighted.sumOf { it.second }
        if (total <= 0.0) return if (morph.visuallyDivergent || !morph.structuralBaseline) morphFallback else fallback
        val target = AdultFingerprint.unit01(fingerprint, 41L) * total
        var acc = 0.0
        for ((recipe, weight) in weighted) {
            acc += weight
            if (target < acc) return recipe
        }
        return weighted.last().first
    }

    fun mediaCue(event: AdultEventRule, request: AdultEventRequest, fingerprint: Long): MediaCue {
        return encode(select(event, request, fingerprint), event, request)
    }

    fun encode(recipe: AdultVisualRecipe, event: AdultEventRule, request: AdultEventRequest): MediaCue {
        val tone = AdultCulture.tone(request.context.cultureTags)
        val cultures = request.context.cultureTags.map { it.lowercase() }.filterNot { isMorphTag(it) }.sorted()
        val eras = AdultContextSignals.eraTags(request.context.cultureTags).sorted()
        val morph = AdultMorphologyParser.parse(request)
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
            add("rig-plan:${recipe.rigPlan.name.lowercase()}")
            addAll(recipe.effectTags.map { it.lowercase() })
            addAll(event.mediaTags.map { it.lowercase() })
            addAll(cultures.take(4))
            eras.firstOrNull()?.let { add("era:$it") }
            if (morph.signaled) {
                add("covering:${morph.covering}")
                add("posture:${morph.posture}")
                add("arms:${morph.arms}")
                add("legs:${morph.legs}")
                add("eyes:${morph.eyes}")
                if (morph.tail) add("tail")
                if (morph.mixedAncestry) add("mixed_ancestry")
                if (morph.hybridLineage) add("hybrid_lineage")
                morph.majorAncestry?.let { add("ancestry:major:$it") }
                morph.lineageIds.take(2).forEach { add("lineage:$it") }
            }
        }
        return MediaCue(assetKey = "adult://recipe/${recipe.id}", tags = tags)
    }

    private fun isMorphTag(tag: String): Boolean {
        val t = tag.lowercase()
        return t.startsWith("lineage:") || t.startsWith("bio_rank:") || t.startsWith("posture:") ||
            t.startsWith("covering:") || t.startsWith("arms:") || t.startsWith("legs:") ||
            t.startsWith("eyes:") || t.startsWith("ancestry:") || t == "tail" ||
            t == "mixed_ancestry" || t == "hybrid_lineage"
    }

    companion object {
        fun bundled(): AdultVisualRecipeRegistry = AdultVisualRecipeRegistry(BundledVisualRecipes.all)
    }
}
