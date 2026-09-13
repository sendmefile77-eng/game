package com.sendmefile77.chronosphere.horde

import org.junit.Assert.assertTrue
import org.junit.Test

class HordeHistoricalVisualPromptTest {
    @Test
    fun predatorHunterChoiceIsVisiblyMaterial() {
        val prompt = HordeHistoricalVisualPrompt.fragment(
            setOf(
                "era-choice:subsistence:predator_hunters",
                "policy:predator_hunters",
            ),
        )
        assertTrue(prompt.contains("blood-stained"))
        assertTrue(prompt.contains("hunter"))
    }

    @Test
    fun stoneToolsChoiceProducesCarriedTools() {
        val prompt = HordeHistoricalVisualPrompt.eraChoiceFragment("era-tribal-breakthrough-stone_tools")
        assertTrue(prompt.contains("flint knives"))
        assertTrue(prompt.contains("stone axes"))
    }
}
