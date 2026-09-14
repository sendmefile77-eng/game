package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.AdultActionPlan
import com.sendmefile77.chronosphere.AdultActionType
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

object HordeAdultActionPromptFactory {
    private val nsfwModels = listOf("CyberRealistic Pony", "AbsoluteReality", "Realistic Vision", "WAI-NSFW-illustrious-SDXL")

    fun create(scene: ResolvedScene, plan: AdultActionPlan, visualTags: Set<String> = emptySet(), visualNumeric: Map<String, Double> = emptyMap(), technologyEra: TechnologyEra? = null, adultVisual: AdultVisualSceneDescriptor? = null): HordeImageRequest {
        require(plan.primary.ageYears >= 18)
        require(plan.partner == null || plan.partner.ageYears >= 18)
        val identity = HordeCharacterVisualProfile.from(plan.primary.personId)
        val morphology = HordeMorphologyVisual.from(visualTags, visualNumeric)
        val chimeric = morphology.signature != "baseline" && morphology.promptFragment.isNotBlank()
        val base = HordeResolvedScenePromptFactory.create(scene = scene.copy(wardrobeState = WardrobeState.UNDRESSED), characterKey = plan.primary.personId, ageYears = plan.primary.ageYears, visualTags = visualTags, visualNumeric = visualNumeric, technologyEra = technologyEra)
        val partnerIdentity = plan.partner?.let { HordeCharacterVisualProfile.from(it.personId) }
        val eraName = technologyEra?.name ?: "UNSPECIFIED"
        val recipeSetting = adultVisual?.settingKey?.takeIf { it.isNotBlank() }?.let { "structured scene setting ${it.replace('.', ' ').replace('-', ' ')}" }
        val partnerLine = plan.partner?.let { partner -> "second confirmed adult age ${partner.ageYears}, ${partnerIdentity?.promptFragment}, fully nude" }
        val historicalBase = HordeAdultSubjectGuard.sanitize(HordeHistoricalVisualPrompt.fragment(visualTags, technologyEra))
        val adultOverlay = HordeAdultVisualEnrichment.fragment(visualTags, technologyEra, HordeAdultVisualEnrichment.Kind.SCENE)
        val adultSignature = HordeAdultVisualEnrichment.signature(visualTags)
        val positiveRaw = listOf(
            actionPrompt(plan),
            actLock(plan.type),
            cameraPrompt(plan),
            "explicit uncensored adult sex, adults 18+",
            HordeAdultSubjectGuard.PERSON_LOCK,
            HordeAdultSubjectGuard.IDENTITY_LOCK,
            if (chimeric) HordeAdultSubjectGuard.CHIMERA_LOCK else null,
            "primary adult face locked: ${identity.promptFragment}",
            partnerLine,
            "completely nude, genitals visible, sexual contact readable at a glance",
            HordeEraVisual.intimateInterior(technologyEra),
            HordeEraVisual.distinctiveMarker(technologyEra),
            HordeEraVisual.materialCulture(technologyEra),
            historicalBase.takeIf { it.isNotBlank() },
            adultOverlay.takeIf { it.isNotBlank() },
            recipeSetting,
            morphology.promptFragment.takeIf { it.isNotBlank() },
            "environment matches era $eraName, no modern kitchen, no tiled bathroom",
            "no text in image",
        ).filter { !it.isNullOrBlank() }.joinToString(", ")
        val positive = HordeAdultSubjectGuard.sanitize(positiveRaw)
        return base.copy(
            cacheKey = listOf("horde-adult-action-v10", plan.cacheToken, identity.signature, morphology.signature, eraName, adultVisual?.recipeId ?: "none", adultVisual?.settingKey ?: "none", adultSignature).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = buildList {
                add(HordeImageRequest.DEFAULT_NEGATIVE_PROMPT)
                addAll(listOf("child", "minor", "teen", "underage", "loli", "shota", "clothing", "dress", "robe", "shirt", "pants", "underwear", "armor", "censored", "mosaic", "black bars", "standing idle portrait", "passport photo", "studio portrait", "posed fashion nude", "just standing", "arms at sides", "standing side by side", "cheek to cheek", "two girls posing", "face close-up only", "kissing as the main subject", "no sexual contact", "closed mouth far from genitals", "bust crop", "portrait crop", "missing feet", "cropped head", "wrong person", "identity change", "wrong sex act", "mismatched sex act", "modern kitchen", "kitchen counter", "office interior"))
                addAll(HordeAdultSubjectGuard.animalSubjectNegatives(chimeric))
                addAll(wrongActNegatives(plan.type))
                addAll(HordeEraVisual.negatives(technologyEra))
                if (plan.type == AdultActionType.BUKKAKE) { add("single person"); add("solo portrait") } else if (!plan.solo) { add("single person"); add("solo portrait"); add("only one body"); add("crowded group of three or more people") } else { add("unrelated extra people") }
            }.joinToString(", "),
            nsfw = true,
            width = if (plan.type == AdultActionType.BUKKAKE || plan.type == AdultActionType.BDSM) 896 else if (plan.type == AdultActionType.FOOTJOB) 832 else if (plan.solo) 768 else 832,
            height = if (plan.type == AdultActionType.BUKKAKE) 1152 else if (plan.type == AdultActionType.FOOTJOB) 1216 else if (plan.solo) 1152 else 1216,
            steps = 24, cfgScale = 6.0, seed = "${base.seed}:action-v10:$eraName:${plan.cacheToken}", preferredModels = nsfwModels, qualityPriority = true, referenceCacheKey = base.referenceCacheKey, saveResultAsReference = false, referenceDenoisingStrength = 0.34,
        )
    }

