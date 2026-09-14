package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

internal const val RESOLVED_SCENE_CACHE_SCHEMA = "horde-resolved-scene-v11"
internal const val CHARACTER_REFERENCE_CACHE_SCHEMA = "horde-character-reference-v5"

object HordeResolvedScenePromptFactory {
    private val sfwModels = listOf(
        "AbsoluteReality",
        "AlbedoBase XL (SDXL)",
        "CyberRealistic Pony",
        "Realistic Vision",
    )

    private val nsfwModels = listOf(
        "AbsoluteReality",
        "Realistic Vision",
        "CyberRealistic Pony",
        "WAI-NSFW-illustrious-SDXL",
    )

    fun create(
        scene: ResolvedScene,
        characterKey: String,
        ageYears: Int,
        visualTags: Set<String> = emptySet(),
        visualNumeric: Map<String, Double> = emptyMap(),
        technologyEra: TechnologyEra? = null,
    ): HordeImageRequest {
        require(characterKey.isNotBlank())
        require(ageYears >= 0)
        require(visualTags.none { it.isBlank() })
        require(visualNumeric.values.all { it.isFinite() })

        val undressed = scene.wardrobeState == WardrobeState.UNDRESSED
        require(!undressed || ageYears >= 18) { "Undressed Horde requests require an adult character" }

        val identity = HordeCharacterVisualProfile.from(characterKey)
        val morphology = HordeMorphologyVisual.from(visualTags, visualNumeric)
        val historicalVisual = HordeHistoricalVisualPrompt.fragment(visualTags, technologyEra)
        val historicalSignature = HordeHistoricalVisualPrompt.signature(visualTags)
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
            WardrobeState.DRESSED -> "fully clothed in clothing appropriate to the stated technological era"
            WardrobeState.PARTIAL -> if (ageYears >= 18) {
                "partially dressed adult, intimate areas covered, clothing appropriate to the stated technological era"
            } else {
                "fully covered age-appropriate clothing appropriate to the stated technological era"
            }
            WardrobeState.UNDRESSED ->
                "completely naked adult, zero clothing anywhere, bare breasts or chest, bare hips and ass, " +
                    "visible adult genitals in frame (erect penis and scrotum or vulva, labia and clitoris), " +
                    "full figure from head to toes, uncensored explicit nude, no drapery"
            WardrobeState.DAMAGED -> "weathered damaged clothing appropriate to the era, body appropriately covered"
        }
        val camera = if (scene.cameraKey.contains("full", ignoreCase = true) || undressed) {
            "complete full-body figure from head to feet, entire body visible, head naturally connected to neck and torso, both shoulders, torso, pelvis, arms and legs present in one continuous body"
        } else {
            "three-quarter portrait from head to at least mid-thigh, complete head, neck, both shoulders, torso and arms visible, head naturally connected to the body, no isolated head or bust composition"
        }

        val positive = buildList {
            add("high quality photorealistic single fictional character")
            add(agePhrase)
            add(identity.promptFragment)
            add(morphologyPhrase)
            add(HordeEraVisual.materialCulture(technologyEra))
            add(HordeEraVisual.portraitInterior(technologyEra))
            if (historicalVisual.isNotBlank()) add(historicalVisual)
            add(wardrobe)
            add(camera)
            add("one anatomically coherent continuous body")
            add("consistent facial identity and appearance across images")
            add("natural proportions appropriate to the specified body plan")
            add("detailed realistic face attached naturally to the body")
            add("high-frequency skin and hair detail, visible pores and natural texture, crisp eyes and facial features")
            add("realistic skin, hair, fabric and material detail")
            add("natural standing or seated pose with believable weight and posture")
            add("documentary cinematic realism, not a sculpture, mannequin or 3D character render")
            add("environment and objects strictly consistent with the technological era")
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
            add("floating head")
            add("disembodied head")
            add("severed head")
            add("detached head")
            add("cropped neck")
            add("head without torso")
            add("bust sculpture")
            add("mannequin")
            add("wax figure")
            add("plastic doll")
            add("3D render")
            add("incomplete torso")
            add("missing shoulders")
            add("detached body parts")
            addAll(HordeEraVisual.negatives(technologyEra))
            if (undressed) {
                add("clothing")
                add("dress")
                add("robe")
                add("shirt")
                add("pants")
                add("skirt")
                add("armor")
                add("underwear")
                add("bra")
                add("loincloth")
                add("censored")
                add("mosaic")
                add("black bars")
                add("covered genitals")
                add("hidden crotch")
                add("fabric over hips")
                add("tasteful implied nude")
                add("artistic shadow covering genitals")
            }
            if (ageYears < 18) {
                add("nudity")
                add("lingerie")
                add("sexualized")
                add("erotic")
                add("suggestive pose")
            }
        }.joinToString(", ")

        val eraSignature = technologyEra?.name ?: "UNSPECIFIED"
        val referenceCacheKey = listOf(
            CHARACTER_REFERENCE_CACHE_SCHEMA,
            characterKey,
            identity.signature,
            morphology.signature,
            eraSignature,
            historicalSignature,
        ).joinToString("|")
        val cacheKey = listOf(
            RESOLVED_SCENE_CACHE_SCHEMA,
            characterKey,
            identity.signature,
            morphology.signature,
            eraSignature,
            historicalSignature,
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
            width = 768,
            height = 1152,
            steps = 22,
            cfgScale = 5.5,
            seed = "chronosphere:$characterKey:${morphology.signature}:$eraSignature:$historicalSignature",
            preferredModels = if (undressed) nsfwModels else sfwModels,
            qualityPriority = true,
            referenceCacheKey = referenceCacheKey,
            saveResultAsReference = canonicalPortrait,
            referenceDenoisingStrength = if (undressed) 0.84 else 0.52,
        )
    }
}
