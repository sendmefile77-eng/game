package com.sendmefile77.chronosphere.llm

import com.sendmefile77.chronosphere.ChronicleDecision
import com.sendmefile77.chronosphere.ChronicleDecisionOption
import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.textgen.ChronicleNarrative
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronicleLlmEnricherTest {
    @Test
    fun promptKeepsAuthoritativeChoiceIdsAndHidesMediaInternals() {
        val event = SimulationEvent(
            id = "war-1",
            tick = 120L,
            code = "WAR_STARTED",
            actorIds = listOf("civ-a", "civ-b"),
            facts = mapOf(
                "a" to "Нері",
                "b" to "Варки",
                "cause" to "кордон",
                "mediaKey" to "private-cache-key",
                "mediaTags" to "internal:tag",
            ),
        )
        val people = PeopleState(
            worldSeed = 1L,
            tick = 120L,
            persons = emptyList(),
            dynasties = emptyList(),
            relationships = emptyList(),
            rulerByCivilization = emptyMap(),
            socialProfiles = emptyList(),
        )
        val economy = EconomyState(
            worldSeed = 1L,
            tick = 120L,
            civilizations = listOf(
                CivilizationEconomy("civ-a", TechnologyEra.TRIBAL, emptyMap(), emptyMap(), emptyMap(), 0.2, 0.0, 10.0),
                CivilizationEconomy("civ-b", TechnologyEra.TRIBAL, emptyMap(), emptyMap(), emptyMap(), 0.1, 0.0, 9.0),
            ),
        )
        val narrative = ChronicleNarrative(
            title = "Почалася війна",
            hook = "Кордон спалахнув.",
            body = "Нері та Варки вступили у війну.",
            significance = "Баланс сил зміниться.",
        )
        val decision = ChronicleDecision(
            eventId = event.id,
            titleUk = "Як відповісти?",
            promptUk = "Оберіть напрямок.",
            options = listOf(
                ChronicleDecisionOption(
                    id = "war-homefront",
                    sourceEventId = event.id,
                    titleUk = "Зміцнити тил",
                    effectUk = "Стабільність зросте.",
                    riskUk = "Фронт не отримає прямої допомоги.",
                    kind = InterventionKind.STABILITY_SUPPORT,
                    targetCivilizationId = "civ-a",
                    strength = 0.66,
                ),
            ),
        )

        val prompt = ChronicleLlmEnricher.buildPrompt(
            event = event,
            recentEvents = listOf(event),
            people = people,
            economy = economy,
            baseNarrative = narrative,
            baseDecision = decision,
        )

        assertTrue(prompt.contains("war-homefront"))
        assertTrue(prompt.contains("WAR_STARTED"))
        assertTrue(prompt.contains("Нері"))
        assertFalse(prompt.contains("private-cache-key"))
        assertFalse(prompt.contains("internal:tag"))
    }
}
