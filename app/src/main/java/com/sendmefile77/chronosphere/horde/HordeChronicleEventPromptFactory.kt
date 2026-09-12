package com.sendmefile77.chronosphere.horde

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
        "INTERVENTION_HARVEST_AID",
        "INTERVENTION_DROUGHT",
        "INTERVENTION_TECH_BOOST",
        "INTERVENTION_STABILITY_SUPPORT",
    )

    fun latestSignificant(events: List<SimulationEvent>): SimulationEvent? =
        events.asReversed().firstOrNull { it.code in significantCodes }

    fun create(event: SimulationEvent, people: PeopleState): HordeImageRequest {
        val participants = event.actorIds.mapNotNull { actorId ->
            people.persons.firstOrNull { it.id == actorId }
        }
        val participantPhrase = participants.take(4).joinToString(", ") { person ->
            val age = person.ageYearsAt(event.tick)
            "${person.biologicalSex.name.lowercase()} adult age $age"
        }
        val hasMinor = participants.any { it.ageYearsAt(event.tick) < 18 }

        val scene = sceneFragment(event)
        val facts = event.facts.entries
            .filter { (key, value) -> key !in setOf("mediaKey", "mediaTags") && value.isNotBlank() }
            .take(5)
            .joinToString(", ") { (key, value) -> "${key.replace('_', ' ')}: $value" }

        val positive = buildList {
            add("high quality cinematic historical world simulation illustration")
            add(scene)
            if (participantPhrase.isNotBlank() && !hasMinor) add("participants: $participantPhrase")
            if (facts.isNotBlank()) add(facts)
            add("single coherent scene")
            add("environmental storytelling")
            add("realistic materials and lighting")
            add("wide establishing composition")
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
            add("modern photography equipment")
            if (hasMinor || event.code == "ADULT_SOCIAL_EVENT") {
                add("nudity")
                add("explicit sex")
                add("sexualized minor")
            }
        }.joinToString(", ")

        return HordeImageRequest(
            cacheKey = listOf(
                "horde-chronicle-event-v1",
                event.id,
                event.tick.toString(),
                event.code,
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
            steps = 22,
            cfgScale = 6.0,
            seed = "chronosphere:event:${event.id}",
            preferredModels = preferredModels,
            referenceCacheKey = null,
            saveResultAsReference = false,
        )
    }

    private fun sceneFragment(event: SimulationEvent): String = when (event.code) {
        "SETTLEMENT_FOUNDED" -> "new settlement being founded in an unsettled landscape"
        "COLONY_FOUNDED" -> "colonists establishing a new remote settlement"
        "WAR_STARTED" -> "two historical armies mobilizing as a war begins"
        "WAR_CASUALTIES" -> "aftermath of a historical battle, damaged battlefield, no graphic gore"
        "CITY_CAPTURED" -> "fortified city changing control after a military campaign"
        "PEACE_TREATY" -> "formal peace treaty between rival historical states"
        "ALLIANCE_FORMED" -> "formal diplomatic alliance ceremony between two states"
        "ERA_ADVANCED" -> "civilization entering a visibly more advanced technological era"
        "RULER_SUCCEEDED" -> "new ruler assuming leadership in a ceremonial historical setting"
        "DYNASTY_FOUNDED" -> "founding of a new ruling dynasty in a ceremonial court"
        "ADULT_SOCIAL_EVENT" -> "private social event among adults in a historically appropriate interior"
        "BIOLOGICAL_DIVERGENCE" -> "fictional humanoid population showing visible evolutionary divergence"
        "STRUCTURAL_MUTATION" -> "fictional evolved humanoid lineage with a newly established body-plan trait"
        "HYBRID_LINEAGE_FORMED" -> "fictional hybrid humanoid lineage emerging in a settlement"
        "INTERVENTION_HARVEST_AID" -> "unexpectedly abundant harvest transforming fields and granaries"
        "INTERVENTION_DROUGHT" -> "severe drought striking farmland and reservoirs"
        "INTERVENTION_TECH_BOOST" -> "sudden technological leap reshaping a historical civilization"
        "INTERVENTION_STABILITY_SUPPORT" -> "public order and civic institutions recovering after instability"
        else -> "major historical event in a fictional civilization"
    }
}
