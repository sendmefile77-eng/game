package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronicleStoryComposerTest {
    private val textGenerator = ChronicleTextGenerator()

    @Test
    fun storyConnectsRealEventsAndHidesRawSocialCodes() {
        val events = listOf(
            SimulationEvent(
                id = "founding",
                tick = 0L,
                code = "SETTLEMENT_FOUNDED",
                actorIds = listOf("civ-1"),
                facts = mapOf("civilization" to "Нері", "settlement" to "Нерап"),
            ),
            SimulationEvent(
                id = "dynasty",
                tick = 60L,
                code = "DYNASTY_FOUNDED",
                actorIds = listOf("civ-1", "person-a"),
                facts = mapOf("civilization" to "Нері", "person" to "Торуен"),
            ),
            SimulationEvent(
                id = "social",
                tick = 72L,
                code = "ADULT_SOCIAL_EVENT",
                actorIds = listOf("civ-1", "person-a", "person-b"),
                facts = mapOf(
                    "civilization" to "Нері",
                    "eventCode" to "bondage_rite",
                    "participants" to "Торуен, Варій",
                ),
            ),
        )

        val story = requireNotNull(
            ChronicleStoryComposer.compose(
                events = events,
                civilizationNames = mapOf("civ-1" to "Нері"),
                textGenerator = textGenerator,
                era = TechnologyEra.METALLURGIC,
            ),
        )

        val text = buildString {
            append(story.title).append(' ')
            append(story.lead).append(' ')
            append(story.paragraphs.joinToString(" ")).append(' ')
            append(story.beats.joinToString(" ") { it.title + " " + it.summary })
        }
        assertTrue(text.contains("Нері"))
        assertTrue(text.contains("Торуен"))
        assertTrue(story.paragraphs.size >= 2)
        assertTrue(text.contains("горн") || text.contains("мід"))
        assertFalse(text.contains("зафіксовано"))
        assertFalse(text.contains("bondage_rite"))
        assertFalse(text.contains("bondage rite", ignoreCase = true))
    }

    @Test
    fun metallurgicStoryDescribesForgeStreetNotAbstractNorms() {
        val events = listOf(
            SimulationEvent(
                id = "founding",
                tick = 0L,
                code = "SETTLEMENT_FOUNDED",
                actorIds = listOf("civ-1"),
                facts = mapOf("civilization" to "Астарі", "settlement" to "Korareach"),
            ),
            SimulationEvent(
                id = "social",
                tick = 40L,
                code = "ADULT_SOCIAL_EVENT",
                actorIds = listOf("civ-1"),
                facts = mapOf(
                    "civilization" to "Астарі",
                    "settlement" to "Korareach",
                    "eventCode" to "cum_rite",
                    "participants" to "Торена, Солорна",
                ),
            ),
        )
        val economy = EconomyState(
            worldSeed = 1L,
            tick = 40L,
            civilizations = listOf(
                CivilizationEconomy(
                    civilizationId = "civ-1",
                    era = TechnologyEra.METALLURGIC,
                    stockpiles = EconomicGood.entries.associateWith { 1.0 },
                    production = EconomicGood.entries.associateWith { 1.0 },
                    demand = EconomicGood.entries.associateWith { 1.0 },
                    shortageIndex = 0.0,
                    tradeBalance = 0.0,
                    grossOutput = 4.0,
                ),
            ),
        )
        val story = requireNotNull(
            ChronicleStoryComposer.compose(
                events = events,
                civilizationNames = mapOf("civ-1" to "Астарі"),
                textGenerator = textGenerator,
                era = economy.civilizations.first().era,
            ),
        )
        val text = (story.lead + " " + story.paragraphs.joinToString(" ")).lowercase()
        assertTrue(text.contains("астарі"))
        assertTrue(text.contains("горн") || text.contains("кузн") || text.contains("саж"))
        assertTrue(text.contains("торена"))
        assertFalse(text.contains("моделі близькості"))
        assertFalse(text.contains("формальна зміна імені"))
    }

    @Test
    fun repeatedSocialEventsFromOneBurstAreCollapsed() {
        val events = listOf(
            socialEvent("social-1", 120L, "union"),
            socialEvent("social-2", 120L, "orgy"),
            socialEvent("social-3", 126L, "bondage_rite"),
        )
        val story = requireNotNull(
            ChronicleStoryComposer.compose(
                events = events,
                civilizationNames = mapOf("civ-1" to "Нері"),
                textGenerator = textGenerator,
                era = TechnologyEra.TRIBAL,
            ),
        )

        assertTrue("burst should be summarized rather than spammed", story.beats.size <= 2)
    }

    private fun socialEvent(id: String, tick: Long, code: String): SimulationEvent = SimulationEvent(
        id = id,
        tick = tick,
        code = "ADULT_SOCIAL_EVENT",
        actorIds = listOf("civ-1"),
        facts = mapOf("civilization" to "Нері", "eventCode" to code, "participants" to "А, Б"),
    )
}