    private fun actLock(type: AdultActionType): String = when (type) {
        AdultActionType.FOOTJOB -> "the only sex act in this image is a footjob: bare feet and toes wrapped around genitals, not oral sex, not vaginal sex, not anal sex, not kissing"
        AdultActionType.ORAL -> "the only sex act in this image is oral sex: a mouth on genitals, not a footjob, not vaginal penetration, not anal sex, not a standing kiss"
        AdultActionType.VAGINAL -> "the only sex act in this image is vaginal sex: a shaft visibly inside a vagina, not a footjob, not oral sex, not anal sex, not a standing kiss"
        AdultActionType.ANAL -> "the only sex act in this image is anal sex: a shaft or fingers visibly inside an anus, not a footjob, not oral sex, not vaginal sex, not kissing"
        AdultActionType.BUKKAKE -> "the only sex act in this image is bukkake: several adult penises ejaculating onto the primary adult's face and breasts, not a standing kiss, not a footjob"
        AdultActionType.MASTURBATION -> "the only sex act in this image is masturbation: the primary adult's own hands on their genitals, orgasm in progress, not a partnered sex act, not kissing"
        AdultActionType.BDSM -> "the only sex act in this image is BDSM: visible restraints, collar or rope, dominant and submissive adults, not vanilla standing nudes, not a kiss portrait"
        AdultActionType.FUTANARI_ORGASM -> "the only sex act in this image is a futanari orgasm: adult woman with both breasts and an erect penis, ejaculating, not a clothed portrait, not a kiss"
    }

    private fun cameraPrompt(plan: AdultActionPlan): String = when (plan.type) {
        AdultActionType.FOOTJOB -> "low three-quarter camera aimed at bare feet on genitals, soles and toes in the foreground, hips and crotch visible, faces secondary"
        AdultActionType.ORAL -> "camera on the mouth-to-genital contact, lips around shaft or vulva filling the mid-frame"
        AdultActionType.VAGINAL -> "side or three-quarter camera showing hips joined, shaft entering the vagina"
        AdultActionType.ANAL -> "rear three-quarter camera on hips and ass, anus stretched around a shaft or fingers"
        AdultActionType.BUKKAKE -> "close three-quarter on the primary adult's face, breasts and torso, several erect adult penises around the head, semen landing on skin"
        AdultActionType.MASTURBATION -> "camera on the primary adult's hands and genitals, sitting or lying"
        AdultActionType.BDSM -> "camera showing restraints and the sexual contact together, bound wrists or collar in frame"
        AdultActionType.FUTANARI_ORGASM -> "camera on the erect penis attached to an adult female body, semen leaving the shaft, breasts and penis both visible"
    }

    private fun wrongActNegatives(type: AdultActionType): List<String> = when (type) {
        AdultActionType.FOOTJOB -> listOf("blowjob", "cunnilingus", "vaginal penetration", "anal penetration", "kissing mouths", "two faces touching")
        AdultActionType.ORAL -> listOf("footjob", "feet on genitals", "vaginal penetration", "anal penetration", "standing kiss")
        AdultActionType.VAGINAL -> listOf("footjob", "blowjob as the main act", "anal penetration as the main act", "standing kiss")
        AdultActionType.ANAL -> listOf("footjob", "vaginal sex as the main act", "kissing mouths", "standing side by side nudes")
        AdultActionType.BUKKAKE -> listOf("footjob", "standing kiss", "no semen", "dry face", "only two people posing")
        AdultActionType.MASTURBATION -> listOf("penetration as the main act", "footjob", "group sex", "standing kiss")
        AdultActionType.BDSM -> listOf("vanilla standing nudes", "no restraints", "fashion pose", "standing kiss")
        AdultActionType.FUTANARI_ORGASM -> listOf("no penis", "clothed", "standing kiss", "only vulva no shaft")
    }

