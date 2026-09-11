package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.LivingPlanetState

data class HistoryComparison(
    val populationDelta: Long,
    val settlementDelta: Int,
    val warDelta: Int,
    val allianceDelta: Int,
    val averageTechnologyDelta: Double,
)

object HistoryComparator {
    fun compare(reference: LivingPlanetState, current: LivingPlanetState): HistoryComparison = HistoryComparison(
        populationDelta = current.totalPopulation - reference.totalPopulation,
        settlementDelta = current.settlements.size - reference.settlements.size,
        warDelta = current.wars.size - reference.wars.size,
        allianceDelta = current.alliances.size - reference.alliances.size,
        averageTechnologyDelta = averageTechnology(current) - averageTechnology(reference),
    )

    private fun averageTechnology(state: LivingPlanetState): Double =
        if (state.civilizations.isEmpty()) 0.0 else state.civilizations.sumOf { it.technology } / state.civilizations.size
}
