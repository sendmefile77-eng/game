package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.AdultActionPlan
import com.sendmefile77.chronosphere.AdultActionType
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

/** Explicit character-card action scenes. Must not inherit idle-portrait composition. */
object HordeAdultActionPromptFactory {
    private val nsfwModels = listOf(
        "CyberRealistic Pony",
        "AbsoluteReality",
        "Realistic Vision",
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
        val act = actionPrompt(plan)
        val camera = cameraPrompt(plan)
        val eraName = technologyEra?.name ?: "UNSPECIFIED"
        val recipeSetting = adultVisual?.settingKey
            ?.takeIf { it.isNotBlank() }
            ?.let { "structured scene setting ${it.replace('.', ' ').replace('-', ' ')}" }

        val partnerLine = plan.partner?.let { partner ->
            "second confirmed adult age ${partner.ageYears}, ${partnerIdentity?.promptFragment}, fully nude"
        }

        val positive = listOf(
            act,
            actLock(plan.type),
            camera,
            "explicit uncensored adult sex, adults 18+",
            "primary adult face locked: ${identity.promptFragment}",
            partnerLine,
            "completely nude, genitals visible, sexual contact readable at a glance",
            HordeEraVisual.intimateInterior(technologyEra),
            HordeEraVisual.distinctiveMarker(technologyEra),
            HordeEraVisual.materialCulture(technologyEra),
            recipeSetting,
            morphology.promptFragment.takeIf { it.isNotBlank() },
            "environment matches era $eraName, no modern kitchen, no tiled bathroom",
            "no text in image",
        ).filter { !it.isNullOrBlank() }.joinToString(", ")

        return base.copy(
            cacheKey = listOf(
                "horde-adult-action-v7",
                plan.cacheToken,
                identity.signature,
                morphology.signature,
                eraName,
                adultVisual?.recipeId ?: "none",
                adultVisual?.settingKey ?: "none",
            ).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = buildList {
                add(HordeImageRequest.DEFAULT_NEGATIVE_PROMPT)
                addAll(
                    listOf(
                        "child", "minor", "teen", "underage", "loli", "shota",
                        "clothing", "dress", "robe", "shirt", "pants", "underwear", "armor",
                        "censored", "mosaic", "black bars",
                        "standing idle portrait", "passport photo", "studio portrait",
                        "posed fashion nude", "just standing", "arms at sides",
                        "standing side by side", "cheek to cheek", "two girls posing",
                        "face close-up only", "kissing as the main subject",
                        "no sexual contact", "closed mouth far from genitals",
                        "bust crop", "portrait crop", "missing feet", "cropped head",
                        "wrong person", "identity change",
                        "wrong sex act", "mismatched sex act",
                        "modern kitchen", "kitchen counter", "office interior",
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
            width = if (plan.type == AdultActionType.FOOTJOB) 832 else if (plan.solo) 768 else 832,
            height = if (plan.type == AdultActionType.FOOTJOB) 1216 else if (plan.solo) 1152 else 1216,
            steps = 24,
            cfgScale = 6.0,
            seed = "${base.seed}:action-v7:$eraName:${plan.cacheToken}",
            preferredModels = nsfwModels,
            qualityPriority = true,
            referenceCacheKey = base.referenceCacheKey,
            saveResultAsReference = false,
            referenceDenoisingStrength = 0.34,
        )
    }

    private fun actLock(type: AdultActionType): String = when (type) {
        AdultActionType.FOOTJOB ->
            "the only sex act in this image is a footjob: bare feet and toes wrapped around genitals, " +
                "not oral sex, not vaginal sex, not anal sex, not kissing"
        AdultActionType.ORAL ->
            "the only sex act in this image is oral sex: a mouth on genitals, " +
                "not a footjob, not vaginal penetration, not anal sex, not a standing kiss"
        AdultActionType.VAGINAL ->
            "the only sex act in this image is vaginal sex: a shaft visibly inside a vagina, " +
                "not a footjob, not oral sex, not anal sex, not a standing kiss"
        AdultActionType.ANAL ->
            "the only sex act in this image is anal sex: a shaft or fingers visibly inside an anus, " +
                "not a footjob, not oral sex, not vaginal sex, not kissing"
    }

    private fun cameraPrompt(plan: AdultActionPlan): String = when (plan.type) {
        AdultActionType.FOOTJOB ->
            "low three-quarter camera aimed at bare feet on genitals, soles and toes in the foreground, " +
                "hips and crotch visible, faces secondary, not a head-and-shoulders crop"
        AdultActionType.ORAL ->
            "camera on the mouth-to-genital contact, lips around shaft or vulva filling the mid-frame, " +
                "kneeling or lying pose, faces not the only subject"
        AdultActionType.VAGINAL ->
            "side or three-quarter camera showing hips joined, shaft entering the vagina, " +
                "bodies lying or kneeling, not standing idle"
        AdultActionType.ANAL ->
            "rear three-quarter camera on hips and ass, anus stretched around a shaft or fingers, " +
                "receiving adult on knees or bent forward, not face-to-face standing"
    }

    private fun wrongActNegatives(type: AdultActionType): List<String> = when (type) {
        AdultActionType.FOOTJOB -> listOf(
            "blowjob", "cunnilingus", "mouth on penis", "vaginal penetration", "anal penetration",
            "missionary sex", "kissing mouths", "two faces touching",
        )
        AdultActionType.ORAL -> listOf(
            "footjob", "feet on genitals", "soles on penis", "vaginal penetration", "anal penetration",
            "standing kiss",
        )
        AdultActionType.VAGINAL -> listOf(
            "footjob", "feet on genitals", "blowjob as the main act", "anal penetration as the main act",
            "standing kiss",
        )
        AdultActionType.ANAL -> listOf(
            "footjob", "feet on genitals", "vaginal sex as the main act", "blowjob as the main act",
            "kissing mouths", "two faces touching", "standing side by side nudes",
        )
    }

    private fun actionPrompt(plan: AdultActionPlan): String {
        val woman = "adult woman"
        val man = "adult man"
        val primary = if (plan.primary.sex == BiologicalSex.FEMALE) woman else man
        val partner = plan.partner?.let { if (it.sex == BiologicalSex.FEMALE) woman else man }
        val ff = plan.partner != null &&
            plan.primary.sex == BiologicalSex.FEMALE &&
            plan.partner?.sex == BiologicalSex.FEMALE
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
            AdultActionType.FOOTJOB -> when {
                plan.solo ->
                    "FOOTJOB: $primary lying nude, soles and toes wrapped around $primaryGenitals, " +
                        "explicit solo footjob, genitals squeezed between bare feet"
                ff ->
                    "FOOTJOB: two nude adult women, one lying back with legs apart, " +
                        "the other woman's bare soles and toes rubbing the first woman's clitoris and labia, " +
                        "feet-on-vulva contact is the center of the frame, not kissing"
                plan.partner?.sex == BiologicalSex.MALE ->
                    "FOOTJOB: $primary giving a footjob to the $partner, bare feet stroking $partnerGenitals, " +
                        "toes and soles wrapped around the erect shaft"
                else ->
                    "FOOTJOB: $primary giving a footjob to the $partner, bare feet on $partnerGenitals, both nude"
            }
            AdultActionType.ORAL -> when {
                plan.solo && plan.primary.sex == BiologicalSex.FEMALE ->
                    "ORAL SEX: $primary on her knees nude, mouth open around a visible erect penis, explicit blowjob"
                plan.solo ->
                    "ORAL SEX: $primary nude, mouth on a vulva, explicit cunnilingus, tongue on clitoris"
                plan.partner?.sex == BiologicalSex.MALE ->
                    "ORAL SEX: $primary performing a blowjob on the $partner, mouth wrapped around the erect penis"
                else ->
                    "ORAL SEX: $primary performing cunnilingus on the $partner, mouth on vulva, tongue on $partnerGenitals"
            }
            AdultActionType.VAGINAL -> when {
                plan.solo && plan.primary.sex == BiologicalSex.FEMALE ->
                    "VAGINAL SEX: $primary nude with legs spread, fingers or a shaft inside the vagina"
                plan.solo ->
                    "VAGINAL SEX: $primary nude, erect penis penetrating a visible vagina"
                plan.primary.sex == BiologicalSex.FEMALE && plan.partner?.sex == BiologicalSex.MALE ->
                    "VAGINAL SEX: the $partner thrusting his penis inside the $primary, penis visibly in the vagina"
                plan.primary.sex == BiologicalSex.MALE && plan.partner?.sex == BiologicalSex.FEMALE ->
                    "VAGINAL SEX: the $primary thrusting his penis inside the $partner, penis visibly in the vagina"
                else ->
                    "VAGINAL SEX: two nude adult women, scissoring with both vulvas pressed together or a visible shaft in a vagina, " +
                        "hips joined, not a standing kiss"
            }
            AdultActionType.ANAL -> when {
                plan.solo ->
                    "ANAL SEX: $primary nude presenting the ass, anus stretched around a shaft or fingers, $primaryGenitals in view"
                ff ->
                    "ANAL SEX: two nude adult women, receiving woman on knees chest down, " +
                        "inserting woman behind her with fingers or a strap-on shaft visibly inside the anus, " +
                        "ass and penetration fill the frame, faces not touching"
                plan.partner?.sex == BiologicalSex.MALE ->
                    "ANAL SEX: the $partner penetrating the $primary anally, penis inside the anus, both nude"
                plan.primary.sex == BiologicalSex.MALE ->
                    "ANAL SEX: the $primary penetrating the $partner anally, penis inside the anus, both nude"
                else ->
                    "ANAL SEX: two nude adults, explicit anal penetration in progress, anus visible around the shaft"
            }
        }
    }
}
