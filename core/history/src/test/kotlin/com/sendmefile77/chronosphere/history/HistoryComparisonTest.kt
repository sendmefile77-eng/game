package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryComparisonTest {
    @Test
    fun reportsDivergenceAgainstReferenceTimeline() {
        val original = state(population = 1_000L, settlements = 1, technology = 0.20)
        val alternative = state(population = 1_240L, settlements = 2, technology = 0.35)
        val comparison = HistoryComparator.compare(original, alternative)

        assertEquals(240L, comparison.populationDelta)
        assertEquals(1, comparison.settlementDelta)
        assertEquals(0, comparison.warDelta)
        assertEquals(0, comparison.allianceDelta)
        assertEquals(0.15, comparison.averageTechnologyDelta, 0.000001)
    }

    private fun state(population: Long, settlements: Int, technology: Double): LivingPlanetState = LivingPlanetState(
        worldSeed = 7L,
        tick = 120L,
        civilizations = listOf(Civilization("c1", "Test", population, 0.7, technology, 10.0)),
        settlements = (0 until settlements).map { index ->
            Settlement(
                id = "s$index",
                name = "S$index",
                civilizationId = "c1",
                x = index,
                y = 0,
                population = population / settlements,
                foodStock = 100.0,
                wealth = 10.0,
                foundedTick = 0L,
            )
        },
    )
}
