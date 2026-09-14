package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.history.InterventionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CenturyDilemmaCatalogTest {
    @Test
    fun everyTechnologyEraHasReadableChapterAndPlayableDilemma() {
        TechnologyEra.entries.forEach { era ->
            val chapter = EraExperience.chapter(era)
            assertTrue(chapter.title.length >= 8)
            assertTrue(chapter.opening.length >= 80)
            assertTrue(chapter.everyday.length >= 80)
            assertTrue(chapter.power.length >= 70)
            assertTrue(chapter.danger.length >= 60)
            assertTrue(chapter.horizon.length >= 60)

            val eraDecision = ChronicleDecision(
                eventId = "player-century-choice-1200-civ-a",
                titleUk = era.displayNameUk,
                promptUk = "Оберіть напрями",
                options = listOf(
                    ChronicleDecisionOption(
                        id = "era-${era.name.lowercase()}-breakthrough-test",
                        sourceEventId = "player-century-choice-1200-civ-a-breakthrough",
                        titleUk = "Тестовий напрям",
                        effectUk = "ефект",
                        riskUk = "ризик",
                        kind = InterventionKind.TECHNOLOGY_BOOST,
                        targetCivilizationId = "civ-a",
                        strength = 0.5,
                    ),
                ),
            )

            val dilemma = CenturyDilemmaCatalog.fromEraDecision(eraDecision)
            assertNotNull(dilemma)
            assertEquals(3, dilemma!!.options.size)
            assertEquals(1, dilemma.options.map { it.sourceEventId }.distinct().size)
            assertTrue(dilemma.options.all { it.effectUk.isNotBlank() && it.riskUk.isNotBlank() })
        }
    }
}
