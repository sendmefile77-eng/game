package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Test

class GameSnapshotV1Test {
    @Test
    fun roundTripPreservesSimulationState() {
        val state = LivingPlanetState(
            worldSeed = 42L,
            tick = 120L,
            civilizations = listOf(Civilization("c1", "Test", 1200, 0.7, 0.1, 55.0, setOf("coastal"))),
            settlements = listOf(Settlement("s1", "Port", "c1", 3, 4, 1200, 900.0, 44.0, 0)),
        )
        assertEquals(state, GameSnapshotV1.decode(GameSnapshotV1.encode(state)))
    }
}
