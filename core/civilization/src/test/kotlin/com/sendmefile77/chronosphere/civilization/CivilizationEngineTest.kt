package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
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
}
