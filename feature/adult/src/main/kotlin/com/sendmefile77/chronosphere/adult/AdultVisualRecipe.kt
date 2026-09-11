package com.sendmefile77.chronosphere.adult

internal data class AdultVisualRecipe(
    val id: String,
    val eventCodes: Set<String>,
    val sceneFamily: String,
    val rigLayout: String,
    val poseKey: String,
    val wardrobeKey: String,
    val settingKey: String,
    val cameraKey: String,
    val lightingKey: String,
    val effectTags: Set<String>,
    val minParticipants: Int = 1,
    val maxParticipants: Int = 8,
    val requiredTags: Set<String> = emptySet(),
    val forbiddenTags: Set<String> = emptySet(),
    val weight: Double = 1.0,
)

internal val SAFE_VISUAL_FALLBACK = AdultVisualRecipe(
    id = "fallback.silhouette.hold",
    eventCodes = emptySet(),
    sceneFamily = "symbolic",
    rigLayout = "solo-bust",
    poseKey = "pose.hold",
    wardrobeKey = "wardrobe.opaque",
    settingKey = "set.threshold",
    cameraKey = "cam.portrait",
    lightingKey = "light.low",
    effectTags = setOf("fallback", "silhouette"),
    minParticipants = 1,
    maxParticipants = 99,
    weight = 1.0,
)
