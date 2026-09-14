package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.SimulationEvent

data class Settlement(
    val id: String,
    val name: String,
    val civilizationId: String,
    val x: Int,
    val y: Int,
    val population: Long,
    val foodStock: Double,
    val wealth: Double,
    val foundedTick: Long,
)

data class LivingPlanetState(
    val worldSeed: Long,
    val tick: Long,
    val civilizations: List<Civilization>,
    val settlements: List<Settlement>,
    val recentEvents: List<SimulationEvent> = emptyList(),
    val relations: List<DiplomaticRelation> = emptyList(),
    val wars: List<WarState> = emptyList(),
    val alliances: List<AllianceState> = emptyList(),
    val taxPolicies: List<CivilizationTaxPolicy> = emptyList(),
    val eliteFactions: List<EliteFactionState> = emptyList(),
    val provinces: List<ProvinceState> = emptyList(),
    val rebellions: List<RebellionState> = emptyList(),
    val institutions: List<InstitutionState> = emptyList(),
) {
    val totalPopulation: Long get() = settlements.sumOf { it.population }
}
