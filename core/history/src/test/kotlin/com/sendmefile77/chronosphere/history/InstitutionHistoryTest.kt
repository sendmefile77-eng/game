package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstitutionHistoryTest {
    @Test
    fun institutionReformCreatesTransitionAndLegacyWithoutInventedCause() {
        val event = SimulationEvent(
            id = "player-institution-1",
            tick = 120L,
            code = "INTERVENTION_INSTITUTION_REFORM",
            actorIds = listOf("civ-1"),
            facts = mapOf("institution" to "COURTS"),
        )
        val world = LivingPlanetState(
            worldSeed = 42L,
            tick = 120L,
            civilizations = listOf(Civilization("civ-1", "Ардан", 1_000L, 0.58, 0.22, 60.0)),
            settlements = listOf(Settlement("city-1", "Астра", "civ-1", 2, 2, 1_000L, 400.0, 100.0, 0L)),
            recentEvents = listOf(event),
        )

        val memory = HistoricalMemoryEngine.reconcile(null, world)

        assertTrue(memory.activeProcessesFor("civ-1").any { it.kind == HistoricalProcessKind.INSTITUTIONAL_TRANSITION })
        assertTrue(memory.legaciesFor("civ-1").any { it.kind == HistoricalLegacyKind.INSTITUTIONAL_MEMORY })
        assertEquals(0, memory.causalLinksFor("civ-1").size)
    }
}
