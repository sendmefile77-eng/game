package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.civilization.WarState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSituationTest {
    @Test
    fun hungryCivilizationGetsAFoodObjective() {
        val briefing = GameSituation.briefing(
            state = state(food = 80.0),
            civilization = civ(),
            economy = null,
            pendingDecisionTitle = null,
        )
        assertTrue(briefing.objective.title.contains("голод"))
        assertFalse(briefing.objective.complete)
    }

    @Test
    fun warIsNamedInTheHeadlineAndObjective() {
        val briefing = GameSituation.briefing(
            state = state(food = 900.0, atWar = true),
            civilization = civ(),
            economy = null,
            pendingDecisionTitle = null,
        )
        assertTrue(briefing.headline.contains("Velor") || briefing.wars.contains("Velor"))
        assertTrue(briefing.objective.title.contains("вій"))
    }

    private fun civ(): Civilization = Civilization("civ-a", "Ardan", 1_000L, 0.62, 0.20, 40.0)

    private fun state(food: Double, atWar: Boolean = false): LivingPlanetState {
        val other = Civilization("civ-b", "Velor", 900L, 0.70, 0.20, 80.0)
        return LivingPlanetState(
            worldSeed = 1L,
            tick = 24L,
            civilizations = listOf(civ(), other),
            settlements = listOf(
                Settlement("city-a", "Astra", "civ-a", 3, 4, 1_000L, food, 20.0, 0L),
                Settlement("city-b", "Bren", "civ-b", 8, 6, 900L, 700.0, 20.0, 0L),
            ),
            wars = if (atWar) listOf(WarState("w1", "civ-a", "civ-b", 10L)) else emptyList(),
        )
    }
}
