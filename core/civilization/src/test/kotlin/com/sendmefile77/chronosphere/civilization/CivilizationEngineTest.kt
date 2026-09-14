package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CivilizationEngineTest {
    @Test
    fun steppingIsDeterministicRegardlessOfBatchSize() {
        val world = WorldGenerator().generate(WorldSeed(424242L))
        val resources = WorldResourceGenerator().generate(world)
        val engine = CivilizationEngine(world, resources)
        val initial = engine.initialize()
        val oneBatch = engine.advance(initial, 120)
        var monthly = initial
        repeat(120) { monthly = engine.advance(monthly, 1) }
        assertEquals(oneBatch, monthly)
    }

    @Test
    fun oneTribeWorldCanCreateItsFirstSuccessorState() {
        val world = WorldGenerator().generate(WorldSeed(424242L))
        val resources = WorldResourceGenerator().generate(world)
        val engine = CivilizationEngine(world, resources)
        val initial = engine.initialize(1)
        val capital = initial.settlements.single()
        val frontier = capital.copy(
            id = "frontier-center",
            name = "Істра",
            x = (capital.x + 6).coerceAtMost(world.width - 1),
            population = 420L,
            foodStock = 320.0,
            wealth = 35.0,
            foundedTick = 600L,
        )
        val prepared = initial.copy(
            tick = 1_199L,
            civilizations = listOf(initial.civilizations.single().copy(population = capital.population + frontier.population)),
            settlements = listOf(capital, frontier),
        )

        val advanced = engine.advance(prepared, 1)

        assertEquals(2, advanced.civilizations.size)
        assertEquals(2, advanced.settlements.map { it.civilizationId }.distinct().size)
        assertTrue(advanced.relations.any { it.matches("civ-1", "civ-2") })
        assertTrue(advanced.recentEvents.any { it.code == "STATE_FOUNDED" && it.actorIds.firstOrNull() == "civ-2" })
    }
}
