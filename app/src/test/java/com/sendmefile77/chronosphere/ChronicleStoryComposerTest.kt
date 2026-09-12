package com.sendmefile77.chronosphere

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
        assertFalse(text.contains("bondage_rite"))
        assertFalse(text.contains("bondage rite", ignoreCase = true))
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
