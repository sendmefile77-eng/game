package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSnapshotV1Test {
    @Test
    fun roundTripPreservesSimulationStateAndChronicle() {
        val state = LivingPlanetState(
            worldSeed = 42L,
            tick = 120L,
            civilizations = listOf(
                Civilization(
                    "c1",
                    "Test",
                    1200,
                    0.7,
                    0.1,
                    55.0,
                    setOf(
                        "coastal",
                        "era-choice:breakthrough:fire",
                        "foundation:fire_mastery",
                        "era-choice:subsistence:predator_hunters",
                        "policy:predator_hunters",
                        "hist:blood_hunt",
                    ),
                ),
            ),
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
        val decoded = GameSnapshotV1.decode(GameSnapshotV1.encode(state))
        assertEquals(state, decoded)
        assertTrue("era-choice:breakthrough:fire" in decoded.civilizations.single().cultureTags)
        assertTrue("era-choice:subsistence:predator_hunters" in decoded.civilizations.single().cultureTags)
        assertTrue("hist:blood_hunt" in decoded.civilizations.single().cultureTags)
    }

    @Test
    fun legacySaveWithoutEventRowsStillLoads() {
        val legacy = listOf(
            "CHRONOSPHERE_SAVE_V1",
            "WORLD\t42\t120",
            "CIV\tc1\tTest\t1200\t0.7\t0.1\t55.0\tcoastal",
            "SET\ts1\tPort\tc1\t3\t4\t1200\t900.0\t44.0\t0",
        ).joinToString("\n")

        val decoded = GameSnapshotV1.decode(legacy)
        assertEquals(42L, decoded.worldSeed)
        assertEquals(120L, decoded.tick)
        assertEquals(1, decoded.civilizations.size)
        assertEquals(1, decoded.settlements.size)
        assertTrue(decoded.recentEvents.isEmpty())
    }
}
