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

    @Test
    fun quietCenturyGetsMandatoryEraDilemma() {
        val combined = TurnChoiceComposer.compose(eraDecision(), null)
        val historical = combined.options.filterNot(TurnChoiceComposer::isEraOption)

        assertEquals(3, historical.size)
        assertEquals(1, TurnChoiceComposer.requiredHistoricalSources(combined).size)
        assertTrue(combined.promptUk.contains("ЦЬОГО СТОЛІТТЯ"))
        assertTrue(combined.promptUk.contains("Світ біля вогню"))
    }

    @Test
    fun playerCanSelectOneToThreeEraDirectionsButNeverFour() {
        val decision = eraDecision()
        var selected = emptySet<String>()
        selected = TurnChoiceComposer.toggleSelection(decision, selected, "era-tribal-breakthrough-fire")
        selected = TurnChoiceComposer.toggleSelection(decision, selected, "era-tribal-subsistence-predator_hunters")
        selected = TurnChoiceComposer.toggleSelection(decision, selected, "era-tribal-society-ritual_culture")

        assertEquals(3, selected.size)
        assertTrue("era-tribal-breakthrough-fire" in selected)
        assertTrue("era-tribal-subsistence-predator_hunters" in selected)
        assertTrue("era-tribal-society-ritual_culture" in selected)

        val afterFourth = TurnChoiceComposer.toggleSelection(
            decision,
            selected,
            "era-tribal-mobility-nomadic_migration",
        )
        assertEquals(selected, afterFourth)
    }

    @Test
    fun selectingAnotherOptionInSameFamilyReplacesThePreviousOne() {
        val decision = eraDecision().copy(
            options = eraDecision().options +
                option("era-tribal-breakthrough-stone_tools", "player-century-choice-1200-civ-a-breakthrough"),
        )
        var selected = TurnChoiceComposer.toggleSelection(
            decision,
            emptySet(),
            "era-tribal-breakthrough-fire",
        )
        selected = TurnChoiceComposer.toggleSelection(
            decision,
            selected,
            "era-tribal-breakthrough-stone_tools",
        )

        assertEquals(setOf("era-tribal-breakthrough-stone_tools"), selected)
    }

    @Test
    fun historicalForkStillRequiresExactlyOneChoiceFromItsSource() {
        val combined = TurnChoiceComposer.compose(
            eraDecision(),
            ChronicleDecision(
                eventId = "event-ruler",
                titleUk = "Нова влада",
                promptUk = "Оберіть реакцію",
                options = listOf(
                    option("rule-legitimacy", "event-ruler"),
                    option("rule-reform", "event-ruler"),
                ),
            ),
        )
        var selected = TurnChoiceComposer.toggleSelection(combined, emptySet(), "rule-legitimacy")
        selected = TurnChoiceComposer.toggleSelection(combined, selected, "rule-reform")

        assertEquals(setOf("rule-reform"), selected)
        assertEquals(
            setOf("event-ruler"),
            selected.mapNotNull { id -> combined.options.firstOrNull { it.id == id }?.sourceEventId }.toSet(),
        )
    }

    @Test
    fun focusedEraCourseHitsHarderThanBroadCourse() {
        val one = EraStrategyBalance.apply(
            listOf(option("era-tribal-breakthrough-fire", "player-century-choice-1200-civ-a-breakthrough")),
        )
        val three = EraStrategyBalance.apply(
            listOf(
                option("era-tribal-breakthrough-fire", "player-century-choice-1200-civ-a-breakthrough"),
                option("era-tribal-subsistence-predator_hunters", "player-century-choice-1200-civ-a-subsistence"),
                option("era-tribal-society-ritual_culture", "player-century-choice-1200-civ-a-society"),
            ),
        )

        assertEquals(0.60, one.single().strength, 0.0001)
        assertEquals(0.41, three.first().strength, 0.0001)
    }

    private fun eraDecision() = ChronicleDecision(
        eventId = "player-century-choice-1200-civ-a",
        titleUk = "Племінна доба",
        promptUk = "Оберіть напрями",
        options = listOf(
            option("era-tribal-breakthrough-fire", "player-century-choice-1200-civ-a-breakthrough"),
            option("era-tribal-subsistence-predator_hunters", "player-century-choice-1200-civ-a-subsistence"),
            option("era-tribal-society-ritual_culture", "player-century-choice-1200-civ-a-society"),
            option("era-tribal-mobility-nomadic_migration", "player-century-choice-1200-civ-a-mobility"),
        ),
    )

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
