package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PoliticsDeterminismTest {
    @Test
    fun politicsAndTerritoryAreDeterministic() {
        val world = WorldGenerator().generate(WorldSeed(987654321L))
        val resources = WorldResourceGenerator().generate(world)
        val engine = CivilizationEngine(world, resources)
        val initial = engine.initialize()
        val first = engine.advance(initial, 480)
        val second = engine.advance(initial, 480)
        assertEquals(first, second)
        val resolver = TerritoryResolver()
        assertArrayEquals(resolver.resolve(world, first), resolver.resolve(world, second))
    }
}
