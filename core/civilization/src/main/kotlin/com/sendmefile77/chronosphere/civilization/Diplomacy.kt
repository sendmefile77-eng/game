package com.sendmefile77.chronosphere.civilization

data class DiplomaticRelation(
    val civilizationA: String,
    val civilizationB: String,
    val value: Double,
    val lastUpdatedTick: Long,
) {
    init {
        require(civilizationA.isNotBlank() && civilizationB.isNotBlank())
        require(civilizationA != civilizationB)
        require(value.isFinite() && value in -1.0..1.0)
    }

    fun involves(civilizationId: String): Boolean = civilizationA == civilizationId || civilizationB == civilizationId
    fun matches(a: String, b: String): Boolean =
        (civilizationA == a && civilizationB == b) || (civilizationA == b && civilizationB == a)
}

data class WarState(
    val id: String,
    val civilizationA: String,
    val civilizationB: String,
    val startedTick: Long,
    val casualtiesA: Long = 0,
    val casualtiesB: Long = 0,
) {
    fun matches(a: String, b: String): Boolean =
        (civilizationA == a && civilizationB == b) || (civilizationA == b && civilizationB == a)
}
