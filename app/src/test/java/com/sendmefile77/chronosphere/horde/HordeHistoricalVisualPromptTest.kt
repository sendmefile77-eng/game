package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra
import org.junit.Assert.assertFalse
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
            TechnologyEra.TRIBAL,
        )
        assertTrue(prompt.contains("hunting"))
        assertTrue(prompt.contains("butchered game"))
    }

    @Test
    fun stoneToolsChoiceProducesCarriedTools() {
        val prompt = HordeHistoricalVisualPrompt.eraChoiceFragment("era-tribal-breakthrough-stone_tools")
        assertTrue(prompt.contains("flint knives"))
        assertTrue(prompt.contains("stone axes"))
    }

    @Test
    fun quietCenturyDilemmaChoiceBecomesAConcreteChronicleScene() {
        val prompt = HordeHistoricalVisualPrompt.eraChoiceFragment("dilemma-tribal-lean-winter-hunt")
        assertTrue(prompt.contains("winter hunting party"))
        assertTrue(prompt.contains("spears"))
    }

    @Test
    fun ancientBreakthroughsDoNotDominateIndustrialImages() {
        val prompt = HordeHistoricalVisualPrompt.fragment(
            setOf(
                "era-choice:breakthrough:fire",
                "era-choice:breakthrough:stone_tools",
                "era-choice:breakthrough:steam_power",
                "era-choice:breakthrough:steel",
                "era-choice:society:factory_discipline",
                "foundation:fire_mastery",
                "hist:hearth_culture",
            ),
            TechnologyEra.INDUSTRIAL,
        )
        assertTrue(prompt.contains("steel"))
        assertTrue(prompt.contains("factory"))
        assertFalse(prompt.contains("flint knives"))
        assertFalse(prompt.contains("charred cooking stones"))
    }

    @Test
    fun informationEraPoliciesChangeTheVisibleEverydayWorld() {
        val prompt = HordeHistoricalVisualPrompt.fragment(
            setOf(
                "era-choice:society:algorithmic_governance",
                "era-choice:mobility:remote_life",
                "policy:open_networks",
            ),
            TechnologyEra.INFORMATION,
        )
        assertTrue(prompt.contains("civic control rooms"))
        assertTrue(prompt.contains("telepresence"))
        assertTrue(prompt.contains("connected devices"))
    }
}
