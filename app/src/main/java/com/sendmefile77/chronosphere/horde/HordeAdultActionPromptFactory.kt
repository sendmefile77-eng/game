package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.AdultActionPlan
import com.sendmefile77.chronosphere.AdultActionType
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene

/** Explicit character-card action scenes. Kept separate from event-recipe mapping. */
object HordeAdultActionPromptFactory {
    private val nsfwModels = listOf(
        "CyberRealistic Pony",
        "WAI-NSFW-illustrious-SDXL",
        "AbsoluteReality",
        "Realistic Vision",
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

        val base = HordeResolvedScenePromptFactory.create(
            scene = scene,
            characterKey = plan.primary.personId,
            ageYears = plan.primary.ageYears,
            visualTags = visualTags,
            visualNumeric = visualNumeric,
            technologyEra = technologyEra,
        )
        val partnerPrompt = plan.partner?.let { partner ->
            val identity = HordeCharacterVisualProfile.from(partner.personId)
            "second confirmed adult participant age ${partner.ageYears}, ${identity.promptFragment}, complete connected body"
        }
        val composition = if (plan.solo) {
            "solo explicit adult scene, only the selected adult is present, whole body visible from head to feet"
        } else {
            "two confirmed adults only, both whole bodies visible in one frame, readable sexual interaction, no extra people"
        }
        return base.copy(
            cacheKey = listOf(
                "horde-adult-action-v1",
                base.cacheKey,
                plan.cacheToken,
                adultVisual?.recipeId ?: "none",
            ).joinToString("|"),
            positivePrompt = listOf(
                "high quality photorealistic explicit pornographic scene from a living historical simulation",
                "all depicted participants are adults age 18 or older",
                base.positivePrompt,
                composition,
                actionPrompt(plan),
                partnerPrompt,
                "full-length bodies, no cropped heads, no cropped feet, no bust crop",
                "explicit visible adult genitals, uncensored",
                "keep the primary adult's face, hair, body plan and identity locked to the reference",
                "no text in image",
            ).filterNotNull().joinToString(", "),
            negativePrompt = listOf(
                base.negativePrompt,
                "child", "minor", "teen", "underage", "loli", "shota",
                "clothing covering genitals", "censored", "mosaic", "black bars",
                "bust crop", "portrait crop", "missing feet", "cropped head",
                "wrong person", "identity change", "unrelated extra people",
            ).joinToString(", "),
            nsfw = true,
            width = 768,
            height = 1152,
            steps = 34,
            cfgScale = 7.0,
            seed = "${base.seed}:action:${plan.cacheToken}",
            preferredModels = nsfwModels,
            qualityPriority = true,
            referenceCacheKey = base.referenceCacheKey,
            saveResultAsReference = false,
            referenceDenoisingStrength = 0.78,
        )
    }

    private fun actionPrompt(plan: AdultActionPlan): String {
        val primarySex = if (plan.primary.sex == BiologicalSex.FEMALE) "adult woman" else "adult man"
        val partnerSex = plan.partner?.let { if (it.sex == BiologicalSex.FEMALE) "adult woman" else "adult man" }
        val primaryGenitals = if (plan.primary.sex == BiologicalSex.FEMALE) {
            "visible vulva, labia and clitoris"
        } else {
            "visible erect penis, scrotum and testicles"
        }
        val partnerGenitals = when (plan.partner?.sex) {
            BiologicalSex.FEMALE -> "partner has visible vulva, labia and clitoris"
            BiologicalSex.MALE -> "partner has visible erect penis, scrotum and testicles"
            null -> "solo explicit genital focus"
        }
        return when (plan.type) {
            AdultActionType.FOOTJOB -> if (plan.solo) {
                "$primarySex reclining full-body, bare feet with $primaryGenitals, explicit autoerotic foot-and-genital contact, uncensored"
            } else {
                "$primarySex giving an explicit footjob to the $partnerSex, both complete bodies in frame, $partnerGenitals, uncensored"
            }
            AdultActionType.ORAL -> if (plan.solo) {
                "$primarySex full-body with $primaryGenitals, explicit autoerotic oral teasing, uncensored"
            } else if (plan.partner?.sex == BiologicalSex.MALE) {
                "$primarySex performing explicit oral sex on the $partnerSex, mouth on penis, $partnerGenitals, both complete bodies"
            } else {
                "$primarySex performing explicit cunnilingus on the $partnerSex, mouth on vulva, $partnerGenitals, both complete bodies"
            }
            AdultActionType.VAGINAL -> if (plan.solo) {
                "$primarySex full-body with legs open, $primaryGenitals, explicit vaginal masturbation, uncensored"
            } else if (plan.primary.sex == BiologicalSex.FEMALE && plan.partner?.sex == BiologicalSex.MALE) {
                "explicit vaginal sex, the $partnerSex penetrating the $primarySex, penis inside vagina, both complete bodies"
            } else if (plan.primary.sex == BiologicalSex.MALE && plan.partner?.sex == BiologicalSex.FEMALE) {
                "explicit vaginal sex, the $primarySex penetrating the $partnerSex, penis inside vagina, both complete bodies"
            } else {
                "explicit lesbian vaginal sex, two adult women, both vulvas visible, complete bodies, uncensored"
            }
            AdultActionType.ANAL -> if (plan.solo) {
                "$primarySex full-body presenting hips, visible anus and $primaryGenitals, explicit autoerotic anal play"
            } else if (plan.partner?.sex == BiologicalSex.MALE) {
                "explicit anal sex, the $partnerSex penetrating the $primarySex anally, both complete bodies, uncensored"
            } else if (plan.primary.sex == BiologicalSex.MALE) {
                "explicit anal sex, the $primarySex penetrating the $partnerSex anally, both complete bodies, uncensored"
            } else {
                "explicit anal lesbian sex, visible anus and vulvas, both complete bodies, uncensored"
            }
        }
    }
}
