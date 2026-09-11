package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionEngineTest {
    private val engine = InterventionEngine()

    @Test
    fun identicalInterventionIsDeterministic() {
        val state = sampleState()
        val command = InterventionCommand(
            id = "test-drought",
            kind = InterventionKind.DROUGHT,
            civilizationId = "civ-a",
            strength = 0.7,
        )
        assertEquals(engine.apply(state, command), engine.apply(state, command))
    }

    @Test
    fun droughtReducesTargetFoodAndPopulationAndKeepsTotalsConsistent() {
        val state = sampleState()
        val result = engine.apply(
            state,
            InterventionCommand("drought-1", InterventionKind.DROUGHT, "civ-a", 0.8),
        )
        val before = state.settlements.first { it.civilizationId == "civ-a" }
        val after = result.settlements.first { it.civilizationId == "civ-a" }
        assertTrue(after.foodStock < before.foodStock)
        assertTrue(after.population < before.population)
        assertEquals(after.population, result.civilizations.first { it.id == "civ-a" }.population)
        assertEquals("INTERVENTION_DROUGHT", result.recentEvents.last().code)
    }

    @Test
    fun technologyBoostChangesOnlySelectedCivilization() {
        val state = sampleState()
        val result = engine.apply(
            state,
            InterventionCommand("tech-1", InterventionKind.TECHNOLOGY_BOOST, "civ-a", 0.6),
        )
        assertTrue(result.civilizations.first { it.id == "civ-a" }.technology > 0.10)
        assertEquals(0.20, result.civilizations.first { it.id == "civ-b" }.technology, 0.0)
    }

    @Test
    fun harvestAidIncreasesFoodWithoutChangingPopulation() {
        val state = sampleState()
        val result = engine.apply(
            state,
            InterventionCommand("aid-1", InterventionKind.HARVEST_AID, "civ-a", 0.5),
        )
        val before = state.settlements.first { it.civilizationId == "civ-a" }
        val after = result.settlements.first { it.civilizationId == "civ-a" }
        assertTrue(after.foodStock > before.foodStock)
        assertEquals(before.population, after.population)
    }

    private fun sampleState(): LivingPlanetState {
        val civA = Civilization("civ-a", "Ardan", 1_000L, 0.60, 0.10, 70.0)
        val civB = Civilization("civ-b", "Velor", 900L, 0.70, 0.20, 80.0)
        return LivingPlanetState(
            worldSeed = 77L,
            tick = 96L,
            civilizations = listOf(civA, civB),
            settlements = listOf(
                Settlement("city-a", "Astra", "civ-a", 3, 4, 1_000L, 650.0, 50.0, 0L),
                Settlement("city-b", "Bren", "civ-b", 12, 6, 900L, 620.0, 48.0, 0L),
            ),
        )
    }
}
