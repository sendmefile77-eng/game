package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.simulation.SimulationEvent

/** Converts semantic adult-module recipes into concrete AI Horde prompts. */
object HordeAdultScenePromptFactory {
    private val nsfwModels = listOf(
        "CyberRealistic Pony",
        "WAI-NSFW-illustrious-SDXL",
        "AbsoluteReality",
        "Realistic Vision",
    )

    fun createCharacter(
        scene: ResolvedScene,
        descriptor: AdultVisualSceneDescriptor,
        characterKey: String,
        ageYears: Int,
        visualTags: Set<String> = emptySet(),
        visualNumeric: Map<String, Double> = emptyMap(),
        technologyEra: TechnologyEra? = null,
    ): HordeImageRequest {
        require(ageYears >= 18) { "Adult Horde scenes require adult participants" }
        require(descriptor.participants.all { it.ageYears >= 18 })

        val base = HordeResolvedScenePromptFactory.create(
            scene = scene,
            characterKey = characterKey,
            ageYears = ageYears,
            visualTags = visualTags,
            visualNumeric = visualNumeric,
            technologyEra = technologyEra,
        )
        val semantics = semanticPrompt(descriptor, technologyEra)
        return base.copy(
            cacheKey = listOf(
                "horde-adult-character-v1",
                base.cacheKey,
                descriptorSignature(descriptor),
            ).joinToString("|"),
            positivePrompt = listOf(
                base.positivePrompt,
                semantics,
                "the selected adult-module pose, setting, camera and lighting are visually dominant",
                "one coherent adult scene rather than a generic nude studio portrait",
            ).joinToString(", "),
            negativePrompt = listOf(
                base.negativePrompt,
                "generic studio backdrop",
                "passport photo",
                "unrelated pose",
                "wrong setting",
                "wrong camera angle",
            ).joinToString(", "),
            nsfw = true,
            ageYears = ageYears,
            seed = "${base.seed}:adult:${descriptor.recipeId}:${descriptor.poseKey}",
            preferredModels = nsfwModels,
            saveResultAsReference = false,
            // Keep the safe canonical character portrait as an img2img identity source for solo cards.
            referenceCacheKey = base.referenceCacheKey,
            referenceDenoisingStrength = 0.72,
        )
    }

