package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra
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
                "completely naked adult, zero clothing, bare breasts or chest, bare hips, visible adult genitals (penis and scrotum or vulva and labia), full figure head-to-feet, uncensored"
            WardrobeState.DAMAGED -> "weathered damaged clothing appropriate to the era, body appropriately covered"
        }
        val camera = if (scene.cameraKey.contains("full", ignoreCase = true) || undressed) {
            "complete full-body figure from head to feet, entire body visible, head naturally connected to neck and torso, both shoulders, torso, pelvis, arms and legs present in one continuous body"
        } else {
            "three-quarter portrait from head to at least mid-thigh, complete head, neck, both shoulders, torso and arms visible, head naturally connected to the body, no isolated head or bust composition"
        }
        val era = eraPromptFragment(technologyEra)

        val positive = buildList {
            add("high quality photorealistic single fictional character")
            add(agePhrase)
            add(identity.promptFragment)
            add(morphologyPhrase)
            add(era)
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
            addAll(eraNegativeFragments(technologyEra))
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
            "horde-character-reference-v4",
            characterKey,
            identity.signature,
            morphology.signature,
            eraSignature,
        ).joinToString("|")
        val cacheKey = listOf(
            "horde-resolved-scene-v8",
            characterKey,
            identity.signature,
            morphology.signature,
            eraSignature,
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
            steps = 32,
            cfgScale = 6.8,
            seed = "chronosphere:$characterKey:${morphology.signature}:$eraSignature",
            preferredModels = if (undressed) nsfwModels else sfwModels,
            qualityPriority = true,
            referenceCacheKey = referenceCacheKey,
            saveResultAsReference = canonicalPortrait,
            referenceDenoisingStrength = if (undressed) 0.84 else 0.52,
        )
    }

    private fun eraPromptFragment(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL ->
            "prehistoric tribal society, stone-age or early sedentary material culture, hide, woven fiber, wood, bone and stone, simple huts or tents, hearth fire, raw natural landscape, no monumental architecture"
        TechnologyEra.AGRARIAN ->
            "early agrarian society, farms and villages, timber, clay, thatch and simple masonry, hand tools, no industrial technology"
        TechnologyEra.URBAN ->
            "early urban civilization, dense low-rise masonry and timber settlement, markets and workshops, pre-industrial streets"
        TechnologyEra.METALLURGIC ->
            "early metalworking civilization, bronze or iron tools and weapons, furnaces and workshops, pre-modern architecture"
        TechnologyEra.MEDIEVAL ->
            "medieval-level material culture, hand-built stone and timber architecture, candles and hearths, no electricity or modern machinery"
        TechnologyEra.EARLY_INDUSTRIAL ->
            "early industrial material culture, brick workshops, steam machinery, soot, mechanical tools, nineteenth-century level technology"
        TechnologyEra.INDUSTRIAL ->
            "industrial-era material culture, factories, steel, rail and mass-produced objects, no digital technology"
        TechnologyEra.ELECTRIC ->
            "electrified early modern society, electric lighting, wired infrastructure, engines and early mass media, no contemporary digital devices"
        TechnologyEra.INFORMATION ->
            "information-age society, contemporary architecture, computers, digital devices and modern infrastructure"
        TechnologyEra.SPACEFARING ->
            "advanced spacefaring civilization, mature aerospace infrastructure, believable high technology and off-world material culture"
        null -> "historically coherent material culture with no unexplained anachronisms"
    }

    private fun eraNegativeFragments(era: TechnologyEra?): List<String> = when (era) {
        TechnologyEra.TRIBAL -> listOf(
            "palace", "chandelier", "luxury mansion", "ornate ballroom", "modern furniture", "glass skyscraper",
            "electric light", "car", "gun", "computer", "metal armor", "tailored suit",
        )
        TechnologyEra.AGRARIAN -> listOf(
            "chandelier", "luxury palace interior", "industrial machinery", "electric light", "car", "computer", "skyscraper",
        )
        TechnologyEra.URBAN, TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> listOf(
            "electric light", "modern furniture", "car", "computer", "smartphone", "skyscraper", "plastic objects",
        )
        TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> listOf(
            "smartphone", "flat screen", "laptop", "modern skyscraper", "spacecraft",
        )
        TechnologyEra.ELECTRIC -> listOf("smartphone", "laptop", "spacecraft", "hologram")
        TechnologyEra.INFORMATION -> listOf("spacecraft interior", "fantasy medieval costume")
        TechnologyEra.SPACEFARING -> listOf("medieval fantasy castle")
        null -> emptyList()
    }
}
