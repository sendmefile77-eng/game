package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class GameSnapshotV1Test {
    @Test
    fun roundTripPreservesSimulationStateAndChronicle() {
        val state = LivingPlanetState(
            worldSeed = 42L,
            tick = 120L,
            civilizations = listOf(Civilization("c1", "Test", 1200, 0.7, 0.1, 55.0, setOf("coastal"))),
            settlements = listOf(Settlement("s1", "Port", "c1", 3, 4, 1200, 900.0, 44.0, 0)),
            recentEvents = listOf(
                SimulationEvent(
                    id = "evt:1/with delimiters",
                    tick = 120L,
                    code = "INTERVENTION_TECH_BOOST",
                    actorIds = listOf("c1"),
                    locationId = "s1",
                    numbers = mapOf("strength" to 0.65, "delta" to 0.12),
                    facts = mapOf("civilization" to "Test", "note" to "A:B, C\nD"),
                ),
            ),
        )
        assertEquals(state, GameSnapshotV1.decode(GameSnapshotV1.encode(state)))
    }
}
