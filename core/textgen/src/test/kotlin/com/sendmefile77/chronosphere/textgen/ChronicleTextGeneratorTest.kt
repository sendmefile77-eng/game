package com.sendmefile77.chronosphere.textgen

import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronicleTextGeneratorTest {
    private val generator = ChronicleTextGenerator()

    @Test
    fun foundedSettlementProducesReadableNarrativeWithConsequences() {
        val event = SimulationEvent(
            id = "found-1",
            tick = 0L,
            code = "SETTLEMENT_FOUNDED",
            facts = mapOf("settlement" to "Erenreach", "civilization" to "Нері"),
            numbers = mapOf("population" to 824.0),
        )

        val narrative = generator.narrative(event)

        assertTrue(narrative.title.contains("Нері"))
        assertTrue(narrative.hook.contains("Erenreach"))
        assertTrue(narrative.body.length > 80)
        assertTrue(narrative.significance.length > 60)
        assertTrue(narrative.changes.any { it.contains("824") })
    }

    @Test
    fun unknownEventStillGetsACompleteNarrative() {
        val narrative = generator.narrative(
            SimulationEvent(id = "x", tick = 0L, code = "UNSEEN_EVENT"),
        )

        assertTrue(narrative.title.isNotBlank())
        assertTrue(narrative.hook.isNotBlank())
        assertTrue(narrative.body.isNotBlank())
        assertTrue(narrative.significance.isNotBlank())
    }
}