    /**
     * Rebuilds the actual visual recipe stored on ADULT_SOCIAL_EVENT and renders it as a scene.
     * Returns null if the original event did not preserve enough structured data or any participant
     * is not provably 18+ at the event tick.
     */
    fun createEvent(
        event: SimulationEvent,
        people: PeopleState,
        evolution: EvolutionState,
        economy: EconomyState? = null,
    ): HordeImageRequest? {
        if (event.code != "ADULT_SOCIAL_EVENT") return null
        val participants = event.actorIds.mapNotNull { actorId ->
            people.persons.firstOrNull { it.id == actorId }
        }.distinctBy { it.id }
        if (participants.isEmpty()) return null

        val refs = participants.map { person ->
            AdultParticipantRef(person.id, person.ageYearsAt(event.tick))
        }
        if (refs.any { it.ageYears < 18 }) return null

        val descriptor = descriptorFromEvent(event, refs) ?: return null
        val era = eraFromDescriptor(descriptor) ?: event.actorIds.firstNotNullOfOrNull { id ->
            economy?.economy(id)?.era
        }

        val participantPrompt = participants.joinToString("; ") { person ->
            val age = person.ageYearsAt(event.tick)
            val identity = HordeCharacterVisualProfile.from(person.id)
            val visual = person.settlementId?.let(evolution::visualDescriptor)
            val morphology = HordeMorphologyVisual.from(
                visualTags = visual?.tags.orEmpty(),
                visualNumeric = visual?.numeric.orEmpty(),
            )
            buildString {
                append("adult participant age $age, ")
                append(identity.promptFragment)
                if (morphology.promptFragment.isNotBlank()) append(", ${morphology.promptFragment}")
            }
        }

        val positive = buildList {
            add("high quality photorealistic adult scene from a living historical simulation")
            add("all depicted participants are adults age 18 or older")
            add("participants: $participantPrompt")
            add(semanticPrompt(descriptor, era))
            add(eraPrompt(era))
            add("complete anatomically coherent connected bodies")
            add("clear readable interaction between the specified participants")
            add("body plans and morphology must match each participant description")
            add("environment, clothing remnants, props and architecture strictly match the stated era")
            add("single continuous scene, believable spatial relationship, cinematic realism")
            add("no text in image")
        }.joinToString(", ")

        val negative = buildList {
            add(HordeImageRequest.DEFAULT_NEGATIVE_PROMPT)
            add("child")
            add("minor")
            add("teen")
            add("young-looking child")
            add("floating head")
            add("disembodied head")
            add("detached body parts")
            add("mannequin")
            add("wax figure")
            add("3D render")
            add("split screen")
            add("multiple panels")
            add("unrelated extra people")
            add("wrong era")
            add("wrong setting")
            add("wrong pose")
            add("tangled anatomy")
            add("accidental duplicate limbs")
            addAll(eraNegative(era))
        }.joinToString(", ")

        val width = if (refs.size == 1) 512 else 768
        val height = if (refs.size == 1) 768 else 512
        return HordeImageRequest(
            cacheKey = listOf(
                "horde-adult-event-v1",
                event.id,
                event.tick.toString(),
                descriptorSignature(descriptor),
                participants.joinToString(",") { it.id },
                era?.name ?: "UNSPECIFIED",
            ).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = negative,
            nsfw = descriptor.explicitness.lowercase() != "implied" || descriptor.effectTags.any(::isExplicitEffect),
            ageYears = refs.minOf { it.ageYears },
            width = width,
            height = height,
            steps = 26,
            cfgScale = 6.5,
            seed = "chronosphere:adult-event:${event.id}:${descriptor.recipeId}",
            preferredModels = nsfwModels,
            referenceCacheKey = null,
            saveResultAsReference = false,
            referenceDenoisingStrength = 0.72,
        )
    }

    private fun descriptorFromEvent(
        event: SimulationEvent,
        participants: List<AdultParticipantRef>,
    ): AdultVisualSceneDescriptor? {
        val mediaKey = event.facts["mediaKey"] ?: return null
        val recipeId = mediaKey.removePrefix("adult://recipe/").takeIf { it != mediaKey && it.isNotBlank() } ?: return null
        val tags = event.facts["mediaTags"]
            ?.split('|')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        if (tags.isEmpty()) return null

        fun value(prefix: String): String? = tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotBlank() }
        val eventCode = event.facts["eventCode"]?.takeIf { it.isNotBlank() }
            ?: value("event:")?.uppercase()
            ?: return null
        val family = value("family:") ?: return null
        val rig = value("rig:") ?: return null
        val pose = value("pose:") ?: return null
        val wardrobe = value("wardrobe:") ?: return null
        val setting = value("setting:") ?: value("pack-setting:") ?: return null
        val camera = value("camera:") ?: return null
        val light = value("light:") ?: return null
        val explicitness = value("explicitness:") ?: "intimate"
        val structuralPrefixes = listOf(
            "recipe:", "family:", "rig:", "pose:", "wardrobe:", "setting:", "camera:", "light:",
            "event:", "pack-setting:", "explicitness:", "participants_", "rig-plan:", "era:",
            "lineage:", "ancestry:", "covering:", "posture:", "arms:", "legs:", "eyes:",
        )
        val effects = tags.filter { tag ->
            structuralPrefixes.none { prefix -> tag.startsWith(prefix) } &&
                tag !in setOf("tail", "mixed_ancestry", "hybrid_lineage")
        }.toSet()

