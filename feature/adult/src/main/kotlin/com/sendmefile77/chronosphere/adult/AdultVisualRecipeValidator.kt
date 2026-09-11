package com.sendmefile77.chronosphere.adult

internal class AdultRecipeValidationException(val errors: List<String>) :
    IllegalArgumentException(errors.joinToString("; "))

internal object AdultVisualRecipeValidator {
    fun validate(recipes: List<AdultVisualRecipe>, fallback: AdultVisualRecipe = SAFE_VISUAL_FALLBACK): List<String> {
        val errors = mutableListOf<String>()
        if (fallback.id.isBlank()) errors += "fallback recipe id is blank"
        validateOne(fallback, errors)
        val seen = mutableSetOf<String>()
        seen.add(fallback.id)
        for (recipe in recipes) {
            if (recipe.id.isBlank()) {
                errors += "recipe id is blank"
            } else if (!seen.add(recipe.id)) {
                errors += "duplicate recipe id ${recipe.id}"
            }
            if (recipe.eventCodes.isEmpty()) errors += "${recipe.id} has no event codes"
            if (recipe.eventCodes.any { it.isBlank() }) errors += "${recipe.id} has a blank event code"
            validateOne(recipe, errors)
        }
        return errors
    }

    fun validateOrThrow(recipes: List<AdultVisualRecipe>, fallback: AdultVisualRecipe = SAFE_VISUAL_FALLBACK): List<AdultVisualRecipe> {
        val errors = validate(recipes, fallback)
        if (errors.isNotEmpty()) throw AdultRecipeValidationException(errors)
        return recipes
    }

    private fun validateOne(recipe: AdultVisualRecipe, errors: MutableList<String>) {
        listOf(
            "sceneFamily" to recipe.sceneFamily,
            "rigLayout" to recipe.rigLayout,
            "poseKey" to recipe.poseKey,
            "wardrobeKey" to recipe.wardrobeKey,
            "settingKey" to recipe.settingKey,
            "cameraKey" to recipe.cameraKey,
            "lightingKey" to recipe.lightingKey,
        ).forEach { (name, value) ->
            if (value.isBlank()) errors += "${recipe.id} $name is blank"
        }
        if (recipe.effectTags.any { it.isBlank() }) errors += "${recipe.id} has a blank effect tag"
        if (recipe.minParticipants < 1) errors += "${recipe.id} minParticipants < 1"
        if (recipe.maxParticipants < recipe.minParticipants) errors += "${recipe.id} maxParticipants < minParticipants"
        recipe.requiredTags.forEach { if (it.isBlank()) errors += "${recipe.id} has a blank required tag" }
        recipe.forbiddenTags.forEach { if (it.isBlank()) errors += "${recipe.id} has a blank forbidden tag" }
        val required = recipe.requiredTags.map { it.lowercase() }.toSet()
        val forbidden = recipe.forbiddenTags.map { it.lowercase() }.toSet()
        if (required.any { it in forbidden }) errors += "${recipe.id} required tag also forbidden"
        if (!recipe.weight.isFinite() || recipe.weight < 0.0) errors += "${recipe.id} weight is not a finite nonnegative value"
    }
}
