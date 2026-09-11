package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.AdmixtureEngine
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.society.SocietyEngine
import com.sendmefile77.chronosphere.worldgen.ResourceDeposit
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlayableLongRunIntegrationTest {
    @Test
    fun sixtyYearWorldRunIsDeterministicAcrossAllPersistentLayers() {
        val first = runWorld(seed = 424242L, years = 60)
        val second = runWorld(seed = 424242L, years = 60)

        assertEquals(first.state, second.state)
        assertEquals(first.people, second.people)
        assertEquals(first.economy, second.economy)
        assertEquals(first.evolution, second.evolution)
        assertHealthy(first, expectedYears = 60)
    }

    @Test
    fun severalSeedsRemainHealthyForFortyYears() {
        listOf(1L, -1L, 987654321L).forEach { seed ->
            assertHealthy(runWorld(seed = seed, years = 40), expectedYears = 40)
        }
    }

    @Test
    fun longRunWorldRoundTripsThroughBranchWorkspace() {
        val run = runWorld(seed = 987654321L, years = 40)
        val timeline = HistoryTimeline()
        var workspace = timeline.create(run.state, run.people, run.economy, run.evolution)
        workspace = timeline.checkpoint(workspace, "Playable smoke point")
        workspace = timeline.fork(workspace, "Playable smoke branch")

        val encoded = HistoryWorkspaceSnapshotV1.encode(workspace)
        val decoded = HistoryWorkspaceSnapshotV1.decode(encoded)

        assertEquals(workspace, decoded)
        assertEquals(run.state, decoded.activeState)
        assertEquals(run.people, decoded.activePeopleState)
        assertEquals(run.economy, decoded.activeEconomyState)
        assertEquals(run.evolution, decoded.activeEvolutionState)
    }

    private fun runWorld(seed: Long, years: Int): RunState {
        val world = WorldGenerator().generate(WorldSeed(seed))
        val resources = WorldResourceGenerator().generate(world)
        val civilizationEngine = CivilizationEngine(world, resources)
        val peopleEngine = PeopleEngine()
        val economyEngine = EconomyEngine(world, resources)
        val evolutionEngine = EvolutionEngine(world)
        val admixtureEngine = AdmixtureEngine(world)
        val societyEngine = SocietyEngine()

        var state = civilizationEngine.initialize()
        var people = peopleEngine.initialize(state)
        var economy = economyEngine.initialize(state)
        var evolution = evolutionEngine.initialize(state)

        repeat(years) {
            val fromTick = state.tick
            val civilizationNext = civilizationEngine.advance(state, 12)
            val economyResult = economyEngine.advance(economy, civilizationNext)
            val peopleResult = peopleEngine.advance(people, economyResult.world)
            val worldWithPeople = economyResult.world.copy(
                recentEvents = (economyResult.world.recentEvents + peopleResult.events).takeLast(96),
            )
            val peopleAtTick = peopleResult.state.copy(tick = worldWithPeople.tick)
            val economyAtTick = economyResult.state.copy(tick = worldWithPeople.tick)

            val evolutionResult = evolutionEngine.advance(evolution, worldWithPeople)
            val admixtureEvents = mutableListOf<SimulationEvent>()
            val evolutionAtTick = admixtureEngine.annualStep(
                evolutionResult.state,
                worldWithPeople,
                worldWithPeople.tick,
                admixtureEvents,
            )
            val worldWithEvolution = worldWithPeople.copy(
                recentEvents = (worldWithPeople.recentEvents + evolutionResult.events + admixtureEvents).takeLast(96),
            )

            val societyResult = societyEngine.advance(
                fromTick = fromTick,
                world = worldWithEvolution,
                people = peopleAtTick,
                economy = economyAtTick,
            )
            state = societyResult.world
            people = societyResult.people.copy(tick = state.tick)
            economy = economyAtTick
            evolution = evolutionAtTick.copy(tick = state.tick)
        }

        return RunState(world, resources, state, people, economy, evolution)
    }

    private fun assertHealthy(run: RunState, expectedYears: Int) {
        assertEquals(expectedYears.toLong() * 12L, run.state.tick)
        assertTrue(run.state.civilizations.isNotEmpty())
        assertTrue(run.state.settlements.isNotEmpty())
        assertTrue(run.state.totalPopulation > 0L)
        assertTrue(run.people.persons.isNotEmpty())
        assertTrue(run.economy.civilizations.isNotEmpty())
        assertTrue(run.evolution.lineages.isNotEmpty())
        assertTrue(run.evolution.populations.isNotEmpty())

        run.state.civilizations.forEach { civilization ->
            assertTrue(civilization.population >= 0L)
            assertTrue(civilization.technology.isFinite())
            assertTrue(civilization.stability.isFinite())
            assertTrue(civilization.treasury.isFinite())
        }
        run.state.settlements.forEach { settlement ->
            assertTrue(settlement.population >= 0L)
            assertTrue(run.state.civilizations.any { it.id == settlement.civilizationId })
        }
        run.state.wars.forEach { war ->
            assertTrue(run.state.civilizations.any { it.id == war.civilizationA })
            assertTrue(run.state.civilizations.any { it.id == war.civilizationB })
        }
        run.state.alliances.forEach { alliance ->
            assertTrue(run.state.civilizations.any { it.id == alliance.civilizationA })
            assertTrue(run.state.civilizations.any { it.id == alliance.civilizationB })
        }
        run.economy.civilizations.forEach { civilization ->
            assertTrue(civilization.shortageIndex.isFinite())
            assertTrue(civilization.tradeBalance.isFinite())
            assertTrue(civilization.grossOutput.isFinite())
            assertTrue(civilization.stockpiles.values.all { it.isFinite() && it >= 0.0 })
        }
        run.evolution.populations.forEach { population ->
            assertTrue(population.population >= 0L)
            assertTrue(population.ancestry.values.all { it.isFinite() && it >= 0.0 })
            if (population.ancestry.isNotEmpty()) {
                assertTrue(abs(population.ancestry.values.sum() - 1.0) < 0.001)
            }
        }
    }

    private data class RunState(
        val world: WorldMap,
        val resources: List<ResourceDeposit>,
        val state: LivingPlanetState,
        val people: PeopleState,
        val economy: EconomyState,
        val evolution: EvolutionState,
    )
}
