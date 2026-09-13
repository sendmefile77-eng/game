package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent

/** Wide chronicle frame: era city life with the material consequences of player choices visible. */
object HordeChronicleEventPromptFactory {
    private val preferredModels = listOf(
        "WAI-NSFW-illustrious-SDXL",
        "AlbedoBase XL (SDXL)",
        "CyberRealistic Pony",
        "AbsoluteReality",
    )

    private val significantCodes = setOf(
        "SETTLEMENT_FOUNDED", "COLONY_FOUNDED", "WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED",
        "PEACE_TREATY", "ALLIANCE_FORMED", "ERA_ADVANCED", "RULER_SUCCEEDED", "DYNASTY_FOUNDED",
        "ADULT_SOCIAL_EVENT", "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION",
        "INTERVENTION_HARVEST_AID", "INTERVENTION_DROUGHT", "INTERVENTION_TECH_BOOST",
        "INTERVENTION_STABILITY_SUPPORT", "INTERVENTION_FESTIVAL", "INTERVENTION_EMBASSY",
        "INTERVENTION_WAR_RAID",
    )

    fun latestSignificant(events: List<SimulationEvent>): SimulationEvent? =
        events.asReversed().firstOrNull { it.code in significantCodes }

    fun create(event: SimulationEvent, people: PeopleState, economy: EconomyState? = null): HordeImageRequest {
        val participants = event.actorIds.mapNotNull { id -> people.persons.firstOrNull { it.id == id } }
        val hasMinor = participants.any { it.ageYearsAt(event.tick) < 18 }
        val era = resolveEra(event, people, economy)
        val erotic = !hasMinor
        val settlement = event.facts["settlement"]?.takeIf { it.isNotBlank() }
        val choiceVisual = HordeHistoricalVisualPrompt.eraChoiceFragment(event.facts["choiceId"])
        val choiceLabel = event.facts["choiceLabel"]?.takeIf { it.isNotBlank() }

        val positive = buildList {
            add("masterpiece, best quality, anime illustration, cinematic wide establishing shot of a living settlement")
            add(HordeEraVisual.materialCulture(era))
            add(HordeEraVisual.distinctiveMarker(era))
            add(sceneWork(event, era, settlement))
            if (choiceVisual.isNotBlank()) {
                add("the newly chosen way of life must be unmistakably visible in the main action: $choiceVisual")
                add("show the practical material consequence, not a symbolic icon or caption")
            }
            if (erotic) {
                add(HordeEraVisual.cityErotica(era))
                add("explicit consensual adult sex in the same frame as ordinary city work")
                add("nude adult woman and nude adult man, visible breasts, nipples, penis, vagina, wet skin")
                add("other clothed workers continue their jobs around them, no one shocked")
            } else {
                add("everyday public work, fully clothed adults, no sexual content")
            }
            add("crowded readable street, environmental storytelling, complete connected bodies")
            add("natural era-correct light, detailed materials, no text, no UI, no modern bathroom")
        }.joinToString(", ")

        val negative = buildList {
            add("worst quality, low quality, blurry, bad anatomy, extra limbs, duplicate people")
            add("disconnected limbs, floating head, cropped face, text, watermark, collage, split screen")
            add("oversaturated, overcooked, burnt colors, neon cyberpunk")
            addAll(HordeEraVisual.negatives(era).take(10))
            if (hasMinor) add("nudity, explicit sex, sexualized minor")
            add("child, loli, shota, underage")
        }.joinToString(", ")

        val eraSignature = era?.name ?: "UNSPECIFIED"
        val adultMinAge = participants.minOfOrNull { it.ageYearsAt(event.tick) }?.coerceAtLeast(0) ?: 21
        return HordeImageRequest(
            cacheKey = listOf(
                "horde-chronicle-event-v8-era-choice",
                event.id,
                event.tick.toString(),
                event.code,
                eraSignature,
                event.facts["choiceId"].orEmpty(),
                choiceLabel.orEmpty(),
                event.actorIds.sorted().joinToString(","),
                event.locationId.orEmpty(),
                if (erotic) "nsfw" else "safe",
            ).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = negative,
            nsfw = erotic,
            ageYears = if (erotic) adultMinAge.coerceAtLeast(18) else adultMinAge,
            width = 1024,
            height = 576,
            steps = 22,
            cfgScale = 5.5,
            seed = "chronosphere:chronicle-choice:$eraSignature:${event.id}:${event.facts["choiceId"].orEmpty()}",
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
        civilizationId?.let { economy.economy(it)?.era }?.let { return it }
        return economy.civilizations.maxByOrNull { it.grossOutput }?.era
    }

    private fun sceneWork(event: SimulationEvent, era: TechnologyEra?, settlement: String?): String {
        val place = settlement?.let { "in the settlement $it" } ?: "in the main settlement"
        val work = when (era) {
            TechnologyEra.TRIBAL -> "hide-working, butchering, hearth-tending $place"
            TechnologyEra.AGRARIAN -> "threshing, hauling grain, tending pens $place"
            TechnologyEra.URBAN -> "market stalls, potters, packed street $place"
            TechnologyEra.METALLURGIC -> "open-air forge yard, bellows, pouring bronze $place"
            TechnologyEra.MEDIEVAL -> "guild street, castle-town lane $place"
            TechnologyEra.EARLY_INDUSTRIAL -> "brick mill yard and chimneys $place"
            TechnologyEra.INDUSTRIAL -> "factory gates and rail sidings $place"
            TechnologyEra.ELECTRIC -> "wired boulevard and workshop fronts $place"
            TechnologyEra.INFORMATION -> "contemporary downtown sidewalk $place"
            TechnologyEra.SPACEFARING -> "habitat concourse $place"
            null -> "everyday civic work $place"
        }
        val choice = event.facts["choiceLabel"]?.takeIf { it.isNotBlank() }
        return when {
            choice != null -> "a century-defining change is being adopted in everyday life, $work"
            event.code == "SETTLEMENT_FOUNDED" || event.code == "COLONY_FOUNDED" -> "first permanent shelters rising, $work"
            event.code == "WAR_STARTED" -> "militia gathering at the edge of the working street, $work"
            event.code == "CITY_CAPTURED" -> "new banners over the same working street, $work"
            event.code == "ERA_ADVANCED" -> "new tools appearing in ordinary hands, $work"
            event.code == "RULER_SUCCEEDED" || event.code == "DYNASTY_FOUNDED" -> "a new leader walking the same working street, $work"
            event.code == "ADULT_SOCIAL_EVENT" -> "public erotic custom happening in the work yard, $work"
            else -> work
        }
    }
}
