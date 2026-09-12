package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import com.sendmefile77.chronosphere.adultcontracts.MediaCue
import com.sendmefile77.chronosphere.evolution.BiologicalRank
import com.sendmefile77.chronosphere.evolution.BodyPlan
import com.sendmefile77.chronosphere.evolution.EvolutionPopulation
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.MorphologyProfile
import com.sendmefile77.chronosphere.evolution.PopulationLineage
import com.sendmefile77.chronosphere.evolution.SkinCovering
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorphologyContextAdultModuleTest {
    @Test
    fun enrichesContractV1RequestWithDeterministicMorphologyContext() {
        val recorder = RecordingModule()
        val module = MorphologyContextAdultModule(recorder, people(), evolution())
        val request = AdultEventRequest(
            requestId = "r1",
            participants = listOf(AdultParticipantRef("adult-a", 30)),
            context = AdultWorldContext(
                worldSeed = 9L,
                tick = 120L,
                cultureTags = setOf("courtly"),
                numericContext = mapOf("wealth" to 0.6),
            ),
        )

        val result = module.evaluate(request)
        val enriched = recorder.lastRequest!!
        assertEquals("r1", enriched.requestId)
        assertTrue("courtly" in enriched.context.cultureTags)
        assertTrue("arms:4" in enriched.context.cultureTags)
        assertTrue("tail" in enriched.context.cultureTags)
        assertTrue("mixed_ancestry" in enriched.context.cultureTags)
        assertEquals(1.2, enriched.context.numericContext["morph_height"]!!, 0.000001)
        assertEquals(0.30, enriched.context.numericContext["morph_admixture"]!!, 0.000001)
        assertEquals(0.6, enriched.context.numericContext["wealth"]!!, 0.000001)
        assertTrue("pmorph:0:arms:4" in result.mediaCue!!.tags)
        assertTrue("pmorph:0:tail:1" in result.mediaCue!!.tags)
        assertTrue("pmorph:0:height:120" in result.mediaCue!!.tags)

        module.evaluate(request)
        assertEquals(enriched, recorder.lastRequest)
    }

    @Test
    fun preservesDifferentBodyPlansForEachAdultParticipant() {
        val recorder = RecordingModule()
        val module = MorphologyContextAdultModule(recorder, people(twoParticipants = true), evolution(twoPopulations = true))
        val request = AdultEventRequest(
            requestId = "pair",
            participants = listOf(
                AdultParticipantRef("adult-a", 30),
                AdultParticipantRef("adult-b", 31),
            ),
            context = AdultWorldContext(worldSeed = 9L, tick = 120L, cultureTags = setOf("open")),
        )

        val result = module.evaluate(request)
        val tags = result.mediaCue!!.tags
        assertTrue("pmorph:0:arms:4" in tags)
        assertTrue("pmorph:0:eyes:2" in tags)
        assertTrue("pmorph:0:tail:1" in tags)
        assertTrue("pmorph:1:arms:2" in tags)
        assertTrue("pmorph:1:eyes:4" in tags)
        assertTrue("pmorph:1:covering:scales" in tags)
        assertFalse("pmorph:1:tail:1" in tags)
    }

    @Test
    fun missingPopulationLeavesRequestBackwardCompatible() {
        val recorder = RecordingModule()
        val module = MorphologyContextAdultModule(recorder, people(), evolution().copy(populations = emptyList()))
        val request = AdultEventRequest(
            requestId = "r2",
            participants = listOf(AdultParticipantRef("adult-a", 30)),
            context = AdultWorldContext(worldSeed = 9L, tick = 120L, cultureTags = setOf("classic")),
        )

        module.evaluate(request)
        assertEquals(request, recorder.lastRequest)
    }

    private class RecordingModule : AdultModule {
        override val contractVersion: Int = ADULT_CONTRACT_VERSION
        var lastRequest: AdultEventRequest? = null
        override fun evaluate(request: AdultEventRequest): AdultModuleResult {
            lastRequest = request
            return AdultModuleResult(
                requestId = request.requestId,
                eventCode = "RECORDED",
                mediaCue = MediaCue(
                    assetKey = "adult://recipe/test",
                    tags = setOf("recipe:test", "event:recorded"),
                ),
            )
        }
    }

    private fun people(twoParticipants: Boolean = false): PeopleState = PeopleState(
        worldSeed = 9L,
        tick = 120L,
        persons = buildList {
            add(person("adult-a", "city-a", -20L * 12L))
            if (twoParticipants) add(person("adult-b", "city-b", -21L * 12L))
        },
        dynasties = emptyList(),
        relationships = emptyList(),
        rulerByCivilization = emptyMap(),
        socialProfiles = emptyList(),
    )

    private fun person(id: String, settlementId: String, birthTick: Long): NotablePerson = NotablePerson(
        id = id,
        name = id,
        civilizationId = "civ-a",
        settlementId = settlementId,
        dynastyId = null,
        birthTick = birthTick,
        deathTick = null,
        role = PersonRole.NOTABLE,
        prestige = 0.5,
        aptitude = 0.8,
    )

    private fun evolution(twoPopulations: Boolean = false): EvolutionState {
        val lineageA = PopulationLineage(
            id = "lineage-hybrid",
            parentLineageId = "lineage-a",
            secondaryParentLineageId = "lineage-b",
            label = "Гібридна лінія",
            originSettlementId = "city-a",
            formedTick = 0L,
            rank = BiologicalRank.SUBSPECIES,
            generation = 4,
            morphology = MorphologyProfile(heightScale = 1.2),
            bodyPlan = BodyPlan(armPairs = 2, hasTail = true),
            divergenceFromOrigin = 0.18,
            tags = setOf("hybrid", "mixed_ancestry"),
        )
        val lineages = mutableListOf(lineageA)
        val populations = mutableListOf(
            EvolutionPopulation(
                id = "population-city-a",
                settlementId = "city-a",
                lineageId = lineageA.id,
                population = 1000L,
                isolation = 0.2,
                geneFlow = 0.8,
                ancestry = mapOf("lineage-a" to 0.70, "lineage-b" to 0.30),
                culturalIdentity = mapOf("civ-a" to 1.0),
            ),
        )
        if (twoPopulations) {
            val lineageB = PopulationLineage(
                id = "lineage-scaled",
                parentLineageId = "lineage-human",
                label = "Луската лінія",
                originSettlementId = "city-b",
                formedTick = 0L,
                rank = BiologicalRank.MORPH,
                generation = 2,
                morphology = MorphologyProfile(eyeSize = 0.75, pigmentation = 0.7),
                bodyPlan = BodyPlan(eyeCount = 4, covering = SkinCovering.SCALES),
                divergenceFromOrigin = 0.14,
                tags = setOf("structural_divergence"),
            )
            lineages += lineageB
            populations += EvolutionPopulation(
                id = "population-city-b",
                settlementId = "city-b",
                lineageId = lineageB.id,
                population = 800L,
                isolation = 0.4,
                geneFlow = 0.6,
                ancestry = mapOf(lineageB.id to 1.0),
                culturalIdentity = mapOf("civ-a" to 1.0),
            )
        }
        return EvolutionState(
            worldSeed = 9L,
            tick = 120L,
            lineages = lineages,
            populations = populations,
        )
    }
}
