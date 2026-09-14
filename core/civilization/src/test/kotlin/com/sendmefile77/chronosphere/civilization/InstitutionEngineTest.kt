package com.sendmefile77.chronosphere.civilization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstitutionEngineTest {
    @Test
    fun reconcileCreatesFourInstitutionsPerCivilization() {
        val reconciled = InstitutionEngine.reconcile(baseState())

        assertEquals(4, reconciled.institutionsFor("civ-1").size)
        assertEquals(InstitutionKind.entries.toSet(), reconciled.institutionsFor("civ-1").map { it.kind }.toSet())
        assertTrue(reconciled.institutions.all { it.capacity in 0.0..1.0 && it.legitimacy in 0.0..1.0 })
    }

    @Test
    fun reformImprovesOnlyWeakestInstitution() {
        val reconciled = InstitutionEngine.reconcile(baseState())
        val weakest = reconciled.institutionsFor("civ-1")
            .minBy { it.capacity * 0.65 + it.legitimacy * 0.35 }

        val reformed = InstitutionEngine.reform(reconciled, "civ-1", 0.65)
        val improved = reformed.institutionFor("civ-1", weakest.kind)!!

        assertTrue(improved.capacity > weakest.capacity)
        assertTrue(improved.legitimacy > weakest.legitimacy)
        reconciled.institutionsFor("civ-1").filter { it.id != weakest.id }.forEach { before ->
            assertEquals(before, reformed.institutions.first { it.id == before.id })
        }
    }

    @Test
    fun annualAdvanceIsDeterministic() {
        val prepared = InstitutionEngine.reconcile(baseState(tick = 12L))

        assertEquals(
            InstitutionEngine.advanceAnnual(prepared),
            InstitutionEngine.advanceAnnual(prepared),
        )
    }

    private fun baseState(tick: Long = 0L): LivingPlanetState = LivingPlanetState(
        worldSeed = 704L,
        tick = tick,
        civilizations = listOf(
            Civilization("civ-1", "Ардан", 2_000L, 0.62, 0.24, 90.0),
        ),
        settlements = listOf(
            Settlement("capital", "Астра", "civ-1", 2, 2, 1_200L, 720.0, 180.0, 0L),
            Settlement("frontier", "Брен", "civ-1", 14, 11, 800L, 410.0, 120.0, 12L),
        ),
    )
}
