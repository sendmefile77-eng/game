package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.people.PeopleEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleSnapshotV1Test {
    @Test
    fun roundTripPreservesPeopleState() {
        val world = LivingPlanetState(
            worldSeed = 42L,
            tick = 240L,
            civilizations = listOf(Civilization("c1", "Ардан", 1_200L, 0.7, 0.2, 60.0)),
            settlements = listOf(Settlement("s1", "Астра", "c1", 2, 3, 1_200L, 900.0, 50.0, 0L)),
        )
        val state = PeopleEngine().initialize(world)
        assertEquals(state, PeopleSnapshotV1.decode(PeopleSnapshotV1.encode(state)))
    }
}
