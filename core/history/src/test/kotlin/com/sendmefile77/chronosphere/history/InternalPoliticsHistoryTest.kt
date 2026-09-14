package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.ProvinceState
import com.sendmefile77.chronosphere.civilization.RebellionState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalPoliticsHistoryTest {
    @Test
    fun taxesUnrestAndRebellionBecomeCausalMemory() {
        val events = listOf(
            SimulationEvent("tax-1", 12L, "TAXES_RAISED", actorIds = listOf("civ-1")),
            SimulationEvent("unrest-1", 24L, "PROVINCIAL_UNREST", actorIds = listOf("civ-1"), locationId = "city-1"),
            SimulationEvent("rebellion-1", 36L, "REBELLION_STARTED", actorIds = listOf("civ-1"), locationId = "city-1"),
        )
        val world = LivingPlanetState(
            worldSeed = 12L,
            tick = 36L,
            civilizations = listOf(Civilization("civ-1", "Ардан", 1_000L, 0.40, 0.15, 30.0)),
            settlements = listOf(Settlement("city-1", "Астра", "civ-1", 2, 2, 1_000L, 300.0, 100.0, 0L)),
            recentEvents = events,
            provinces = listOf(ProvinceState("province:city-1", "civ-1", "city-1", 0.18, 0.84, 0.20, 0.75, 36L)),
            rebellions = listOf(RebellionState("rebellion-city-1-36", "civ-1", "province:city-1", 36L, 36L, 0.84)),
        )

        val memory = HistoricalMemoryEngine.reconcile(null, world)

        assertTrue(memory.activeProcessesFor("civ-1").any { it.kind == HistoricalProcessKind.INTERNAL_CRISIS })
        assertTrue(memory.legaciesFor("civ-1").any { it.kind == HistoricalLegacyKind.REBELLION_MEMORY })
        val links = memory.causalLinksFor("civ-1")
        assertEquals(2, links.size)
        assertTrue(links.any { it.causeEventId == "tax-1" && it.effectEventId == "unrest-1" })
        assertTrue(links.any { it.causeEventId == "unrest-1" && it.effectEventId == "rebellion-1" })
    }
}
