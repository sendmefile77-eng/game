package com.sendmefile77.chronosphere.llm

import com.sendmefile77.chronosphere.GameBriefing
import com.sendmefile77.chronosphere.GameObjective
import com.sendmefile77.chronosphere.NeighborStanding
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldLlmAdvisorTest {
    @Test
    fun promptUsesCalculatedBriefingVisibleActionsAndPersistentHistoricalLegacy() {
        val civilization = Civilization(
            "civ-a",
            "Нері",
            1_200L,
            0.34,
            0.18,
            55.0,
            cultureTags = setOf(
                "era-choice:subsistence:predator_hunters",
                "policy:predator_hunters",
                "foundation:stone_tools",
            ),
        )
        val state = LivingPlanetState(
            worldSeed = 7L,
            tick = 24L,
            civilizations = listOf(civilization),
            settlements = emptyList(),
        )
        val economy = EconomyState(
            worldSeed = 7L,
            tick = 24L,
            civilizations = listOf(
                CivilizationEconomy(
                    civilizationId = "civ-a",
                    era = TechnologyEra.TRIBAL,
                    stockpiles = emptyMap(),
                    production = emptyMap(),
                    demand = emptyMap(),
                    shortageIndex = 0.42,
                    tradeBalance = -3.0,
                    grossOutput = 12.0,
                ),
            ),
        )
        val briefing = GameBriefing(
            headline = "Нері хитається: низька стабільність",
            pressure = "Їжа достатньо · порядок напруга",
            hint = "Порядок піднімає стабільність.",
            wars = emptyList(),
            allies = emptyList(),
            neighbors = listOf(
                NeighborStanding("civ-b", "Варки", -0.30, false, false, "холод"),
            ),
            latestEvent = "Голод",
            objective = GameObjective(
                title = "Втримай державу",
                detail = "Підніми порядок.",
                meter = "стабільність 34% / 38%",
                complete = false,
            ),
        )

        val prompt = WorldLlmAdvisor.buildPrompt(state, civilization, economy, briefing)

        assertTrue(prompt.contains("Нері"))
        assertTrue(prompt.contains("Втримай державу"))
        assertTrue(prompt.contains("Варки"))
        assertTrue(prompt.contains("predator hunters"))
        assertTrue(prompt.contains("stone tools"))
        assertTrue(prompt.contains("довготривала спадщина"))
        WorldLlmAdvisor.ALLOWED_ACTIONS.forEach { action -> assertTrue(prompt.contains(action)) }
    }
}
