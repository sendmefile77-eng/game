package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent

/** Builds one bounded, non-blocking AI Horde illustration request for a chronicle event. */
object HordeChronicleEventPromptFactory {
    private val preferredModels = listOf(
        "AlbedoBase XL (SDXL)",
        "AbsoluteReality",
        "Realistic Vision",
        "CyberRealistic Pony",
    )

    private val significantCodes = setOf(
        "SETTLEMENT_FOUNDED",
        "COLONY_FOUNDED",
        "WAR_STARTED",
        "WAR_CASUALTIES",
        "CITY_CAPTURED",
        "PEACE_TREATY",
        "ALLIANCE_FORMED",
        "ERA_ADVANCED",
        "RULER_SUCCEEDED",
        "DYNASTY_FOUNDED",
        "ADULT_SOCIAL_EVENT",
        "BIOLOGICAL_DIVERGENCE",
        "STRUCTURAL_MUTATION",
        "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE",
        "PLAYER_STRUCTURAL_MUTATION",
        "PLAYER_HYBRIDIZATION",
        "INTERVENTION_HARVEST_AID",
        "INTERVENTION_DROUGHT",
        "INTERVENTION_TECH_BOOST",
        "INTERVENTION_STABILITY_SUPPORT",
    )

    fun latestSignificant(events: List<SimulationEvent>): SimulationEvent? =
        events.asReversed().firstOrNull { it.code in significantCodes }

    fun create(
        event: SimulationEvent,
        people: PeopleState,
        economy: EconomyState? = null,
    ): HordeImageRequest {
        val participants = event.actorIds.mapNotNull { actorId ->
            people.persons.firstOrNull { it.id == actorId }
        }
        val participantPhrase = participants.take(4).joinToString(", ") { person ->
            val age = person.ageYearsAt(event.tick)
            "${person.biologicalSex.name.lowercase()} adult age $age"
        }
        val hasMinor = participants.any { it.ageYearsAt(event.tick) < 18 }
        val era = resolveEra(event, people, economy)

        val scene = sceneFragment(event, era)
        val eraContext = eraPromptFragment(era)
        val facts = event.facts.entries
            .filter { (key, value) -> key !in setOf("mediaKey", "mediaTags") && value.isNotBlank() }
            .take(5)
            .joinToString(", ") { (key, value) -> "${key.replace('_', ' ')}: $value" }

        val positive = buildList {
            add("high quality cinematic historical world simulation illustration")
            add(scene)
            add(eraContext)
            if (participantPhrase.isNotBlank() && !hasMinor) add("participants: $participantPhrase")
            if (facts.isNotBlank()) add(facts)
            add("one physically coherent location and moment")
            add("environmental storytelling")
            add("realistic people with complete connected bodies")
            add("realistic materials and lighting")
            add("wide establishing composition")
            add("all architecture, clothing, tools, furniture and technology strictly match the stated era")
            add("documentary cinematic realism, not fantasy concept art")
            add("no text in image")
            if (event.code == "ADULT_SOCIAL_EVENT") {
                add("private adult social gathering, mature atmosphere, fully covered, non-explicit")
            }
        }.joinToString(", ")

        val negative = buildList {
            add(HordeImageRequest.DEFAULT_NEGATIVE_PROMPT)
            add("split screen")
            add("collage")
            add("UI")
            add("caption")
            add("letters")
            add("floating head")
            add("disembodied body parts")
            add("mannequin")
            add("3D render")
            add("fantasy castle unless historically appropriate")
            addAll(eraNegativeFragments(era))
            if (hasMinor || event.code == "ADULT_SOCIAL_EVENT") {
                add("nudity")
                add("explicit sex")
                add("sexualized minor")
            }
        }.joinToString(", ")

        val eraSignature = era?.name ?: "UNSPECIFIED"
        return HordeImageRequest(
            cacheKey = listOf(
                "horde-chronicle-event-v3",
                event.id,
                event.tick.toString(),
                event.code,
                eraSignature,
                event.actorIds.sorted().joinToString(","),
                event.locationId.orEmpty(),
                event.facts.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" },
            ).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = negative,
            nsfw = false,
            ageYears = participants.minOfOrNull { it.ageYearsAt(event.tick) }?.coerceAtLeast(0) ?: 18,
            width = 768,
            height = 448,
            steps = 24,
            cfgScale = 6.5,
            seed = "chronosphere:event:${event.id}:$eraSignature",
            preferredModels = preferredModels,
            referenceCacheKey = null,
            saveResultAsReference = false,
        )
    }

