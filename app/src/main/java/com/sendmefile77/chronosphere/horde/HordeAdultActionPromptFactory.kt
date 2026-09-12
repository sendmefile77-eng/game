package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.AdultActionPlan
import com.sendmefile77.chronosphere.AdultActionType
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

/** Explicit character-card action scenes. Kept separate from idle portrait mapping. */
object HordeAdultActionPromptFactory {
    private val nsfwModels = listOf(
        "AbsoluteReality",
        "Realistic Vision",
        "CyberRealistic Pony",
        "WAI-NSFW-illustrious-SDXL",
    )

    fun create(
        scene: ResolvedScene,
        plan: AdultActionPlan,
        visualTags: Set<String> = emptySet(),
        visualNumeric: Map<String, Double> = emptyMap(),
        technologyEra: TechnologyEra? = null,
        adultVisual: AdultVisualSceneDescriptor? = null,
    ): HordeImageRequest {
        require(plan.primary.ageYears >= 18)
        require(plan.partner == null || plan.partner.ageYears >= 18)

        val identity = HordeCharacterVisualProfile.from(plan.primary.personId)
        val morphology = HordeMorphologyVisual.from(visualTags, visualNumeric)
        val base = HordeResolvedScenePromptFactory.create(
            scene = scene.copy(wardrobeState = WardrobeState.UNDRESSED),
            characterKey = plan.primary.personId,
            ageYears = plan.primary.ageYears,
            visualTags = visualTags,
            visualNumeric = visualNumeric,
            technologyEra = technologyEra,
        )
        val partnerIdentity = plan.partner?.let { HordeCharacterVisualProfile.from(it.personId) }
        val partnerLine = plan.partner?.let { partner ->
            "second confirmed adult participant age ${partner.ageYears}, " +
                "${partnerIdentity?.promptFragment}, complete connected nude body"
        }
        val act = actionPrompt(plan)
        val actLock = actLock(plan.type)
        val composition = if (plan.solo) {
            "one explicit adult sex act filling the frame, whole nude body from head to feet"
        } else {
            "two confirmed adults having sex in one frame, both whole nude bodies visible, " +
                "the selected sex act is the only subject of the image, no extra people"
        }
        val recipeSetting = adultVisual?.settingKey
            ?.takeIf { it.isNotBlank() }
            ?.let { "structured scene setting ${it.replace('.', ' ').replace('-', ' ')}" }

        return base.copy(
            cacheKey = listOf(
                "horde-adult-action-v5",
                plan.cacheToken,
                identity.signature,
                morphology.signature,
                technologyEra?.name ?: "UNSPECIFIED",
                adultVisual?.recipeId ?: "none",
                adultVisual?.settingKey ?: "none",
            ).joinToString("|"),
            positivePrompt = listOf(
                "explicit uncensored photorealistic adult sex scene, adults 18+",
                actLock,
                act,
                act,
                composition,
                HordeEraVisual.intimateInterior(technologyEra),
                HordeEraVisual.materialCulture(technologyEra),
                recipeSetting,
                "primary adult identity: ${identity.promptFragment}",
                morphology.promptFragment.takeIf { it.isNotBlank() },
                partnerLine,
                "completely nude, no clothing, genitals in view, sexual contact clearly readable",
                "the environment, furniture, light and materials must match the stated technological era",
                "full-length bodies, no bust crop, no portrait crop",
                "keep the primary adult face and hair locked to the reference identity",
                "no text in image",
            ).filter { !it.isNullOrBlank() }.joinToString(", "),
            negativePrompt = buildList {
                add(HordeImageRequest.DEFAULT_NEGATIVE_PROMPT)
                addAll(
                    listOf(
                        "child", "minor", "teen", "underage", "loli", "shota",
                        "clothing", "dress", "robe", "shirt", "pants", "underwear", "armor",
                        "censored", "mosaic", "black bars",
                        "standing idle portrait", "passport photo", "studio portrait",
                        "posed fashion nude", "just standing", "arms at sides",
                        "no sexual contact", "closed mouth far from genitals",
                        "bust crop", "portrait crop", "missing feet", "cropped head",
                        "wrong person", "identity change",
                        "wrong sex act", "mismatched sex act",
                        "oversaturated", "overexposed", "burnt colors", "overcooked",
                        "high contrast", "oversharpened",
                    ),
                )
                addAll(wrongActNegatives(plan.type))
                addAll(HordeEraVisual.negatives(technologyEra))
                if (!plan.solo) {
                    add("single person")
                    add("solo portrait")
                    add("only one body")
                    add("crowded group of three or more people")
                } else {
                    add("unrelated extra people")
                }
            }.joinToString(", "),
            nsfw = true,
            width = if (plan.solo) 768 else 832,
            height = if (plan.solo) 1152 else 1216,
            steps = 20,
            cfgScale = 5.2,
            seed = "${base.seed}:action-v5:${plan.cacheToken}",
            preferredModels = nsfwModels,
            qualityPriority = true,
            referenceCacheKey = base.referenceCacheKey,
            saveResultAsReference = false,
            referenceDenoisingStrength = 0.88,
        )
    }

