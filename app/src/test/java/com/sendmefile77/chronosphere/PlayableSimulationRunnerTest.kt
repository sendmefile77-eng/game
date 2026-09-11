package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayableSimulationRunnerTest {
    @Test
    fun repeatedRunFromSameStateIsDeterministic() {
        val world = WorldGenerator().generate(WorldSeed(424242L))
        val resources = WorldResourceGenerator().generate(world)
        val worldState = CivilizationEngine(world, resources).initialize()
        val peopleEngine = PeopleEngine()
        val people = peopleEngine.initialize(worldState)
        val economy = EconomyEngine(world, resources).initialize(worldState)
        val evolution = EvolutionEngine(world).initialize(worldState)
        val runner = PlayableSimulationRunner(
            worldMap = world,
            resources = resources,
            peopleEngine = peopleEngine,
            adultModule = NoOpAdultModule,
        )

        val first = runner.advance(worldState, people, economy, evolution, months = 120)
        val second = runner.advance(worldState, people, economy, evolution, months = 120)

        assertEquals(first, second)
        assertEquals(120L, first.world.tick)
        assertEquals(first.world.tick, first.people.tick)
        assertEquals(first.world.tick, first.economy.tick)
        assertEquals(first.world.tick, first.evolution.tick)
        assertTrue(first.world.totalPopulation >= 0L)
        assertTrue(first.world.civilizations.all { it.treasury.isFinite() && it.stability.isFinite() && it.technology.isFinite() })
    }
}