    private fun resolveEra(
        event: SimulationEvent,
        people: PeopleState,
        economy: EconomyState?,
    ): TechnologyEra? {
        if (economy == null) return null
        event.actorIds.firstNotNullOfOrNull { actorId -> economy.economy(actorId)?.era }?.let { return it }
        val participantCivilization = event.actorIds.firstNotNullOfOrNull { actorId ->
            people.persons.firstOrNull { it.id == actorId }?.civilizationId
        }
        return participantCivilization?.let { economy.economy(it)?.era }
    }

    private fun sceneFragment(event: SimulationEvent, era: TechnologyEra?): String = when (event.code) {
        "SETTLEMENT_FOUNDED" -> "people physically establishing a new settlement in an unsettled landscape, building the first era-appropriate shelters"
        "COLONY_FOUNDED" -> "settlers establishing a remote community using only era-appropriate transport, tools and buildings"
        "WAR_STARTED" -> "two era-appropriate armed groups or armies mobilizing as a war begins"
        "WAR_CASUALTIES" -> "aftermath of an era-appropriate battle, damaged landscape, exhausted survivors, no graphic gore"
        "CITY_CAPTURED" -> if (era == TechnologyEra.TRIBAL || era == TechnologyEra.AGRARIAN) {
            "a fortified settlement or village changing control after conflict, simple earthwork, timber or early masonry defenses"
        } else {
            "an era-appropriate fortified city or major settlement changing control after a military campaign"
        }
        "PEACE_TREATY" -> if (era == TechnologyEra.TRIBAL) {
            "leaders of rival tribal communities making peace face to face beside a communal fire or in a simple timber shelter"
        } else {
            "formal peace agreement between rival societies in an era-appropriate civic or diplomatic setting"
        }
        "ALLIANCE_FORMED" -> if (era == TechnologyEra.TRIBAL) {
            "leaders of tribal communities forming an alliance in an open-air council beside fire and simple shelters"
        } else {
            "formal alliance ceremony between two societies in an era-appropriate setting"
        }
        "ERA_ADVANCED" -> "civilization visibly transitioning into its newly reached technological era, showing practical changes in tools, buildings and daily life"
        "RULER_SUCCEEDED" -> if (era == TechnologyEra.TRIBAL) {
            "tribal community publicly recognizing a new leader in a simple settlement, no throne room, no palace"
        } else {
            "community recognizing a new ruler in a ceremonial setting strictly appropriate to the technological era"
        }
        "DYNASTY_FOUNDED" -> if (era == TechnologyEra.TRIBAL) {
            "a tribal kin group and community recognizing the beginning of a hereditary leadership lineage, outdoors or inside a simple timber or hide shelter, no court or palace"
        } else {
            "founding of a ruling lineage in a social setting strictly appropriate to the technological era"
        }
        "ADULT_SOCIAL_EVENT" -> if (era == TechnologyEra.TRIBAL) {
            "private social gathering of adults in a simple tribal dwelling or sheltered camp, firelight, ordinary daily-life objects"
        } else {
            "private social event among adults in an interior strictly appropriate to the technological era"
        }
        "BIOLOGICAL_DIVERGENCE" -> "fictional humanoid population in its real daily environment showing visible evolutionary divergence"
        "STRUCTURAL_MUTATION" -> "fictional evolved humanoid lineage in its normal settlement showing a newly established body-plan trait, complete coherent bodies"
        "HYBRID_LINEAGE_FORMED" -> "fictional hybrid humanoid community emerging in an era-appropriate settlement, complete coherent people"
        "PLAYER_EVOLUTION_DIVERGENCE" -> "a newly separated humanoid population visibly diverging from its parent lineage, showing coherent changed proportions while living in its normal era-appropriate settlement"
        "PLAYER_STRUCTURAL_MUTATION" -> "a newly established humanoid lineage visibly displaying the specific changed body plan described in the event facts, several complete coherent individuals in their normal era-appropriate settlement"
        "PLAYER_HYBRIDIZATION" -> "a stable new hybrid humanoid population combining visible inherited traits from two genuinely different parent lineages, several complete coherent individuals in an era-appropriate settlement"
        "INTERVENTION_HARVEST_AID" -> "unexpectedly abundant harvest transforming era-appropriate fields, food stores and community life"
        "INTERVENTION_DROUGHT" -> "severe drought striking the era-appropriate settlement, fields and water sources"
        "INTERVENTION_TECH_BOOST" -> "sudden technological leap visibly changing tools, work and buildings while remaining internally coherent"
        "INTERVENTION_STABILITY_SUPPORT" -> "community order recovering after instability in a public setting appropriate to the era"
        else -> "major historical event in a fictional civilization"
    }

