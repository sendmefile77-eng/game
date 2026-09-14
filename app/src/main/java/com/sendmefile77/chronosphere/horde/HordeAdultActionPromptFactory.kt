package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.AdultActionPlan
import com.sendmefile77.chronosphere.AdultActionType
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

object HordeAdultActionPromptFactory {
    private const val ACTION_SCHEMA = "horde-adult-action-v12"

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
        val chimeric = morphology.signature != "baseline" && morphology.promptFragment.isNotBlank()
        val base = HordeResolvedScenePromptFactory.create(
            scene = scene.copy(wardrobeState = WardrobeState.UNDRESSED),
            characterKey = plan.primary.personId,
            ageYears = plan.primary.ageYears,
            visualTags = visualTags,
            visualNumeric = visualNumeric,
            technologyEra = technologyEra,
        )
        val partnerIdentity = plan.partner?.let { HordeCharacterVisualProfile.from(it.personId) }
        val eraName = technologyEra?.name ?: "UNSPECIFIED"
        val recipeSetting = adultVisual?.settingKey
            ?.takeIf { it.isNotBlank() }
            ?.let { "structured scene setting ${it.replace('.', ' ').replace('-', ' ')}" }
        val partnerLine = plan.partner?.let { partner ->
            "second confirmed adult age ${partner.ageYears}, ${partnerIdentity?.promptFragment}, fully nude"
        }
        val historicalBase = HordeAdultSubjectGuard.sanitize(
            HordeHistoricalVisualPrompt.fragment(visualTags, technologyEra),
        )
        val adultOverlay = HordeAdultVisualEnrichment.fragment(
            visualTags,
            technologyEra,
            HordeAdultVisualEnrichment.Kind.SCENE,
        )
        val adultSignature = HordeAdultVisualEnrichment.signature(visualTags)

        // Action comes first on purpose. Local Dream compresses long prompts, so the selected act must
        // remain the strongest visual instruction before era, mood and decorative context are added.
        val positiveRaw = listOf(
            actionPrompt(plan),
            actLock(plan.type),
            cameraPrompt(plan),
            "consensual explicit adult scene, all participants are fictional adults age 18+",
            HordeAdultSubjectGuard.PERSON_LOCK,
            HordeAdultSubjectGuard.IDENTITY_LOCK,
            if (chimeric) HordeAdultSubjectGuard.CHIMERA_LOCK else null,
            "primary adult face locked: ${identity.promptFragment}",
            partnerLine,
            "completely nude, selected sexual act readable at a glance",
            HordeEraVisual.intimateInterior(technologyEra),
            HordeEraVisual.distinctiveMarker(technologyEra),
            HordeEraVisual.materialCulture(technologyEra),
            historicalBase.takeIf { it.isNotBlank() },
            adultOverlay.takeIf { it.isNotBlank() },
            plan.setting.takeIf { it.isNotBlank() },
            plan.mood.takeIf { it.isNotBlank() },
            plan.bond?.name?.lowercase()?.let { "relationship bond $it" },
            recipeSetting,
            morphology.promptFragment.takeIf { it.isNotBlank() },
            "environment matches era $eraName, no modern kitchen, no tiled bathroom",
            "no text in image",
        ).filter { !it.isNullOrBlank() }.joinToString(", ")
        val positive = HordeAdultSubjectGuard.sanitize(positiveRaw)

