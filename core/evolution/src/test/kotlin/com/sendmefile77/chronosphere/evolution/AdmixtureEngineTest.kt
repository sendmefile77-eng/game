package com.sendmefile77.chronosphere.evolution

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmixtureEngineTest {
    private val map = WorldMap(
        seed = WorldSeed(77L),
        width = 20,
        height = 10,
        tiles = List(200) { index ->
            WorldTile(index % 20, index / 20, 0.45, 0.55, 0.55, Biome.GRASSLAND)
        },
    )

    @Test
    fun stableContactProducesDeterministicHybridLineage() {
        val first = runYears(initialState(), 80)
        val second = runYears(initialState(), 80)
        assertEquals(first, second)

        val mixed = first.population("city-a")!!
        assertTrue(mixed.admixture >= 0.12)
        assertEquals(1.0, mixed.ancestry.values.sum(), 0.000001)
        val hybrid = first.lineage(mixed.lineageId)!!
        assertTrue(hybrid.isHybrid)
        assertTrue(hybrid.morphology.pigmentation in 0.20..0.80)
        assertTrue(first.visualDescriptor("city-a")!!.tags.contains("hybrid_lineage"))
    }

    @Test
    fun culturalAssimilationDoesNotRewriteBiologicalAncestry() {
        val base = initialState().copy(
            populations = initialState().populations.map { population ->
                if (population.settlementId == "city-a") {
                    population.copy(culturalIdentity = mapOf("civ-b" to 1.0))
                } else population
            },
        )
        val initialAncestry = base.population("city-a")!!.ancestry
        val evolved = runYears(base, 60)
        val city = evolved.population("city-a")!!

        assertTrue((city.culturalIdentity["civ-a"] ?: 0.0) > 0.60)
        assertNotEquals(mapOf("civ-a" to 1.0), city.ancestry)
        assertTrue(city.ancestry.keys.containsAll(initialAncestry.keys))
        assertEquals(1.0, city.ancestry.values.sum(), 0.000001)
    }

    private fun runYears(start: EvolutionState, years: Int): EvolutionState {
        val engine = AdmixtureEngine(map)
        val world = world()
        var state = start
        repeat(years) { year ->
            val events = mutableListOf<SimulationEvent>()
            state = engine.annualStep(state, world, (year + 1L) * 12L, events)
        }
        return state
    }

    private fun initialState(): EvolutionState {
        val a = PopulationLineage(
            id = "lineage-a",
            parentLineageId = EvolutionEngine.ORIGIN_LINEAGE_ID,
            label = "A",
            originSettlementId = "city-a",
            formedTick = 0,
            rank = BiologicalRank.MORPH,
            generation = 1,
            morphology = MorphologyProfile(pigmentation = 0.20, hairCoverage = 0.20, heightScale = 0.92),
            tags = setOf("human_derived"),
        )
        val b = PopulationLineage(
            id = "lineage-b",
            parentLineageId = EvolutionEngine.ORIGIN_LINEAGE_ID,
            label = "B",
            originSettlementId = "city-b",
            formedTick = 0,
            rank = BiologicalRank.MORPH,
            generation = 1,
            morphology = MorphologyProfile(pigmentation = 0.80, hairCoverage = 0.70, heightScale = 1.12),
            tags = setOf("human_derived"),
        )
        return EvolutionState(
            worldSeed = 77L,
            tick = 0L,
            lineages = listOf(a, b),
            populations = listOf(
                EvolutionPopulation(
                    id = "pop-a", settlementId = "city-a", lineageId = a.id, population = 10_000,
                    isolation = 0.0, geneFlow = 1.0, ancestry = mapOf(a.id to 1.0), culturalIdentity = mapOf("civ-a" to 1.0),
                ),
                EvolutionPopulation(
                    id = "pop-b", settlementId = "city-b", lineageId = b.id, population = 10_000,
                    isolation = 0.0, geneFlow = 1.0, ancestry = mapOf(b.id to 1.0), culturalIdentity = mapOf("civ-b" to 1.0),
                ),
            ),
        )
    }

    private fun world(): LivingPlanetState = LivingPlanetState(
        worldSeed = 77L,
        tick = 0L,
        civilizations = listOf(
            Civilization("civ-a", "A", 10_000, 0.7, 0.2, 100.0),
            Civilization("civ-b", "B", 10_000, 0.7, 0.2, 100.0),
        ),
        settlements = listOf(
            Settlement("city-a", "A", "civ-a", 8, 5, 10_000, 100.0, 100.0, 0),
            Settlement("city-b", "B", "civ-b", 9, 5, 10_000, 100.0, 100.0, 0),
        ),
    )
}
