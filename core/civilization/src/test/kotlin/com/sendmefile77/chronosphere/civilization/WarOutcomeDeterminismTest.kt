package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarOutcomeDeterminismTest {
    @Test
    fun longSimulationIsDeterministicWithPolitics() {
        val world = WorldGenerator().generate(WorldSeed(991122L))
        val resources = WorldResourceGenerator().generate(world)
        val engine = CivilizationEngine(world, resources)
        val initialA = engine.initialize(8)
        val initialB = engine.initialize(8)
        val a = engine.advance(initialA, 2_400)
        val b = engine.advance(initialB, 2_400)
        assertEquals(a, b)
        assertTrue(a.relations.isNotEmpty())
    }
}
