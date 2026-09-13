package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EraTurnChoiceCatalogTest {
    @Test
    fun tribalTurnOffersConcreteEraChoicesInsteadOfGenericPolicyButtons() {
        val decision = EraTurnChoiceCatalog.decision(
            state = state(tick = 24L),
            economy = economy(TechnologyEra.TRIBAL, tick = 24L),
            civilizationId = "civ-a",
        )

        assertTrue(EraTurnChoiceCatalog.isEraTurn(decision))
        assertTrue(decision.options.size in 4..5)
        assertTrue(decision.options.all { it.id.startsWith("era-tribal-") })
        assertTrue(decision.options.any { EraTurnChoiceCatalog.family(it) == "breakthrough" })
        assertTrue(decision.options.any { EraTurnChoiceCatalog.family(it) == "subsistence" })
        assertTrue(decision.options.any { EraTurnChoiceCatalog.family(it) == "society" })
        assertTrue(decision.options.any { EraTurnChoiceCatalog.family(it) == "mobility" })
        assertTrue(decision.options.none { it.titleUk in setOf("Накопичити резерви", "Утримати порядок", "Ставка на ремесла") })
    }

    @Test
    fun sameWorldProducesDifferentCenturyMenuAsTimeMoves() {
        val first = EraTurnChoiceCatalog.decision(
            state = state(tick = 24L),
            economy = economy(TechnologyEra.TRIBAL, tick = 24L),
            civilizationId = "civ-a",
        ).options.map { it.id }
        val later = EraTurnChoiceCatalog.decision(
            state = state(tick = 1_224L),
            economy = economy(TechnologyEra.TRIBAL, tick = 1_224L),
            civilizationId = "civ-a",
        ).options.map { it.id }

        assertNotEquals(first, later)
    }

    @Test
    fun selectedLifestyleBecomesCivilizationLegacyForTextAndImages() {
        val decision = EraTurnChoiceCatalog.decision(
            state = state(tick = 24L),
            economy = economy(TechnologyEra.TRIBAL, tick = 24L),
            civilizationId = "civ-a",
        )
        val lifestyle = decision.options.first { EraTurnChoiceCatalog.family(it) == "subsistence" }
        val changed = EraTurnChoiceCatalog.applyLegacy(state(24L), "civ-a", lifestyle.id)
        val tags = changed.civilizations.first { it.id == "civ-a" }.cultureTags

        assertTrue(tags.any { it.startsWith("era-choice:subsistence:") })
        assertTrue(tags.any { it.startsWith("policy:") || it.startsWith("foundation:") })
        assertEquals(emptySet<String>(), changed.civilizations.first { it.id == "civ-b" }.cultureTags)
    }

    @Test
    fun changingLifestyleSupersedesThePreviousLifestyle() {
        var current = state(tick = 24L)
        val first = EraTurnChoiceCatalog.decision(current, economy(TechnologyEra.TRIBAL, 24L), "civ-a")
            .options.first { EraTurnChoiceCatalog.family(it) == "subsistence" }
        current = EraTurnChoiceCatalog.applyLegacy(current, "civ-a", first.id)

        val alternate = listOf(
            "era-tribal-subsistence-predator_hunters",
            "era-tribal-subsistence-plant_foragers",
            "era-tribal-subsistence-river_fishers",
        ).first { it != first.id }
        current = EraTurnChoiceCatalog.applyLegacy(current, "civ-a", alternate)
        val activeLifestyleTags = current.civilizations.first { it.id == "civ-a" }.cultureTags
            .filter { it.startsWith("era-choice:subsistence:") }

        assertEquals(1, activeLifestyleTags.size)
        assertTrue(activeLifestyleTags.single().endsWith(alternate.substringAfterLast('-')))
    }

    private fun state(tick: Long): LivingPlanetState = LivingPlanetState(
        worldSeed = 77L,
        tick = tick,
        civilizations = listOf(
            Civilization("civ-a", "Астарі", 1_200L, 0.60, 0.05, 80.0),
            Civilization("civ-b", "Варки", 900L, 0.58, 0.05, 60.0),
        ),
        settlements = listOf(
            Settlement("city-a", "Аста", "civ-a", 2, 3, 1_200L, 700.0, 20.0, 0L),
            Settlement("city-b", "Вара", "civ-b", 7, 8, 900L, 500.0, 15.0, 0L),
        ),
    )

    private fun economy(era: TechnologyEra, tick: Long): EconomyState = EconomyState(
        worldSeed = 77L,
        tick = tick,
        civilizations = listOf(
            CivilizationEconomy("civ-a", era, emptyMap(), emptyMap(), emptyMap(), 0.0, 0.0, 1.0),
            CivilizationEconomy("civ-b", era, emptyMap(), emptyMap(), emptyMap(), 0.0, 0.0, 1.0),
        ),
    )
}
