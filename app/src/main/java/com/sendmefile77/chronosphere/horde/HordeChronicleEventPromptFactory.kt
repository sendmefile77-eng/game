package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent

/** Builds one bounded chronicle illustration request that also behaves well with Local Dream. */
object HordeChronicleEventPromptFactory {
    private val preferredModels = listOf(
        "AlbedoBase XL (SDXL)",
        "AbsoluteReality",
        "Realistic Vision",
        "CyberRealistic Pony",
    )

    private val significantCodes = setOf(
        "SETTLEMENT_FOUNDED", "COLONY_FOUNDED", "WAR_STARTED", "WAR_CASUALTIES",
        "CITY_CAPTURED", "PEACE_TREATY", "ALLIANCE_FORMED", "ERA_ADVANCED",
        "RULER_SUCCEEDED", "DYNASTY_FOUNDED", "ADULT_SOCIAL_EVENT",
        "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION",
        "INTERVENTION_HARVEST_AID", "INTERVENTION_DROUGHT", "INTERVENTION_TECH_BOOST",
        "INTERVENTION_STABILITY_SUPPORT",
    )

    fun latestSignificant(events: List<SimulationEvent>): SimulationEvent? =
        events.asReversed().firstOrNull { it.code in significantCodes }

    fun create(
        event: SimulationEvent,
        people: PeopleState,
        economy: EconomyState? = null,
    ): HordeImageRequest {
        val participants = event.actorIds.mapNotNull { id -> people.persons.firstOrNull { it.id == id } }
        val hasMinor = participants.any { it.ageYearsAt(event.tick) < 18 }
        val era = resolveEra(event, people, economy)
        val participantPhrase = participants.take(3).joinToString(", ") { person ->
            "${person.biologicalSex.name.lowercase()} adult ${person.ageYearsAt(event.tick)}"
        }
        val facts = event.facts.entries
            .filter { (key, value) -> key !in setOf("mediaKey", "mediaTags") && value.isNotBlank() }
            .take(2)
            .joinToString(", ") { (_, value) -> value }

        // Keep the useful part close to the front: Local Dream's CLIP models have a short context.
        val positive = buildList {
            add("masterpiece, best quality, cinematic wide establishing shot")
            add(sceneFragment(event, era))
            add(eraPromptFragment(era))
            if (participantPhrase.isNotBlank() && !hasMinor) add("people: $participantPhrase")
            if (facts.isNotBlank()) add("story details: $facts")
            add("single coherent moment, environmental storytelling")
            add("complete connected bodies, readable faces, detailed materials, natural light")
            add("no text, no UI")
            if (event.code == "ADULT_SOCIAL_EVENT") add("adult private social gathering, mature atmosphere")
        }.joinToString(", ")

        val negative = buildList {
            add("worst quality, low quality, blurry, bad anatomy, extra limbs, duplicate people")
            add("disconnected limbs, floating head, cropped face, text, watermark, collage, split screen")
            addAll(eraNegativeFragments(era).take(8))
            if (hasMinor) add("nudity, sexual content")
        }.joinToString(", ")

        val eraSignature = era?.name ?: "UNSPECIFIED"
        return HordeImageRequest(
            cacheKey = listOf(
                "horde-chronicle-event-v5",
                event.id,
                event.tick.toString(),
                event.code,
                eraSignature,
                event.actorIds.sorted().joinToString(","),
                event.locationId.orEmpty(),
            ).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = negative,
            nsfw = event.code == "ADULT_SOCIAL_EVENT" && !hasMinor,
            ageYears = participants.minOfOrNull { it.ageYearsAt(event.tick) }?.coerceAtLeast(0) ?: 18,
            width = 1024,
            height = 576,
            steps = 24,
            cfgScale = 6.2,
            seed = "chronosphere:event:${event.id}:$eraSignature",
            preferredModels = preferredModels,
            qualityPriority = false,
            referenceCacheKey = null,
            saveResultAsReference = false,
        )
    }

    private fun resolveEra(event: SimulationEvent, people: PeopleState, economy: EconomyState?): TechnologyEra? {
        if (economy == null) return null
        event.actorIds.firstNotNullOfOrNull { id -> economy.economy(id)?.era }?.let { return it }
        val civilizationId = event.actorIds.firstNotNullOfOrNull { id ->
            people.persons.firstOrNull { it.id == id }?.civilizationId
        }
        return civilizationId?.let { economy.economy(it)?.era }
    }

