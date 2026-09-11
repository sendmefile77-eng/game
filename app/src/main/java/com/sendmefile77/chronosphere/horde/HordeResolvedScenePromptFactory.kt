package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

object HordeResolvedScenePromptFactory {
    private val sfwModels = listOf(
        "AbsoluteReality",
        "AlbedoBase XL (SDXL)",
        "CyberRealistic Pony",
        "Realistic Vision",
    )

    private val nsfwModels = listOf(
        "CyberRealistic Pony",
        "AbsoluteReality",
        "Realistic Vision",
        "WAI-NSFW-illustrious-SDXL",
    )

    fun create(
        scene: ResolvedScene,
        characterKey: String,
        ageYears: Int,
        visualTags: Set<String> = emptySet(),
        visualNumeric: Map<String, Double> = emptyMap(),
    ): HordeImageRequest {
        require(characterKey.isNotBlank())
        require(ageYears >= 0)
        require(visualTags.none { it.isBlank() })
        require(visualNumeric.values.all { it.isFinite() })

        val undressed = scene.wardrobeState == WardrobeState.UNDRESSED
        require(!undressed || ageYears >= 18) { "Undressed Horde requests require an adult character" }

        val identity = HordeCharacterVisualProfile.from(characterKey)
        val morphology = HordeMorphologyVisual.from(visualTags, visualNumeric)
        val agePhrase = when {
            ageYears < 13 -> "child age $ageYears"
            ageYears < 18 -> "teenager age $ageYears"
            ageYears < 30 -> "adult age $ageYears"
            ageYears < 60 -> "mature adult age $ageYears"
            else -> "older adult age $ageYears"
        }
        val morphologyPhrase = when {
            morphology.promptFragment.isNotBlank() -> morphology.promptFragment
            scene.bodyRigKey.contains("morph", ignoreCase = true) ->
                "distinctive nonstandard humanoid morphology matching the fictional lineage"
            else -> "natural humanlike anatomy"
        }
        val wardrobe = when (scene.wardrobeState) {
            WardrobeState.DRESSED -> "fully clothed"
            WardrobeState.PARTIAL -> if (ageYears >= 18) {
                "partially dressed adult, intimate areas covered"
            } else {
                "fully covered age-appropriate clothing"
            }
            WardrobeState.UNDRESSED ->
                "adult nude full-body portrait, neutral nonsexual pose, natural anatomy, uncensored"
            WardrobeState.DAMAGED -> "weathered damaged clothing, body appropriately covered"
        }
        val camera = if (scene.cameraKey.contains("full", ignoreCase = true)) "full body framing" else "portrait framing"

        val positive = buildList {
            add("high quality photorealistic single fictional character")
            add(agePhrase)
            add(identity.promptFragment)
            add(morphologyPhrase)
            add(wardrobe)
            add(camera)
            add("consistent facial identity and appearance across images")
            add("natural proportions appropriate to the specified body plan")
            add("detailed face")
            add("realistic skin and material detail")
            add("coherent pose")
            add("soft cinematic lighting")
            add("simple coherent background")
            if (ageYears < 18) add("strictly nonsexual age-appropriate presentation")
        }.joinToString(", ")

        val negative = buildList {
            add(HordeImageRequest.DEFAULT_NEGATIVE_PROMPT)
            add("multiple people")
            add("split screen")
            add("different person")
            add("identity change")
            add("different hair color")
            add("different eye color")
            if (undressed) {
                add("clothing")
                add("underwear")
                add("censored")
                add("mosaic")
                add("black bars")
            }
            if (ageYears < 18) {
                add("nudity")
                add("lingerie")
                add("sexualized")
                add("erotic")
                add("suggestive pose")
            }
        }.joinToString(", ")

        val referenceCacheKey = listOf(
            "horde-character-reference-v2",
            characterKey,
            identity.signature,
            morphology.signature,
        ).joinToString("|")
        val cacheKey = listOf(
            "horde-resolved-scene-v4",
            characterKey,
            identity.signature,
            morphology.signature,
            ageYears.toString(),
            scene.sceneKey,
            scene.styleId,
            scene.wardrobeState.name,
            scene.bodyRigKey,
            scene.poseKey,
            scene.backgroundKey,
            scene.cameraKey,
            scene.lightingKey,
            scene.layerKeys.sorted().joinToString(","),
        ).joinToString("|")

        val canonicalPortrait = scene.wardrobeState == WardrobeState.DRESSED &&
            scene.cameraKey.contains("portrait", ignoreCase = true)

        return HordeImageRequest(
            cacheKey = cacheKey,
            positivePrompt = positive,
            negativePrompt = negative,
            nsfw = undressed,
            ageYears = ageYears,
            seed = "chronosphere:$characterKey:${morphology.signature}",
            preferredModels = if (undressed) nsfwModels else sfwModels,
            referenceCacheKey = referenceCacheKey,
            saveResultAsReference = canonicalPortrait,
            referenceDenoisingStrength = if (undressed) 0.68 else 0.52,
        )
    }
}