    private fun eraPromptFragment(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL ->
            "prehistoric tribal technology only, stone, wood, bone, hide and woven fiber, hearth fires, simple huts or tents, raw landscape, small-scale settlement, absolutely no palace, chandelier or modern furnishing"
        TechnologyEra.AGRARIAN ->
            "early agrarian material culture, farms and villages, timber, clay, thatch and simple masonry, hand tools and animal power, no industrial technology"
        TechnologyEra.URBAN ->
            "early urban pre-industrial civilization, low-rise masonry and timber buildings, markets, workshops and crowded streets"
        TechnologyEra.METALLURGIC ->
            "metalworking pre-modern civilization, bronze or iron tools and weapons, furnaces, workshops and era-appropriate fortifications"
        TechnologyEra.MEDIEVAL ->
            "medieval-level technology, stone and timber architecture, candles and hearths, animal transport, no electricity"
        TechnologyEra.EARLY_INDUSTRIAL ->
            "early industrial society, brick workshops, steam machinery, soot, mechanical tools and rail-era material culture"
        TechnologyEra.INDUSTRIAL ->
            "industrial society, factories, steel, railways, engines and mass-produced objects, no digital technology"
        TechnologyEra.ELECTRIC ->
            "electrified early modern society, electric lighting, wired infrastructure, engines and early mass media"
        TechnologyEra.INFORMATION ->
            "information-age society with contemporary architecture, computers, digital devices and modern infrastructure"
        TechnologyEra.SPACEFARING ->
            "advanced spacefaring civilization with believable mature aerospace and off-world infrastructure"
        null -> "strictly internally consistent historical material culture, no unexplained anachronisms"
    }

    private fun eraNegativeFragments(era: TechnologyEra?): List<String> = when (era) {
        TechnologyEra.TRIBAL -> listOf(
            "palace", "chandelier", "luxury mansion", "ballroom", "ornate court", "throne room",
            "modern furniture", "large glass windows", "skyscraper", "electric light", "car", "gun",
            "computer", "smartphone", "industrial machinery", "tailored suit", "Victorian interior",
        )
        TechnologyEra.AGRARIAN -> listOf(
            "chandelier", "luxury palace interior", "industrial machinery", "electric light", "car", "computer", "skyscraper",
        )
        TechnologyEra.URBAN, TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> listOf(
            "electric light", "modern furniture", "car", "computer", "smartphone", "skyscraper", "plastic objects",
        )
        TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> listOf(
            "smartphone", "flat screen", "laptop", "modern glass skyscraper", "spacecraft", "hologram",
        )
        TechnologyEra.ELECTRIC -> listOf("smartphone", "laptop", "spacecraft", "hologram")
        TechnologyEra.INFORMATION -> listOf("spacecraft interior", "medieval fantasy court")
        TechnologyEra.SPACEFARING -> listOf("medieval fantasy castle")
        null -> emptyList()
    }
}
