package com.sendmefile77.chronosphere.worldgen

import com.sendmefile77.chronosphere.simulation.WorldSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorldGeneratorTest {
    private val generator = WorldGenerator()

    @Test fun sameSeedProducesSameFingerprint() {
        assertEquals(generator.generate(WorldSeed(42)).fingerprint, generator.generate(WorldSeed(42)).fingerprint)
    }

    @Test fun differentSeedsProduceDifferentFingerprint() {
        assertNotEquals(generator.generate(WorldSeed(42)).fingerprint, generator.generate(WorldSeed(43)).fingerprint)
    }
}
