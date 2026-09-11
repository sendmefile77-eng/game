package com.sendmefile77.chronosphere.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeterministicRngTest {
    @Test fun sameSeedProducesSameSequence() {
        val a = DeterministicRng(WorldSeed(77))
        val b = DeterministicRng(WorldSeed(77))
        repeat(100) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test fun differentSeedsDiverge() {
        val a = DeterministicRng(WorldSeed(1)).nextLong()
        val b = DeterministicRng(WorldSeed(2)).nextLong()
        assertNotEquals(a, b)
    }
}