    private fun actLock(type: AdultActionType): String = when (type) {
        AdultActionType.FOOTJOB ->
            "the only sex act in this image is a footjob: bare feet and toes wrapped around genitals, " +
                "not oral sex, not vaginal sex, not anal sex"
        AdultActionType.ORAL ->
            "the only sex act in this image is oral sex: a mouth on genitals, " +
                "not a footjob, not vaginal penetration, not anal sex"
        AdultActionType.VAGINAL ->
            "the only sex act in this image is vaginal sex: a shaft visibly inside a vagina, " +
                "not a footjob, not oral sex, not anal sex"
        AdultActionType.ANAL ->
            "the only sex act in this image is anal sex: a shaft visibly inside an anus, " +
                "not a footjob, not oral sex, not vaginal sex"
    }

    private fun wrongActNegatives(type: AdultActionType): List<String> = when (type) {
        AdultActionType.FOOTJOB -> listOf(
            "blowjob", "cunnilingus", "mouth on penis", "vaginal penetration", "anal penetration", "missionary sex",
        )
        AdultActionType.ORAL -> listOf(
            "footjob", "feet on genitals", "soles on penis", "vaginal penetration", "anal penetration",
        )
        AdultActionType.VAGINAL -> listOf(
            "footjob", "feet on genitals", "blowjob as the main act", "anal penetration as the main act",
        )
        AdultActionType.ANAL -> listOf(
            "footjob", "feet on genitals", "vaginal sex as the main act", "blowjob as the main act",
        )
    }

    private fun actionPrompt(plan: AdultActionPlan): String {
        val woman = "adult woman"
        val man = "adult man"
        val primary = if (plan.primary.sex == BiologicalSex.FEMALE) woman else man
        val partner = plan.partner?.let { if (it.sex == BiologicalSex.FEMALE) woman else man }
        val primaryGenitals = if (plan.primary.sex == BiologicalSex.FEMALE) {
            "visible vulva, labia and clitoris"
        } else {
            "visible erect penis, scrotum and testicles"
        }
        val partnerGenitals = when (plan.partner?.sex) {
            BiologicalSex.FEMALE -> "partner's visible vulva, labia and clitoris"
            BiologicalSex.MALE -> "partner's visible erect penis, scrotum and testicles"
            null -> primaryGenitals
        }
        return when (plan.type) {
            AdultActionType.FOOTJOB -> if (plan.solo) {
                "$primary lying nude, soles and toes wrapped around $primaryGenitals, " +
                    "explicit solo footjob, genitals squeezed between bare feet, uncensored"
            } else {
                "$primary giving a footjob to the $partner, bare feet stroking $partnerGenitals, " +
                    "toes and soles on the shaft or vulva, both bodies nude, sexual contact obvious"
            }
            AdultActionType.ORAL -> when {
                plan.solo && plan.primary.sex == BiologicalSex.FEMALE ->
                    "$primary on her knees nude, mouth open around a visible erect penis in her mouth, " +
                        "explicit blowjob, saliva, $primaryGenitals visible"
                plan.solo ->
                    "$primary nude, mouth on a vulva, explicit cunnilingus, tongue on clitoris, " +
                        "$primaryGenitals visible"
                plan.partner?.sex == BiologicalSex.MALE ->
                    "$primary performing a blowjob on the $partner, mouth wrapped around the erect penis, " +
                        "lips on the shaft, $partnerGenitals in the mouth, both nude"
                else ->
                    "$primary performing cunnilingus on the $partner, mouth on vulva, " +
                        "tongue on $partnerGenitals, both nude bodies in frame"
            }
            AdultActionType.VAGINAL -> when {
                plan.solo && plan.primary.sex == BiologicalSex.FEMALE ->
                    "$primary nude with legs spread, fingers or a shaft inside the vagina, " +
                        "explicit vaginal penetration, $primaryGenitals open and wet"
                plan.solo ->
                    "$primary nude, erect penis penetrating a visible vagina, vaginal sex in progress"
                plan.primary.sex == BiologicalSex.FEMALE && plan.partner?.sex == BiologicalSex.MALE ->
                    "vaginal sex, the $partner thrusting his penis inside the $primary, " +
                        "penis visibly in the vagina, both nude"
                plan.primary.sex == BiologicalSex.MALE && plan.partner?.sex == BiologicalSex.FEMALE ->
                    "vaginal sex, the $primary thrusting his penis inside the $partner, " +
                        "penis visibly in the vagina, both nude"
                else ->
                    "two nude adult women, explicit tribbing or vaginal penetration with a visible shaft, " +
                        "both vulvas in view"
            }
            AdultActionType.ANAL -> when {
                plan.solo ->
                    "$primary nude presenting the ass, explicit anal penetration, " +
                        "visible anus stretched around a shaft or fingers, $primaryGenitals in view"
                plan.partner?.sex == BiologicalSex.MALE ->
                    "anal sex, the $partner penetrating the $primary anally, " +
                        "penis inside the anus, both nude, $partnerGenitals visible"
                plan.primary.sex == BiologicalSex.MALE ->
                    "anal sex, the $primary penetrating the $partner anally, " +
                        "penis inside the anus, both nude"
                else ->
                    "two nude adult women, explicit anal sex, visible anus and vulvas, penetration in progress"
            }
        }
    }
}