    private fun actionPrompt(plan: AdultActionPlan): String {
        val primary = if (plan.primary.sex == BiologicalSex.FEMALE) "adult woman" else "adult man"
        val partner = plan.partner?.let { if (it.sex == BiologicalSex.FEMALE) "adult woman" else "adult man" }
        val ff = plan.partner != null && plan.primary.sex == BiologicalSex.FEMALE && plan.partner?.sex == BiologicalSex.FEMALE
        val primaryGenitals = if (plan.primary.sex == BiologicalSex.FEMALE) "visible vulva, labia and clitoris" else "visible erect penis, scrotum and testicles"
        val partnerGenitals = when (plan.partner?.sex) { BiologicalSex.FEMALE -> "partner's visible vulva, labia and clitoris"; BiologicalSex.MALE -> "partner's visible erect penis, scrotum and testicles"; null -> primaryGenitals }
        return when (plan.type) {
            AdultActionType.FOOTJOB -> when {
                plan.solo -> "FOOTJOB: $primary lying nude, soles and toes wrapped around $primaryGenitals"
                ff -> "FOOTJOB: two nude adult women, bare soles and toes rubbing clitoris and labia, not kissing"
                plan.partner?.sex == BiologicalSex.MALE -> "FOOTJOB: $primary giving a footjob to the $partner, toes wrapped around the erect shaft"
                else -> "FOOTJOB: $primary giving a footjob to the $partner, bare feet on $partnerGenitals"
            }
            AdultActionType.ORAL -> when {
                plan.solo && plan.primary.sex == BiologicalSex.FEMALE -> "ORAL SEX: $primary on her knees nude, mouth around a visible erect penis, explicit blowjob"
                plan.solo -> "ORAL SEX: $primary nude, mouth on a vulva, explicit cunnilingus"
                plan.partner?.sex == BiologicalSex.MALE -> "ORAL SEX: $primary performing a blowjob on the $partner, mouth wrapped around the erect penis"
                else -> "ORAL SEX: $primary performing cunnilingus on the $partner, mouth on vulva"
            }
            AdultActionType.VAGINAL -> when {
                plan.solo && plan.primary.sex == BiologicalSex.FEMALE -> "VAGINAL SEX: $primary nude with legs spread, fingers or a shaft inside the vagina"
                plan.solo -> "VAGINAL SEX: $primary nude, erect penis penetrating a visible vagina"
                plan.primary.sex == BiologicalSex.FEMALE && plan.partner?.sex == BiologicalSex.MALE -> "VAGINAL SEX: the $partner thrusting his penis inside the $primary"
                plan.primary.sex == BiologicalSex.MALE && plan.partner?.sex == BiologicalSex.FEMALE -> "VAGINAL SEX: the $primary thrusting his penis inside the $partner"
                else -> "VAGINAL SEX: two nude adult women, vulvas pressed together or a visible shaft in a vagina"
            }
            AdultActionType.ANAL -> when {
                plan.solo -> "ANAL SEX: $primary nude presenting the ass, anus stretched around a shaft or fingers"
                ff -> "ANAL SEX: two nude adult women, receiving woman on knees, strap-on or fingers inside the anus, faces not touching"
                plan.partner?.sex == BiologicalSex.MALE -> "ANAL SEX: the $partner penetrating the $primary anally, penis inside the anus"
                plan.primary.sex == BiologicalSex.MALE -> "ANAL SEX: the $primary penetrating the $partner anally, penis inside the anus"
                else -> "ANAL SEX: two nude adults, explicit anal penetration in progress"
            }
            AdultActionType.BUKKAKE -> "BUKKAKE: $primary kneeling nude, several erect adult penises around her face and breasts, thick semen on face, tongue, hair and chest"
            AdultActionType.MASTURBATION -> if (plan.primary.sex == BiologicalSex.FEMALE) "MASTURBATION: $primary lying nude, fingers on $primaryGenitals, rubbing the clitoris, wet orgasm" else "MASTURBATION: $primary sitting nude, hand stroking $primaryGenitals, ejaculating"
            AdultActionType.BDSM -> if (plan.solo) "BDSM: $primary nude, wrists bound, collar on, genitals exposed" else "BDSM: $primary and the $partner both nude, rope or leather restraints, collar, one adult dominating the other"
            AdultActionType.FUTANARI_ORGASM -> "FUTANARI ORGASM: adult woman with breasts and an erect penis, $primary body, ejaculating hard, semen in the air"
        }
    }
}