    private fun sceneFragment(event: SimulationEvent, era: TechnologyEra?): String = when (event.code) {
        "SETTLEMENT_FOUNDED" -> "a community founding its first permanent settlement, shelters rising around a central gathering place"
        "COLONY_FOUNDED" -> "settlers establishing a remote daughter settlement with era-appropriate tools and transport"
        "WAR_STARTED" -> "two opposing groups mobilizing at the beginning of a war"
        "WAR_CASUALTIES" -> "aftermath of a battle, exhausted survivors and damaged landscape, no graphic gore"
        "CITY_CAPTURED" -> "a fortified settlement changing control after conflict"
        "PEACE_TREATY" -> "rival leaders making peace face to face in an era-appropriate public setting"
        "ALLIANCE_FORMED" -> "leaders of two societies forming an alliance before their communities"
        "ERA_ADVANCED" -> "daily life visibly changing as a civilization enters a new technological era"
        "RULER_SUCCEEDED" -> "a community publicly recognizing a new ruler"
        "DYNASTY_FOUNDED" -> "a kin group and community recognizing the beginning of a hereditary ruling line"
        "ADULT_SOCIAL_EVENT" -> "a private social gathering of consenting adults in an era-appropriate interior or sheltered camp"
        "BIOLOGICAL_DIVERGENCE" -> "a humanoid population in daily life showing a clearly visible evolutionary divergence"
        "STRUCTURAL_MUTATION" -> "several coherent humanoids showing a newly established body-plan trait in normal daily life"
        "HYBRID_LINEAGE_FORMED" -> "a new hybrid humanoid community combining visible traits of two parent lineages"
        "PLAYER_EVOLUTION_DIVERGENCE" -> "a newly separated humanoid population visibly diverging from its parent lineage"
        "PLAYER_STRUCTURAL_MUTATION" -> "a newly established humanoid lineage displaying a changed body plan"
        "PLAYER_HYBRIDIZATION" -> "a stable hybrid humanoid population combining traits from two distinct lineages"
        "INTERVENTION_HARVEST_AID" -> "an unexpectedly abundant harvest transforming food stores and community life"
        "INTERVENTION_DROUGHT" -> "severe drought striking settlement, fields and water sources"
        "INTERVENTION_TECH_BOOST" -> "a sudden technological leap visibly changing tools, work and buildings"
        "INTERVENTION_STABILITY_SUPPORT" -> "public order recovering after instability, people returning to ordinary life"
        else -> "a major historical event in a fictional civilization"
    }

    private fun eraPromptFragment(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL -> "prehistoric tribal material culture, stone, wood, bone, hide, woven fiber, hearth fires, simple huts, wild landscape"
        TechnologyEra.AGRARIAN -> "early agrarian villages, timber, clay, thatch, simple masonry, hand tools, animal power"
        TechnologyEra.URBAN -> "early pre-industrial town, markets, workshops, low-rise masonry and timber buildings"
        TechnologyEra.METALLURGIC -> "pre-modern metalworking society, furnaces, bronze or iron tools, workshops, simple fortifications"
        TechnologyEra.MEDIEVAL -> "medieval technology, stone and timber architecture, candles, hearths, animal transport"
        TechnologyEra.EARLY_INDUSTRIAL -> "early industrial society, brick workshops, steam machinery, soot, rail-era material culture"
        TechnologyEra.INDUSTRIAL -> "industrial society, factories, steel, railways, engines, mass-produced objects"
        TechnologyEra.ELECTRIC -> "electrified early modern society, electric lighting, wired infrastructure, engines"
        TechnologyEra.INFORMATION -> "information-age society, computers, digital devices, contemporary infrastructure"
        TechnologyEra.SPACEFARING -> "believable advanced spacefaring civilization and mature aerospace infrastructure"
        null -> "internally consistent historical material culture, no anachronisms"
    }

    private fun eraNegativeFragments(era: TechnologyEra?): List<String> = when (era) {
        TechnologyEra.TRIBAL -> listOf("palace", "chandelier", "throne room", "modern furniture", "electric light", "car", "gun", "computer")
        TechnologyEra.AGRARIAN -> listOf("industrial machinery", "electric light", "car", "computer", "skyscraper")
        TechnologyEra.URBAN, TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> listOf("electric light", "car", "computer", "smartphone", "skyscraper", "plastic")
        TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> listOf("smartphone", "laptop", "glass skyscraper", "spacecraft", "hologram")
        TechnologyEra.ELECTRIC -> listOf("smartphone", "laptop", "spacecraft", "hologram")
        TechnologyEra.INFORMATION -> listOf("medieval fantasy court")
        TechnologyEra.SPACEFARING -> listOf("medieval fantasy castle")
        null -> emptyList()
    }
}
