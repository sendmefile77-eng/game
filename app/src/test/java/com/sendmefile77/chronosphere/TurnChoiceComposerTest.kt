package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.history.InterventionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnChoiceComposerTest {
    @Test
    fun historicalForkAndEraDirectionsShareOneTurnScreen() {
        val era = ChronicleDecision(
            eventId = "player-century-choice-1200-civ-a",
            titleUk = "Племінна доба",
            promptUk = "Оберіть напрями",
            options = listOf(
                option("era-tribal-breakthrough-fire", "player-century-choice-1200-civ-a-breakthrough"),
                option("era-tribal-subsistence-predator_hunters", "player-century-choice-1200-civ-a-subsistence"),
            ),
        )
        val history = ChronicleDecision(
            eventId = "event-ruler",
            titleUk = "Нова влада",
            promptUk = "Оберіть реакцію",
            options = listOf(
                option("rule-legitimacy", "event-ruler"),
                option("rule-reform", "event-ruler"),
            ),
        )

        val combined = TurnChoiceComposer.compose(era, history)

        assertEquals(4, combined.options.size)
        assertEquals(setOf("event-ruler"), TurnChoiceComposer.requiredHistoricalSources(combined))
        assertTrue(combined.options.any { TurnChoiceComposer.isEraOption(it) })
        assertTrue(combined.promptUk.contains("одразу проживе"))
    }

    private fun option(id: String, source: String) = ChronicleDecisionOption(
        id = id,
        sourceEventId = source,
        titleUk = id,
        effectUk = "ефект",
        riskUk = "ризик",
        kind = InterventionKind.STABILITY_SUPPORT,
        targetCivilizationId = "civ-a",
        strength = 0.5,
    )
}
