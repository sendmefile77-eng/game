package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalMemoryEngineTest {
    @Test
    fun structuralChoicePersistsInCivilizationAndProducesRecurringTradeoff() {
        val base = state()
        val activated = HistoricalCommitmentEngine.activate(
            state = base,
            civilizationId = "civ-a",
            choiceId = "era-push",
            originTick = base.tick,
        )
        val policy = HistoricalCommitmentEngine.active(activated.civilizations.first()).single()
        assertEquals("era-push", policy.definition.id)

        val advanced = HistoricalCommitmentEngine.applyRecurring(activated, months = 1200)
        assertTrue(advanced.civilizations.first().technology > activated.civilizations.first().technology)
        assertTrue(advanced.civilizations.first().stability < activated.civilizations.first().stability)
    }

    @Test
    fun newerChoiceInSameFamilySupersedesOlderPolicyTag() {
        val first = HistoricalCommitmentEngine.activate(state(), "civ-a", "era-push", 120L)
        val second = HistoricalCommitmentEngine.activate(first, "civ-a", "era-consolidate", 240L)
        val active = HistoricalCommitmentEngine.active(second.civilizations.first())

        assertEquals(1, active.size)
        assertEquals("era-consolidate", active.single().definition.id)
        assertEquals(240L, active.single().originTick)
    }

    @Test
    fun memoryBuildsFoundationsAndDoesNotDuplicateSameEvent() {
        val event = SimulationEvent(
            id = "war-1-start",
            tick = 120L,
            code = "WAR_STARTED",
            actorIds = listOf("civ-a", "civ-b"),
        )
        val world = state(tick = 120L, events = listOf(event), includeSecondCivilization = true)
        val first = HistoricalMemoryEngine.reconcile(null, world, economy = economy(world))
        val second = HistoricalMemoryEngine.reconcile(first, world, economy = economy(world))

        assertEquals(3, first.foundationsFor("civ-a").size)
        assertEquals(1, first.processes.count { it.kind == HistoricalProcessKind.WAR })
        assertEquals(first.processes, second.processes)
    }

    @Test
    fun memoryArchivesSupersededCommitmentInsteadOfForgettingIt() {
        val firstWorld = HistoricalCommitmentEngine.activate(state(tick = 120L), "civ-a", "rule-legitimacy", 120L)
        val first = HistoricalMemoryEngine.reconcile(null, firstWorld, economy = economy(firstWorld))
        val secondWorld = HistoricalCommitmentEngine.activate(firstWorld.copy(tick = 240L), "civ-a", "rule-reform", 240L)
        val second = HistoricalMemoryEngine.reconcile(first, secondWorld, economy = economy(secondWorld))

        assertTrue(second.commitments.any { it.choiceId == "rule-legitimacy" && it.status == HistoricalCommitmentStatus.SUPERSEDED })
        assertTrue(second.commitments.any { it.choiceId == "rule-reform" && it.status == HistoricalCommitmentStatus.ACTIVE })
    }

    private fun state(
        tick: Long = 120L,
        events: List<SimulationEvent> = emptyList(),
        includeSecondCivilization: Boolean = false,
    ): LivingPlanetState {
        val civilizations = buildList {
            add(Civilization("civ-a", "Ардан", 1_000L, 0.70, 0.20, 100.0))
            if (includeSecondCivilization) add(Civilization("civ-b", "Белар", 900L, 0.66, 0.18, 90.0))
        }
        val settlements = buildList {
            add(Settlement("city-a", "Астра", "civ-a", 2, 3, 1_000L, 800.0, 90.0, 0L))
            if (includeSecondCivilization) add(Settlement("city-b", "Бера", "civ-b", 8, 7, 900L, 700.0, 80.0, 0L))
        }
        return LivingPlanetState(
            worldSeed = 42L,
            tick = tick,
            civilizations = civilizations,
            settlements = settlements,
            recentEvents = events,
        )
    }

    private fun economy(world: LivingPlanetState): EconomyState = EconomyState(
        worldSeed = world.worldSeed,
        tick = world.tick,
        civilizations = world.civilizations.map { civilization ->
            CivilizationEconomy(
                civilizationId = civilization.id,
                era = TechnologyEra.AGRARIAN,
                stockpiles = mapOf(EconomicGood.FOOD to 100.0),
                production = mapOf(EconomicGood.FOOD to 90.0, EconomicGood.CRAFTS to 20.0),
                demand = mapOf(EconomicGood.FOOD to 80.0),
                shortageIndex = 0.02,
                tradeBalance = 0.0,
                grossOutput = 110.0,
            )
        },
    )
}
