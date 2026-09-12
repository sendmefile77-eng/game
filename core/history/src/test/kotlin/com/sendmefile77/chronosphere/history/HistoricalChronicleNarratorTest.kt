package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalChronicleNarratorTest {
    @Test
    fun groupsRawEventsIntoCausalProcessInsteadOfEventList() {
        val events = listOf(
            SimulationEvent("war-1", 12L, "WAR_STARTED", actorIds = listOf("civ-a"), facts = mapOf("civilization" to "Ардан")),
            SimulationEvent("war-2", 24L, "WAR_CASUALTIES", actorIds = listOf("civ-a"), facts = mapOf("civilization" to "Ардан")),
            SimulationEvent("peace-1", 36L, "PEACE_TREATY", actorIds = listOf("civ-a"), facts = mapOf("civilization" to "Ардан")),
        )

        val bridge = HistoricalChronicleNarrator.fromEvents(events, mapOf("civ-a" to "Ардан"))!!
        assertEquals("Чому світ став таким", bridge.titleUk)
        assertTrue(bridge.bodyUk.contains("Воєнний цикл"))
        assertTrue(bridge.bodyUk.contains("миру"))
        assertTrue(bridge.tracesUk.single().contains("3 фактів"))
    }
}
