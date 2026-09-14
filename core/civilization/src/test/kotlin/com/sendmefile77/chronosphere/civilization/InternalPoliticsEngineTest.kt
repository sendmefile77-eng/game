package com.sendmefile77.chronosphere.civilization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalPoliticsEngineTest {
    @Test
    fun reconcileCreatesPolicyElitesAndProvinceForEveryState() {
        val reconciled = InternalPoliticsEngine.reconcile(baseState())

        assertEquals(1, reconciled.taxPolicies.size)
        assertEquals(4, reconciled.eliteFactions.size)
        assertEquals(2, reconciled.provinces.size)
        assertEquals(TaxPolicyKind.BALANCED, reconciled.taxPolicyFor("civ-1")?.kind)
        assertTrue(reconciled.provinces.all { it.civilizationId == "civ-1" })
    }

    @Test
    fun coarseStepMatchesExplicitMonthlyPoliticsReplay() {
        val prepared = InternalPoliticsEngine.reconcile(baseState())
        val finalSnapshot = prepared.copy(tick = 12L)
        val batched = InternalPoliticsBatchEngine.advance(finalSnapshot, fromTick = 0L)

        var monthly = prepared
        for (tick in 1L..12L) {
            monthly = InternalPoliticsEngine.advance(monthly.copy(tick = tick))
            monthly = InternalSecessionEngine.advance(monthly)
        }

        assertEquals(monthly, batched)
    }

    @Test
    fun matureSevereRebellionCanBecomeActualSuccessorState() {
        val base = InternalPoliticsEngine.reconcile(baseState(tick = 36L))
        val frontier = base.provinces.first { it.settlementId == "frontier" }
        val primed = base.copy(
            provinces = base.provinces.map { province ->
                if (province.id == frontier.id) province.copy(loyalty = 0.08, unrest = 0.96, autonomy = 0.70)
                else province
            },
            rebellions = listOf(
                RebellionState(
                    id = "rebellion-frontier-12",
                    civilizationId = "civ-1",
                    provinceId = frontier.id,
                    startedTick = 12L,
                    lastUpdatedTick = 36L,
                    severity = 0.96,
                ),
            ),
        )

        val split = InternalSecessionEngine.advance(primed)

        assertEquals(2, split.civilizations.size)
        val successor = split.civilizations.first { it.id != "civ-1" }
        assertEquals(successor.id, split.settlements.first { it.id == "frontier" }.civilizationId)
        assertTrue(split.recentEvents.any { it.code == "SECESSION" && successor.id in it.actorIds })
        assertEquals(RebellionStatus.SUCCEEDED, split.rebellions.single().status)
        assertEquals(successor.id, split.provinces.first { it.settlementId == "frontier" }.civilizationId)
    }

    private fun baseState(tick: Long = 0L): LivingPlanetState = LivingPlanetState(
        worldSeed = 77L,
        tick = tick,
        civilizations = listOf(
            Civilization(
                id = "civ-1",
                name = "Ардан",
                population = 1_800L,
                stability = 0.58,
                technology = 0.18,
                treasury = 55.0,
            ),
        ),
        settlements = listOf(
            Settlement(
                id = "capital",
                name = "Астра",
                civilizationId = "civ-1",
                x = 2,
                y = 2,
                population = 1_000L,
                foodStock = 700.0,
                wealth = 160.0,
                foundedTick = 0L,
            ),
            Settlement(
                id = "frontier",
                name = "Брен",
                civilizationId = "civ-1",
                x = 20,
                y = 18,
                population = 800L,
                foodStock = 420.0,
                wealth = 120.0,
                foundedTick = 1L,
            ),
        ),
    )
}
