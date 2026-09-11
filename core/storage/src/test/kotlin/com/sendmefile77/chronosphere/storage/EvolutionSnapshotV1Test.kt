package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.evolution.BiologicalRank
import com.sendmefile77.chronosphere.evolution.BodyPlan
import com.sendmefile77.chronosphere.evolution.EvolutionPopulation
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.MorphologyProfile
import com.sendmefile77.chronosphere.evolution.PopulationLineage
import org.junit.Assert.assertEquals
import org.junit.Test

class EvolutionSnapshotV1Test {
    @Test
    fun hybridAncestryAndCultureRoundTrip() {
        val a = PopulationLineage(
            id = "a", parentLineageId = null, label = "A", originSettlementId = "city",
            formedTick = 0L, rank = BiologicalRank.MORPH, generation = 1,
            morphology = MorphologyProfile(pigmentation = 0.2),
        )
        val b = PopulationLineage(
            id = "b", parentLineageId = null, label = "B", originSettlementId = "city2",
            formedTick = 0L, rank = BiologicalRank.MORPH, generation = 1,
            morphology = MorphologyProfile(pigmentation = 0.8),
        )
        val hybrid = PopulationLineage(
            id = "h", parentLineageId = "a", secondaryParentLineageId = "b",
            label = "Hybrid", originSettlementId = "city", formedTick = 120L,
            rank = BiologicalRank.SUBSPECIES, generation = 2,
            morphology = MorphologyProfile(pigmentation = 0.5), bodyPlan = BodyPlan(),
            divergenceFromOrigin = 0.11, tags = setOf("hybrid", "mixed_ancestry"),
        )
        val state = EvolutionState(
            worldSeed = 77L,
            tick = 240L,
            lineages = listOf(a, b, hybrid),
            populations = listOf(
                EvolutionPopulation(
                    id = "p", settlementId = "city", lineageId = "h", population = 5000,
                    isolation = 0.2, geneFlow = 0.8, ancestry = mapOf("a" to 0.55, "b" to 0.45),
                    culturalIdentity = mapOf("civ-a" to 0.25, "civ-b" to 0.75),
                ),
            ),
        )
        assertEquals(state, EvolutionSnapshotV1.decode(EvolutionSnapshotV1.encode(state)))
    }
}
