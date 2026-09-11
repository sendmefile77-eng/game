package com.sendmefile77.chronosphere.scene

object ScenePackValidator {
    fun validateOrThrow(pack: ScenePack): ScenePack {
        val duplicateRecipeIds = pack.recipes.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateRecipeIds.isEmpty()) { "Duplicate scene recipe ids: ${duplicateRecipeIds.sorted()}" }
        require(pack.recipes.all { it.packId == pack.id }) { "All recipes must use pack id ${pack.id}" }
        require(pack.recipes.all { it.packVersion == pack.version }) { "All recipes must use pack version ${pack.version}" }
        require(pack.recipes.all { it.styleId == pack.styleId }) { "Scene pack must use one coherent style id" }
        require(pack.recipes.any { it.fallbackPriority > 0 }) { "Scene pack must declare at least one fallback recipe" }
        pack.recipes.forEach { recipe ->
            require(recipe.requiredTags.intersect(recipe.forbiddenTags).isEmpty()) {
                "Recipe ${recipe.id} requires and forbids the same tag"
            }
            require(recipe.supportedRigFamilies.none { it.isBlank() }) { "Recipe ${recipe.id} has blank rig family" }
        }
        return pack.copy(recipes = pack.recipes.sortedBy { it.id })
    }
}
