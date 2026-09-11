package com.sendmefile77.chronosphere.scene

class SceneResolver(pack: ScenePack) {
    private val pack: ScenePack = ScenePackValidator.validateOrThrow(pack)

    fun compatible(request: SceneRequest): List<SceneRecipe> = pack.recipes.filter { recipe ->
        recipe.fallbackPriority == 0 &&
            request.intent in recipe.intents &&
            request.participants.size in recipe.minParticipants..recipe.maxParticipants &&
            request.sceneTags.containsAll(recipe.requiredTags) &&
            request.sceneTags.none { it in recipe.forbiddenTags } &&
            (request.requestedWardrobeState == null || recipe.wardrobeState == request.requestedWardrobeState) &&
            rigCompatible(recipe, request)
    }

    fun resolve(request: SceneRequest): ResolvedScene {
        val eligible = compatible(request)
        val selected = weightedSelect(eligible, request) ?: fallback(request)
        return ResolvedScene(
            sceneKey = sceneKey(request, selected),
            recipeId = selected.id,
            packId = selected.packId,
            packVersion = selected.packVersion,
            styleId = selected.styleId,
            wardrobeState = selected.wardrobeState,
            bodyRigKey = selected.bodyRigKey,
            poseKey = selected.poseKey,
            backgroundKey = selected.backgroundKey,
            cameraKey = selected.cameraKey,
            lightingKey = selected.lightingKey,
            layerKeys = selected.layerKeys,
            fallbackUsed = selected.fallbackPriority > 0,
        )
    }

    private fun weightedSelect(recipes: List<SceneRecipe>, request: SceneRequest): SceneRecipe? {
        val weighted = recipes
            .filter { it.weight > 0.0 }
            .sortedBy { it.id }
            .map { recipe -> recipe to contextualWeight(recipe, request) }
            .filter { it.second > 0.0 && it.second.isFinite() }
        if (weighted.isEmpty()) return null
        val total = weighted.sumOf { it.second }
        if (!total.isFinite() || total <= 0.0) return null
        val target = unit(request.worldSeed, stableRequestKey(request)) * total
        var acc = 0.0
        weighted.forEach { (recipe, weight) ->
            acc += weight
            if (target < acc) return recipe
        }
        return weighted.last().first
    }

    private fun contextualWeight(recipe: SceneRecipe, request: SceneRequest): Double {
        var weight = recipe.weight
        request.participants.forEach { participant ->
            val divergence = participant.numeric["morph_divergence"]
            if (divergence != null && divergence.isFinite()) {
                if ("morph-safe" in recipe.requiredTags || "morph-safe" in recipe.layerKeys) {
                    weight *= 1.0 + divergence.coerceIn(0.0, 1.0) * 0.25
                }
            }
        }
        return weight
    }

    private fun rigCompatible(recipe: SceneRecipe, request: SceneRequest): Boolean {
        if (recipe.supportedRigFamilies.isEmpty()) return true
        return request.participants.all { participant -> participant.rigFamily in recipe.supportedRigFamilies }
    }

    private fun fallback(request: SceneRequest): SceneRecipe {
        val candidates = pack.recipes.asSequence()
            .filter { it.fallbackPriority > 0 }
            .filter { request.intent in it.intents }
            .filter { request.participants.size in it.minParticipants..it.maxParticipants }
            .filter { request.requestedWardrobeState == null || it.wardrobeState == request.requestedWardrobeState }
            .filter { rigCompatible(it, request) || it.supportedRigFamilies.isEmpty() }
            .sortedWith(compareByDescending<SceneRecipe> { it.fallbackPriority }.thenBy { it.id })
            .toList()
        if (candidates.isNotEmpty()) return candidates.first()

        // Do not silently change an explicit wardrobe-state request. Asset packs must provide
        // a compatible fallback for that state (for example an undressed morphology-safe silhouette).
        if (request.requestedWardrobeState != null) {
            error("No fallback recipe for ${request.intent} with ${request.requestedWardrobeState}")
        }

        return pack.recipes.asSequence()
            .filter { it.fallbackPriority > 0 && request.intent in it.intents }
            .sortedWith(compareByDescending<SceneRecipe> { it.fallbackPriority }.thenBy { it.id })
            .firstOrNull()
            ?: error("No fallback recipe for ${request.intent}")
    }

    private fun stableRequestKey(request: SceneRequest): String = buildString {
        append(request.eventId)
        append('|').append(request.rngToken)
        append('|').append(request.intent.name)
        append('|').append(request.requestedWardrobeState?.name ?: "ANY")
        request.participants.sortedBy { it.entityId }.forEach { participant ->
            append('|').append(participant.entityId)
            append(':').append(participant.rigFamily)
            participant.tags.sorted().forEach { append(':').append(it) }
        }
        request.sceneTags.sorted().forEach { append('|').append(it) }
    }

    private fun sceneKey(request: SceneRequest, recipe: SceneRecipe): String =
        "${pack.id}:${pack.version}:${java.lang.Long.toUnsignedString(hash(request.worldSeed, stableRequestKey(request) + '|' + recipe.id), 16)}"

    private fun unit(seed: Long, key: String): Double {
        var z = hash(seed, key)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    private fun hash(seed: Long, key: String): Long {
        var value = seed xor -3750763034362895579L
        key.forEach { ch -> value = (value xor ch.code.toLong()) * 1099511628211L }
        return value
    }
}