        return AdultVisualSceneDescriptor(
            requestId = event.id,
            intent = "event",
            eventCode = eventCode,
            participants = participants,
            recipeId = recipeId,
            sceneFamily = family,
            rigLayout = rig,
            poseKey = pose,
            wardrobeKey = wardrobe,
            settingKey = setting,
            cameraKey = camera,
            lightingKey = light,
            explicitness = explicitness,
            effectTags = effects,
            mediaTags = tags,
        )
    }

    private fun semanticPrompt(descriptor: AdultVisualSceneDescriptor, era: TechnologyEra?): String = buildList {
        add(eventPrompt(descriptor.eventCode))
        add("scene family ${humanize(descriptor.sceneFamily)}")
        add(compositionPrompt(descriptor.rigLayout, descriptor.participants.size))
        add(posePrompt(descriptor.poseKey))
        add(wardrobePrompt(descriptor.wardrobeKey))
        add(settingPrompt(descriptor.settingKey, era))
        add(cameraPrompt(descriptor.cameraKey))
        add(lightingPrompt(descriptor.lightingKey))
        add(explicitnessPrompt(descriptor.explicitness, descriptor.effectTags))
        if (descriptor.effectTags.isNotEmpty()) {
            add("scene details: ${descriptor.effectTags.sorted().joinToString(", ") { humanize(it) }}")
        }
    }.joinToString(", ")

    private fun eventPrompt(code: String): String = when (code.uppercase()) {
        "COURTSHIP" -> "consensual adult courtship and close romantic attention"
        "UNION", "DYNASTIC_BOND", "SUCCESSION_BED" -> "consensual sexual union between adult participants"
        "AFFAIR", "SECRET_COUPLING" -> "clandestine consensual sexual encounter between adults"
        "FERTILITY_RITE", "CUM_RITE", "SACRED_UNION", "ALTAR_COUPLING" -> "consensual adult erotic fertility or sacred ritual"
        "PATRONAGE_LIAISON", "CONCUBINAGE" -> "consensual adult intimate liaison shaped by status and patronage"
        "ROUGH_COUPLING", "POWER_FUCK" -> "intense consensual adult sexual encounter with forceful body language"
        "ANAL_UNION" -> "consensual adult anal sexual encounter"
        "ORGY", "HAREM_NIGHT", "HAREM_SERVICE" -> "consensual group sexual scene involving only adults"
        "PUBLIC_SEX" -> "consensual adult sexual exhibition in the specified public setting"
        "BONDAGE_RITE" -> "consensual adult bondage and restraint scene"
        "TABOO_BREAK" -> "consensual adult forbidden or taboo-breaking intimate encounter"
        "SCANDAL" -> "adult intimate scandal or public exposure without changing the selected recipe"
        else -> "consensual adult intimate scene matching event ${humanize(code)}"
    }

    private fun posePrompt(key: String): String = when (key.lowercase()) {
        "pose.missionary", "pose.missionary-blend" -> "face-to-face missionary sexual position"
        "pose.from-behind", "pose.prone-bone" -> "rear-entry sexual position with clearly readable adult anatomy"
        "pose.upright-fuck" -> "upright penetrative sexual position"
        "pose.seed-rite", "pose.sacred-join", "pose.altar-mount" -> "ritualized sexual joining matching the ceremonial setting"
        "pose.seated-straddle" -> "seated straddling sexual position"
        "pose.side-entry" -> "side-lying intimate sexual position"
        "pose.against-frame" -> "standing intimate position against the architectural frame"
        "pose.muffled-fuck" -> "close clandestine sexual position with restrained movement"
        "pose.standing-lift" -> "standing lifted sexual position with balanced coherent anatomy"
        "pose.anal-kneel" -> "kneeling anal sexual position between adults"
        "pose.pile" -> "group sexual composition with each adult body spatially readable"
        "pose.oral-circle" -> "group oral-sex composition involving only adults"
        "pose.bent-over-crowd" -> "bent-over public sexual position with surrounding adult witnesses"
        "pose.bound-spread" -> "consensual restrained spread-body bondage pose"
        "pose.entwined-limbs" -> "intertwined sexual pose adapted to the specified additional limbs"
        "pose.coil-mount" -> "mounted sexual pose adapted to a tailed body plan"
        "pose.lean-close" -> "close tender leaning pose"
        "pose.accused" -> "socially exposed accused pose"
        else -> "pose ${humanize(key)}"
    }

    private fun wardrobePrompt(key: String): String = when {
        key.contains("undressed", true) || key.contains("none", true) || key.contains("stripped", true) ->
            "adult participants nude according to the selected recipe"
        key.contains("half", true) || key.contains("open", true) || key.contains("parted", true) || key.contains("hiked", true) ->
            "partially removed era-appropriate clothing exactly as required by the recipe"
        key.contains("discarded", true) -> "era-appropriate clothing visibly discarded nearby"
        key.contains("ripped", true) || key.contains("torn", true) -> "disheveled or torn era-appropriate clothing"
        key.contains("clothed", true) || key.contains("opaque", true) -> "fully clothed according to the selected recipe"
        else -> "wardrobe ${humanize(key)}"
    }

    private fun compositionPrompt(rig: String, count: Int): String = when {
        count <= 1 -> "one complete adult figure with the whole intended body composition visible"
        count == 2 -> "two complete adult bodies interacting in one coherent composition, both participants clearly readable"
        else -> "$count adult participants arranged in one coherent group composition without merged or ambiguous bodies"
    } + ", rig layout ${humanize(rig)}"

    private fun settingPrompt(key: String, era: TechnologyEra?): String {
        val base = when (key.lowercase()) {
            "set.chamber", "set.bedchamber" -> "private sleeping chamber"
            "set.hidden-room", "set.locked-room" -> "secluded private room"
            "set.garden" -> "secluded garden or natural courtship place"
            "set.palace" -> "elite residence appropriate to the era"
            "set.shrine", "set.temple", "set.altar" -> "ritual sacred setting appropriate to the era"
            "set.salon" -> "private social salon appropriate to the era"
            "set.annex" -> "private residential annex"
            "set.war-tent" -> "military shelter or war tent appropriate to the era"
            "set.feast-hall" -> "communal feast space appropriate to the era"
            "set.plaza" -> "public square appropriate to the era"
            "set.arcade" -> "urban public arcade appropriate to the era"
            "set.cellar" -> "private cellar or underground room"
            "set.loft" -> "industrial-era loft interior"
            "set.threshold" -> "simple threshold or doorway setting"
            "set.card" -> "private character-card environment"
            else -> humanize(key)
        }
        return "$base, ${eraPrompt(era)}"
    }

    private fun cameraPrompt(key: String): String = when (key.lowercase()) {
        "cam.intimate" -> "intimate medium-close cinematic camera with the interaction fully readable"
        "cam.close", "cam.tight" -> "close cinematic framing while keeping the important interacting anatomy in frame"
        "cam.wide" -> "wide composition showing every participant and the setting"
        "cam.three-quarter" -> "three-quarter view showing bodies and interaction clearly"
        "cam.over-shoulder" -> "over-the-shoulder angle with both adult bodies readable"
        "cam.low" -> "low cinematic angle without hiding the selected pose"
        "cam.side" -> "side view clearly showing the interaction"
        "cam.formal", "cam.icon" -> "formal centered composition appropriate to the ritual or status context"
        "cam.portrait" -> "portrait-oriented framing with a complete coherent body"
        else -> "camera ${humanize(key)}"
    }

    private fun lightingPrompt(key: String): String = when (key.lowercase()) {
        "light.day" -> "natural daylight"
        "light.lamp" -> "warm era-appropriate lamp or fire lighting"
        "light.neon" -> "era-appropriate neon lighting"
        "light.slit" -> "narrow directional light from a private opening"
        "light.gold" -> "warm ceremonial golden light"
        "light.flame", "light.torch", "light.ember" -> "fire or torch lighting appropriate to the era"
        "light.candle" -> "candlelight"
        "light.dark", "light.low" -> "low-key private lighting with readable bodies"
        "light.hard" -> "hard dramatic light"
        "light.amber" -> "warm amber light"
        "light.noon" -> "clear midday light"
        "light.cool" -> "cool naturalistic light"
        else -> "lighting ${humanize(key)}"
    }

    private fun explicitnessPrompt(explicitness: String, effects: Set<String>): String = when {
        explicitness.equals("implied", true) -> "adult intimacy is implied rather than graphically shown"
        explicitness.equals("explicit", true) || effects.any(::isExplicitEffect) ->
            "explicit consensual adult erotic scene, the selected sexual action is visible and anatomically coherent"
        else -> "intimate consensual adult erotic scene with clear body language and the selected action"
    }

    private fun isExplicitEffect(tag: String): Boolean = tag.lowercase() in setOf(
        "penetration", "sex", "rough-sex", "anal", "oral", "group", "fluids", "creampie", "bdsm", "restraint",
    )

    private fun eraFromDescriptor(descriptor: AdultVisualSceneDescriptor): TechnologyEra? {
        val tag = descriptor.mediaTags.firstOrNull { it.startsWith("era:") }?.removePrefix("era:") ?: return null
        return when (tag.lowercase().removePrefix("era_")) {
            "tribal" -> TechnologyEra.TRIBAL
            "agrarian" -> TechnologyEra.AGRARIAN
            "urban" -> TechnologyEra.URBAN
            "metallurgic" -> TechnologyEra.METALLURGIC
            "medieval" -> TechnologyEra.MEDIEVAL
            "early_industrial" -> TechnologyEra.EARLY_INDUSTRIAL
            "industrial" -> TechnologyEra.INDUSTRIAL
            "electric" -> TechnologyEra.ELECTRIC
            "information" -> TechnologyEra.INFORMATION
            "spacefaring" -> TechnologyEra.SPACEFARING
            else -> null
        }
    }

    private fun eraPrompt(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL -> "prehistoric tribal material culture only: hide, woven fiber, wood, bone, stone, simple shelters and firelight"
        TechnologyEra.AGRARIAN -> "early agrarian material culture: farms, timber, clay, thatch, simple masonry and hand tools"
        TechnologyEra.URBAN -> "early urban pre-industrial architecture, workshops and dense low-rise settlement"
        TechnologyEra.METALLURGIC -> "pre-modern metalworking material culture with bronze or iron tools and era-appropriate interiors"
        TechnologyEra.MEDIEVAL -> "medieval material culture, timber or stone interiors, candles or hearths, no electricity"
        TechnologyEra.EARLY_INDUSTRIAL -> "early industrial brick, steam-age objects and mechanical technology"
        TechnologyEra.INDUSTRIAL -> "industrial-era architecture, steel, engines and mass-produced objects, no digital technology"
        TechnologyEra.ELECTRIC -> "electrified early-modern environment with wired lighting and period-appropriate furnishings"
        TechnologyEra.INFORMATION -> "contemporary information-age environment and modern infrastructure"
        TechnologyEra.SPACEFARING -> "believable mature spacefaring environment and advanced materials"
        null -> "historically coherent material culture with no unexplained anachronisms"
    }

    private fun eraNegative(era: TechnologyEra?): List<String> = when (era) {
        TechnologyEra.TRIBAL -> listOf("palace", "chandelier", "modern bed", "electric light", "car", "gun", "computer", "tailored suit")
        TechnologyEra.AGRARIAN -> listOf("chandelier", "industrial machinery", "electric light", "car", "computer", "skyscraper")
        TechnologyEra.URBAN, TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> listOf("electric light", "car", "computer", "smartphone", "plastic furniture")
        TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> listOf("smartphone", "laptop", "hologram", "spacecraft")
        TechnologyEra.ELECTRIC -> listOf("smartphone", "laptop", "hologram", "spacecraft")
        TechnologyEra.INFORMATION -> listOf("medieval fantasy court")
        TechnologyEra.SPACEFARING -> listOf("medieval fantasy castle")
        null -> emptyList()
    }

    private fun descriptorSignature(descriptor: AdultVisualSceneDescriptor): String = listOf(
        descriptor.intent,
        descriptor.eventCode,
        descriptor.recipeId,
        descriptor.sceneFamily,
        descriptor.rigLayout,
        descriptor.poseKey,
        descriptor.wardrobeKey,
        descriptor.settingKey,
        descriptor.cameraKey,
        descriptor.lightingKey,
        descriptor.explicitness,
        descriptor.effectTags.sorted().joinToString(","),
        descriptor.participants.joinToString(",") { "${it.entityId}:${it.ageYears}" },
    ).joinToString("|")

    private fun humanize(value: String): String = value
        .substringAfterLast(':')
        .removePrefix("pose.")
        .removePrefix("set.")
        .removePrefix("cam.")
        .removePrefix("light.")
        .removePrefix("wardrobe.")
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
}
