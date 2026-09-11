package com.sendmefile77.chronosphere.simulation

data class SimulationEvent(
    val id: String,
    val tick: Long,
    val code: String,
    val actorIds: List<String> = emptyList(),
    val locationId: String? = null,
    val numbers: Map<String, Double> = emptyMap(),
    val facts: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank())
        require(tick >= 0)
        require(code.isNotBlank())
    }
}
