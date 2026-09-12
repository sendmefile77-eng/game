package com.sendmefile77.chronosphere.evolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEvolutionInterventionEngineTest {
    private val engine = PlayerEvolutionInterventionEngine()

    @Test
    fun identicalHumanLinesAreNotHybridCandidates() {
        val state = EvolutionState(
            worldSeed = 7L,
            tick = 0L,
            lineages = listOf(
                lineage("a", BodyPlan(), MorphologyProfile.HUMAN_BASELINE),
                lineage("b", BodyPlan(), MorphologyProfile.HUMAN_BASELINE),
            ),
            populations = listOf(
                population("city-a", "a"),
                population("city-b", "b"),
            ),
        )

        assertNull(engine.bestHybridCandidate(state, "city-a"))
    }

    @Test
    fun structurallyDifferentLineIsEligibleForHybridization() {
        val state = EvolutionState(
            worldSeed = 7L,
            tick = 0L,
            lineages = listOf(
                lineage("a", BodyPlan(), MorphologyProfile.HUMAN_BASELINE),
                lineage("b", BodyPlan(armPairs = 2, eyeCount = 4, hasTail = true), MorphologyProfile(heightScale = 1.12)),
            ),
            populations = listOf(
                population("city-a", "a"),
                population("city-b", "b"),
            ),
        )

        val candidate = engine.bestHybridCandidate(state, "city-a")
        requireNotNull(candidate)
        assertEquals("b", candidate.lineageId)
        assertTrue(candidate.difference >= PlayerEvolutionInterventionEngine.MIN_HYBRID_DIFFERENCE)
    }

    private fun lineage(id: String, bodyPlan: BodyPlan, morphology: MorphologyProfile): PopulationLineage =
        PopulationLineage(
            id = id,
            parentLineageId = null,
            label = "Line $id",
            originSettlementId = "city-$id",
            formedTick = 0L,
            rank = BiologicalRank.POPULATION,
            generation = 1,
            morphology = morphology,
            bodyPlan = bodyPlan,
            divergenceFromOrigin = morphology.distanceTo(MorphologyProfile.HUMAN_BASELINE),
        )

    private fun population(settlementId: String, lineageId: String): EvolutionPopulation =
        EvolutionPopulation(
            id = "population-$settlementId",
            settlementId = settlementId,
            lineageId = lineageId,
            population = 1000L,
            isolation = 0.5,
            geneFlow = 0.5,
        )
}
