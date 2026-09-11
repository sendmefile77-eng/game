package com.sendmefile77.chronosphere.economy

enum class EconomicGood {
    FOOD,
    TIMBER,
    STONE,
    METAL,
    FUEL,
    CRAFTS,
}

enum class TechnologyEra(val displayNameUk: String) {
    TRIBAL("Племінна"),
    AGRARIAN("Аграрна"),
    URBAN("Міська"),
    METALLURGIC("Металургійна"),
    MEDIEVAL("Станова"),
    EARLY_INDUSTRIAL("Ранньоіндустріальна"),
    INDUSTRIAL("Індустріальна"),
    ELECTRIC("Електрична"),
    INFORMATION("Інформаційна"),
    SPACEFARING("Космічна"),
}

data class CivilizationEconomy(
    val civilizationId: String,
    val era: TechnologyEra,
    val stockpiles: Map<EconomicGood, Double>,
    val production: Map<EconomicGood, Double>,
    val demand: Map<EconomicGood, Double>,
    val shortageIndex: Double,
    val tradeBalance: Double,
    val grossOutput: Double,
)

data class TradeRoute(
    val id: String,
    val exporterId: String,
    val importerId: String,
    val good: EconomicGood,
    val volume: Double,
    val value: Double,
    val tick: Long,
)

data class EconomyState(
    val worldSeed: Long,
    val tick: Long,
    val civilizations: List<CivilizationEconomy>,
    val routes: List<TradeRoute> = emptyList(),
) {
    fun economy(civilizationId: String): CivilizationEconomy? = civilizations.firstOrNull { it.civilizationId == civilizationId }
}
