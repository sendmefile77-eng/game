package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import com.sendmefile77.chronosphere.evolution.BiologicalRank
import com.sendmefile77.chronosphere.evolution.BodyPlan
import com.sendmefile77.chronosphere.evolution.EvolutionPopulation
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.MorphologyProfile
import com.sendmefile77.chronosphere.evolution.PopulationLineage
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole
import org.junit.Assert.assertEquals
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

        module.evaluate(request)
        val enriched = recorder.lastRequest!!
        assertEquals("r1", enriched.requestId)
        assertTrue("courtly" in enriched.context.cultureTags)
        assertTrue("arms:4" in enriched.context.cultureTags)
        assertTrue("tail" in enriched.context.cultureTags)
        assertTrue("mixed_ancestry" in enriched.context.cultureTags)
        assertEquals(1.2, enriched.context.numericContext["morph_height"]!!, 0.000001)
        assertEquals(0.30, enriched.context.numericContext["morph_admixture"]!!, 0.000001)
        assertEquals(0.6, enriched.context.numericContext["wealth"]!!, 0.000001)

        module.evaluate(request)
        assertEquals(enriched, recorder.lastRequest)
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
            return AdultModuleResult(requestId = request.requestId, eventCode = "RECORDED")
        }
    }

    private fun people(): PeopleState = PeopleState(
        worldSeed = 9L,
        tick = 120L,
        persons = listOf(
            NotablePerson(
                id = "adult-a",
                name = "Ара",
                civilizationId = "civ-a",
                settlementId = "city-a",
                dynastyId = null,
                birthTick = -20L * 12L,
                deathTick = null,
                role = PersonRole.NOTABLE,
                prestige = 0.5,
                aptitude = 0.8,
            ),
        ),
        dynasties = emptyList(),
        relationships = emptyList(),
        rulerByCivilization = emptyMap(),
        socialProfiles = emptyList(),
    )

    private fun evolution(): EvolutionState {
        val lineage = PopulationLineage(
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
        return EvolutionState(
            worldSeed = 9L,
            tick = 120L,
            lineages = listOf(lineage),
            populations = listOf(
                EvolutionPopulation(
                    id = "population-city-a",
                    settlementId = "city-a",
                    lineageId = lineage.id,
                    population = 1000L,
                    isolation = 0.2,
                    geneFlow = 0.8,
                    ancestry = mapOf("lineage-a" to 0.70, "lineage-b" to 0.30),
                    culturalIdentity = mapOf("civ-a" to 1.0),
                ),
            ),
        )
    }
}
