package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.DiplomaticRelation
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EraChoiceRecurringConsequencesTest {
    @Test
    fun predatorHunterLifestyleKeepsChangingFoodStabilityAndRelationsAcrossACentury() {
        val initial = state(
            tags = setOf(
                "era-choice:subsistence:predator_hunters",
                "policy:predator_hunters",
                "hist:blood_hunt",
            ),
            relation = 0.20,
        )
        val before = initial.civilizations.first { it.id == "civ-a" }
        val beforeFood = initial.settlements.first { it.civilizationId == "civ-a" }.foodStock

        val after = HistoricalCommitmentEngine.applyRecurring(initial, months = 1_200)
        val civilization = after.civilizations.first { it.id == "civ-a" }
        val food = after.settlements.first { it.civilizationId == "civ-a" }.foodStock

        assertTrue(food > beforeFood)
        assertTrue(civilization.stability < before.stability)
        assertTrue(after.relations.single().value < initial.relations.single().value)
        assertTrue("era-choice:subsistence:predator_hunters" in civilization.cultureTags)
        assertTrue("hist:blood_hunt" in civilization.cultureTags)
    }

    @Test
    fun accumulatedFireAndStoneDiscoveriesHaveACombinedDurableMaterialEffect() {
        val initial = state(
            tags = setOf(
                "era-choice:breakthrough:fire",
                "era-choice:breakthrough:stone_tools",
                "foundation:fire_mastery",
                "foundation:stone_tools",
            ),
        )
        val beforeTechnology = initial.civilizations.first { it.id == "civ-a" }.technology
        val beforeFood = initial.settlements.first { it.civilizationId == "civ-a" }.foodStock

        val after = HistoricalCommitmentEngine.applyRecurring(initial, months = 1_200)
        val civilization = after.civilizations.first { it.id == "civ-a" }
        val food = after.settlements.first { it.civilizationId == "civ-a" }.foodStock

        assertTrue(civilization.technology > beforeTechnology)
        assertTrue(food > beforeFood)
        assertTrue("era-choice:breakthrough:fire" in civilization.cultureTags)
        assertTrue("era-choice:breakthrough:stone_tools" in civilization.cultureTags)
    }

    @Test
    fun civilizationWithoutEraChoiceTagsDoesNotReceiveEraChoiceBonuses() {
        val initial = state(tags = emptySet())
        val after = HistoricalCommitmentEngine.applyRecurring(initial, months = 1_200)

        assertEquals(initial, after)
    }

    private fun state(tags: Set<String>, relation: Double = 0.0): LivingPlanetState = LivingPlanetState(
        worldSeed = 77L,
        tick = 120L,
        civilizations = listOf(
            Civilization("civ-a", "Астарі", 1_000L, 0.60, 0.10, 80.0, cultureTags = tags),
            Civilization("civ-b", "Варки", 900L, 0.58, 0.10, 70.0),
        ),
        settlements = listOf(
            Settlement("city-a", "Аста", "civ-a", 2, 3, 1_000L, 500.0, 20.0, 0L),
            Settlement("city-b", "Вара", "civ-b", 7, 8, 900L, 450.0, 15.0, 0L),
        ),
        relations = listOf(DiplomaticRelation("civ-a", "civ-b", relation, 120L)),
    )
}
