package com.sendmefile77.chronosphere.scene

enum class SceneIntent {
    PORTRAIT,
    EVENT,
    CHARACTER_UNDRESS,
}

enum class WardrobeState {
    DRESSED,
    PARTIAL,
    UNDRESSED,
    DAMAGED,
}

data class SceneParticipant(
    val entityId: String,
    val ageYears: Int,
    val rigFamily: String,
    val tags: Set<String> = emptySet(),
    val numeric: Map<String, Double> = emptyMap(),
) {
    init {
        require(entityId.isNotBlank())
        require(ageYears >= 0)
        require(rigFamily.isNotBlank())
        require(numeric.values.all { it.isFinite() })
    }
}

data class SceneRequest(
    val worldSeed: Long,
    val eventId: String,
    val rngToken: Long,
    val intent: SceneIntent,
    val participants: List<SceneParticipant>,
    val sceneTags: Set<String> = emptySet(),
    val numericContext: Map<String, Double> = emptyMap(),
    val requestedWardrobeState: WardrobeState? = null,
) {
    init {
        require(eventId.isNotBlank())
        require(participants.isNotEmpty())
        require(numericContext.values.all { it.isFinite() })
        if (intent == SceneIntent.CHARACTER_UNDRESS || requestedWardrobeState == WardrobeState.UNDRESSED) {
            require(participants.all { it.ageYears >= 18 }) { "Undressed character scenes require adult participants" }
        }
    }
}

data class SceneRecipe(
    val id: String,
    val packId: String,
    val packVersion: Int,
    val styleId: String,
    val intents: Set<SceneIntent>,
    val minParticipants: Int = 1,
    val maxParticipants: Int = 1,
    val supportedRigFamilies: Set<String> = emptySet(),
    val requiredTags: Set<String> = emptySet(),
    val forbiddenTags: Set<String> = emptySet(),
    val wardrobeState: WardrobeState = WardrobeState.DRESSED,
    val bodyRigKey: String,
    val poseKey: String,
    val backgroundKey: String,
    val cameraKey: String,
    val lightingKey: String,
    val layerKeys: List<String> = emptyList(),
    val weight: Double = 1.0,
    val fallbackPriority: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(packId.isNotBlank())
        require(packVersion >= 1)
        require(styleId.isNotBlank())
        require(intents.isNotEmpty())
        require(minParticipants >= 1 && maxParticipants >= minParticipants)
        require(bodyRigKey.isNotBlank())
        require(poseKey.isNotBlank())
        require(backgroundKey.isNotBlank())
        require(cameraKey.isNotBlank())
        require(lightingKey.isNotBlank())
        require(layerKeys.none { it.isBlank() })
        require(weight.isFinite() && weight >= 0.0)
    }
}

data class ScenePack(
    val id: String,
    val version: Int,
    val styleId: String,
    val recipes: List<SceneRecipe>,
) {
    init {
        require(id.isNotBlank())
        require(version >= 1)
        require(styleId.isNotBlank())
        require(recipes.isNotEmpty())
    }
}

data class ResolvedScene(
    val sceneKey: String,
    val recipeId: String,
    val packId: String,
    val packVersion: Int,
    val styleId: String,
    val wardrobeState: WardrobeState,
    val bodyRigKey: String,
    val poseKey: String,
    val backgroundKey: String,
    val cameraKey: String,
    val lightingKey: String,
    val layerKeys: List<String>,
    val fallbackUsed: Boolean,
)