        return base.copy(
            cacheKey = listOf(
                ACTION_SCHEMA,
                plan.cacheToken,
                identity.signature,
                morphology.signature,
                eraName,
                adultVisual?.recipeId ?: "none",
                adultVisual?.settingKey ?: "none",
                adultSignature,
            ).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = buildList {
                add(HordeImageRequest.DEFAULT_NEGATIVE_PROMPT)
                addAll(
                    listOf(
                        "child", "minor", "teen", "underage", "loli", "shota",
                        "clothing", "dress", "robe", "shirt", "pants", "underwear", "armor",
                        "censored", "mosaic", "black bars", "standing idle portrait", "passport photo",
                        "studio portrait", "posed fashion nude", "just standing", "arms at sides",
                        "standing side by side", "cheek to cheek", "two girls posing", "face close-up only",
                        "kissing as the main subject", "no sexual contact", "bust crop", "portrait crop",
                        "missing feet", "cropped head", "wrong person", "identity change", "wrong sex act",
                        "mismatched sex act", "modern kitchen", "kitchen counter", "office interior",
                    ),
                )
                addAll(HordeAdultSubjectGuard.animalSubjectNegatives(chimeric))
                addAll(wrongActNegatives(plan.type))
                addAll(HordeEraVisual.negatives(technologyEra))
                when (plan.type) {
                    AdultActionType.BUKKAKE, AdultActionType.MMF, AdultActionType.FFM -> {
                        add("single person")
                        add("solo portrait")
                        add("only one body")
                    }
                    else -> if (!plan.solo) {
                        add("single person")
                        add("solo portrait")
                        add("only one body")
                        add("crowded group of three or more people")
                    } else {
                        add("unrelated extra people")
                    }
                }
            }.joinToString(", "),
            nsfw = true,
            width = frameWidth(plan),
            height = frameHeight(plan),
            steps = 24,
            cfgScale = 6.0,
            seed = "${base.seed}:action-v12:$eraName:${plan.cacheToken}",
            preferredModels = nsfwModels,
            qualityPriority = true,
            referenceCacheKey = base.referenceCacheKey,
            saveResultAsReference = false,
            referenceDenoisingStrength = 0.34,
        )
    }

    private fun frameWidth(plan: AdultActionPlan): Int = when (plan.type) {
        AdultActionType.BUKKAKE,
        AdultActionType.BDSM,
        AdultActionType.MMF,
        AdultActionType.FFM,
        -> 896

        AdultActionType.FOOTJOB,
        AdultActionType.SIXTY_NINE,
        AdultActionType.PAIZURI,
        AdultActionType.SCISSORING,
        -> 832

        else -> if (plan.solo) 768 else 832
    }

    private fun frameHeight(plan: AdultActionPlan): Int = when (plan.type) {
        AdultActionType.BUKKAKE,
        AdultActionType.MMF,
        AdultActionType.FFM,
        -> 1152

        AdultActionType.FOOTJOB,
        AdultActionType.SIXTY_NINE,
        AdultActionType.PAIZURI,
        AdultActionType.SCISSORING,
        -> 1216

        else -> if (plan.solo) 1152 else 1216
    }

    private fun actLock(type: AdultActionType): String = when (type) {
        AdultActionType.FOOTJOB -> "selected act lock: footjob only, bare feet and toes visibly doing the stimulation; do not replace with oral, vaginal, anal or kissing"
        AdultActionType.HANDJOB -> "selected act lock: handjob only, a hand visibly stroking genitals; do not replace with oral, intercourse, footjob or kissing"
        AdultActionType.ORAL -> "selected act lock: blowjob only, mouth visibly on penis; do not replace with cunnilingus, footjob, intercourse or kissing"
        AdultActionType.CUNNILINGUS -> "selected act lock: cunnilingus only, mouth visibly on vulva; do not replace with blowjob, footjob, intercourse or kissing"
        AdultActionType.SIXTY_NINE -> "selected act lock: 69 only, both adults simultaneously performing oral sex on each other; do not collapse into one-way oral or kissing"
        AdultActionType.VAGINAL -> "selected act lock: vaginal intercourse only, vaginal penetration clearly readable; do not replace with footjob, oral, anal or kissing"
        AdultActionType.ANAL -> "selected act lock: anal intercourse only, anal penetration clearly readable; do not replace with vaginal, oral, footjob or kissing"
        AdultActionType.PAIZURI -> "selected act lock: paizuri only, penis visibly between breasts; do not replace with intercourse, oral, handjob or posing"
        AdultActionType.SCISSORING -> "selected act lock: scissoring or tribadism only, two adult women with vulva-to-vulva contact; do not replace with kissing or generic posing"
        AdultActionType.MUTUAL_MASTURBATION -> "selected act lock: mutual masturbation only, both adults visibly using hands for genital stimulation; do not replace with penetration or oral"
        AdultActionType.BUKKAKE -> "selected act lock: bukkake only, multiple adult men ejaculating onto the primary adult; do not replace with ordinary group intercourse or posing"
        AdultActionType.FACIAL -> "selected act lock: facial finish only, ejaculation visibly landing on an adult partner's face; do not replace with bukkake crowding or intercourse"
        AdultActionType.CREAMPIE -> "selected act lock: creampie finish only, vaginal intercourse with internal ejaculation and visible post-climax evidence; do not replace with facial or external finish"
        AdultActionType.MASTURBATION -> "selected act lock: solo masturbation only, the primary adult using their own hands on their genitals; do not invent a partner"
        AdultActionType.BDSM -> "selected act lock: consensual BDSM only, restraints or collar and a clear adult dominance/submission dynamic; do not replace with vanilla posing"
        AdultActionType.MMF -> "selected act lock: MMF threesome only, one adult woman and two adult men visibly participating; do not reduce to a couple"
        AdultActionType.FFM -> "selected act lock: FFM threesome only, two adult women and one adult man visibly participating; do not reduce to a couple"
        AdultActionType.FUTANARI_ORGASM -> "selected act lock: futanari orgasm only, adult woman with breasts and an erect penis visibly climaxing; do not replace with ordinary nude posing"
    }

    private fun cameraPrompt(plan: AdultActionPlan): String = when (plan.type) {
        AdultActionType.FOOTJOB -> "low three-quarter camera with the feet-to-genital contact centered and unobstructed"
        AdultActionType.HANDJOB -> "three-quarter camera with the stroking hand and genitals centered and clearly visible"
        AdultActionType.ORAL -> "camera centered on mouth-to-penis contact with both adult bodies still readable"
        AdultActionType.CUNNILINGUS -> "camera centered on mouth-to-vulva contact with hips and face clearly readable"
        AdultActionType.SIXTY_NINE -> "side three-quarter camera showing both simultaneous oral contacts in the same frame"
        AdultActionType.VAGINAL -> "side or three-quarter camera showing vaginal penetration and joined hips unobstructed"
        AdultActionType.ANAL -> "rear three-quarter camera showing anal penetration and both adult bodies unobstructed"
        AdultActionType.PAIZURI -> "upper-body three-quarter camera with breasts and penis centered while keeping both faces visible"
        AdultActionType.SCISSORING -> "low side camera showing both women's hips, legs and vulva-to-vulva contact"
        AdultActionType.MUTUAL_MASTURBATION -> "medium three-quarter camera showing both adults' hands and genital contact at once"
        AdultActionType.BUKKAKE -> "close three-quarter camera on the primary adult's face and torso with multiple adult participants around them"
        AdultActionType.FACIAL -> "close three-quarter camera on the receiving adult's face and the visible ejaculation source"
        AdultActionType.CREAMPIE -> "intimate three-quarter camera on joined hips and immediate post-climax vaginal contact"
        AdultActionType.MASTURBATION -> "camera on the primary adult's hands and genitals while keeping the whole adult body readable"
        AdultActionType.BDSM -> "camera showing restraints and the consensual sexual interaction together in one readable composition"
        AdultActionType.MMF -> "wide-enough three-quarter camera for three adult bodies, one woman and two men, with no participant cropped out"
        AdultActionType.FFM -> "wide-enough three-quarter camera for three adult bodies, two women and one man, with no participant cropped out"
        AdultActionType.FUTANARI_ORGASM -> "camera showing adult female face, breasts and erect penis together, with climax clearly readable"
    }

    private fun wrongActNegatives(type: AdultActionType): List<String> = when (type) {
        AdultActionType.FOOTJOB -> listOf("blowjob", "cunnilingus", "vaginal penetration", "anal penetration", "kissing mouths")
        AdultActionType.HANDJOB -> listOf("footjob", "blowjob", "cunnilingus", "vaginal penetration", "anal penetration")
        AdultActionType.ORAL -> listOf("cunnilingus", "footjob", "vaginal penetration", "anal penetration", "standing kiss")
        AdultActionType.CUNNILINGUS -> listOf("blowjob", "footjob", "vaginal penetration", "anal penetration", "standing kiss")
        AdultActionType.SIXTY_NINE -> listOf("one-way blowjob", "one-way cunnilingus", "standing kiss", "separate bodies")
        AdultActionType.VAGINAL -> listOf("footjob", "blowjob as the main act", "anal penetration as the main act", "standing kiss")
        AdultActionType.ANAL -> listOf("footjob", "vaginal sex as the main act", "kissing mouths", "standing side by side nudes")
        AdultActionType.PAIZURI -> listOf("handjob", "blowjob", "vaginal penetration", "anal penetration", "breasts not touching penis")
        AdultActionType.SCISSORING -> listOf("male-only scene", "penis penetration", "standing kiss", "women posing side by side")
        AdultActionType.MUTUAL_MASTURBATION -> listOf("penetration as the main act", "oral sex as the main act", "single person", "standing kiss")
        AdultActionType.BUKKAKE -> listOf("footjob", "standing kiss", "no semen", "dry face", "only two people posing")
        AdultActionType.FACIAL -> listOf("bukkake crowd", "no semen on face", "intercourse as main act", "dry face")
        AdultActionType.CREAMPIE -> listOf("facial", "external ejaculation", "condom", "no vaginal contact")
        AdultActionType.MASTURBATION -> listOf("penetration as the main act", "footjob", "group sex", "standing kiss")
        AdultActionType.BDSM -> listOf("vanilla standing nudes", "no restraints", "fashion pose", "standing kiss")
        AdultActionType.MMF -> listOf("only two people", "two women one man", "solo portrait", "couple portrait")
        AdultActionType.FFM -> listOf("only two people", "one woman two men", "solo portrait", "couple portrait")
        AdultActionType.FUTANARI_ORGASM -> listOf("no penis", "clothed", "standing kiss", "only vulva no shaft")
    }

    private fun actionPrompt(plan: AdultActionPlan): String {
        val primary = if (plan.primary.sex == BiologicalSex.FEMALE) "adult woman" else "adult man"
        val partner = plan.partner?.let { if (it.sex == BiologicalSex.FEMALE) "adult woman" else "adult man" }
        val ff = plan.partner != null &&
            plan.primary.sex == BiologicalSex.FEMALE &&
            plan.partner?.sex == BiologicalSex.FEMALE
        val primaryGenitals = if (plan.primary.sex == BiologicalSex.FEMALE) {
            "visible adult vulva"
        } else {
            "visible adult penis"
        }
        val partnerGenitals = when (plan.partner?.sex) {
            BiologicalSex.FEMALE -> "partner's visible adult vulva"
            BiologicalSex.MALE -> "partner's visible adult penis"
            null -> primaryGenitals
        }

        return when (plan.type) {
            AdultActionType.FOOTJOB -> when {
                plan.solo -> "FOOTJOB: $primary nude, bare soles and toes visibly stimulating $primaryGenitals"
                ff -> "FOOTJOB: two nude adult women, one woman's bare feet visibly stimulating the other adult woman's vulva"
                else -> "FOOTJOB: $primary and $partner nude, bare feet visibly stimulating $partnerGenitals"
            }
            AdultActionType.HANDJOB -> when {
                plan.solo -> "HANDJOB: $primary nude, one hand visibly stroking $primaryGenitals"
                else -> "HANDJOB: $primary and $partner nude, one adult's hand visibly stroking $partnerGenitals"
            }
            AdultActionType.ORAL -> when {
                plan.partner?.sex == BiologicalSex.MALE -> "BLOWJOB: $primary performing oral sex on the $partner, mouth visibly on the adult penis"
                plan.primary.sex == BiologicalSex.MALE -> "BLOWJOB: the $partner performing oral sex on the $primary, mouth visibly on the adult penis"
                else -> "BLOWJOB: two confirmed nude adults, oral stimulation centered on an adult penis"
            }
            AdultActionType.CUNNILINGUS -> when {
                plan.primary.sex == BiologicalSex.FEMALE -> "CUNNILINGUS: the $partner performing oral sex on the $primary, mouth visibly on the adult vulva"
                plan.partner?.sex == BiologicalSex.FEMALE -> "CUNNILINGUS: the $primary performing oral sex on the $partner, mouth visibly on the adult vulva"
                else -> "CUNNILINGUS: consensual adult oral stimulation centered on a clearly visible adult vulva"
            }
            AdultActionType.SIXTY_NINE -> "69: $primary and $partner nude in a clear sixty-nine position, both adults simultaneously performing oral sex on each other"
            AdultActionType.VAGINAL -> when {
                plan.solo && plan.primary.sex == BiologicalSex.FEMALE -> "VAGINAL SEX: $primary nude, visible vaginal penetration with fingers or an adult toy"
                plan.primary.sex == BiologicalSex.FEMALE && plan.partner?.sex == BiologicalSex.MALE -> "VAGINAL SEX: the $partner penetrating the $primary vaginally"
                plan.primary.sex == BiologicalSex.MALE && plan.partner?.sex == BiologicalSex.FEMALE -> "VAGINAL SEX: the $primary penetrating the $partner vaginally"
                else -> "VAGINAL SEX: confirmed nude adults with vaginal penetration clearly visible"
            }
            AdultActionType.ANAL -> when {
                plan.solo -> "ANAL SEX: $primary nude, visible anal penetration with fingers or an adult toy"
                else -> "ANAL SEX: $primary and $partner nude, anal penetration clearly visible"
            }
            AdultActionType.PAIZURI -> when {
                plan.primary.sex == BiologicalSex.FEMALE && plan.partner?.sex == BiologicalSex.MALE -> "PAIZURI: the $primary using her breasts around the $partner's adult penis"
                plan.primary.sex == BiologicalSex.MALE && plan.partner?.sex == BiologicalSex.FEMALE -> "PAIZURI: the $partner using her breasts around the $primary's adult penis"
                else -> "PAIZURI: consensual adult breast sex, an adult penis visibly held between breasts"
            }
            AdultActionType.SCISSORING -> "SCISSORING: two nude adult women in a clear tribadism position, hips joined and vulva-to-vulva contact visible"
            AdultActionType.MUTUAL_MASTURBATION -> "MUTUAL MASTURBATION: $primary and $partner nude, both adults visibly using hands for genital stimulation at the same time"
            AdultActionType.BUKKAKE -> "BUKKAKE: primary fictional adult kneeling nude, several confirmed adult men around the primary adult, visible group climax on face and torso"
            AdultActionType.FACIAL -> "FACIAL: two confirmed nude adults, visible ejaculation onto the receiving adult's face, selected finish centered in frame"
            AdultActionType.CREAMPIE -> "CREAMPIE: two confirmed nude adults in vaginal intercourse, internal ejaculation as the selected climax, immediate post-climax contact visible"
            AdultActionType.MASTURBATION -> "MASTURBATION: $primary alone and nude, own hand visibly stimulating $primaryGenitals, no partner present"
            AdultActionType.BDSM -> if (plan.solo) {
                "BDSM: $primary nude in a consensual restraint scene, wrists bound or collar visible, no second person invented"
            } else {
                "BDSM: $primary and $partner nude in a consensual dominance/submission scene, restraints or collar clearly visible"
            }
            AdultActionType.MMF -> "MMF THREESOME: exactly three fictional adults, one adult woman and two adult men, all three visibly participating in the selected consensual scene"
            AdultActionType.FFM -> "FFM THREESOME: exactly three fictional adults, two adult women and one adult man, all three visibly participating in the selected consensual scene"
            AdultActionType.FUTANARI_ORGASM -> "FUTANARI ORGASM: fictional adult woman with breasts and an erect penis visibly climaxing, adult anatomy clear in frame"
        }
    }
}
