package com.sendmefile77.chronosphere.civilization

enum class TaxPolicyKind(
    val rate: Double,
    val titleUk: String,
) {
    RELIEF(0.08, "Податкове полегшення"),
    BALANCED(0.16, "Збалансовані податки"),
    HIGH(0.24, "Високі податки"),
    EXTRACTION(0.32, "Надзвичайні збори"),
}

data class CivilizationTaxPolicy(
    val civilizationId: String,
    val kind: TaxPolicyKind = TaxPolicyKind.BALANCED,
    val changedTick: Long = 0L,
) {
    init {
        require(civilizationId.isNotBlank())
        require(changedTick >= 0L)
    }

    val rate: Double get() = kind.rate
}

enum class EliteFactionKind(val titleUk: String) {
    LANDHOLDERS("Землевласники"),
    MERCHANTS("Торгові доми"),
    MILITARY("Військова верхівка"),
    BUREAUCRACY("Управлінський апарат"),
}

data class EliteFactionState(
    val id: String,
    val civilizationId: String,
    val kind: EliteFactionKind,
    val influence: Double,
    val loyalty: Double,
    val lastUpdatedTick: Long,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(influence.isFinite() && influence in 0.0..1.0)
        require(loyalty.isFinite() && loyalty in 0.0..1.0)
        require(lastUpdatedTick >= 0L)
    }
}

data class ProvinceState(
    val id: String,
    val civilizationId: String,
    val settlementId: String,
    val loyalty: Double,
    val unrest: Double,
    val autonomy: Double,
    val taxBurden: Double,
    val lastUpdatedTick: Long,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(settlementId.isNotBlank())
        require(loyalty.isFinite() && loyalty in 0.0..1.0)
        require(unrest.isFinite() && unrest in 0.0..1.0)
        require(autonomy.isFinite() && autonomy in 0.0..1.0)
        require(taxBurden.isFinite() && taxBurden in 0.0..1.0)
        require(lastUpdatedTick >= 0L)
    }
}

enum class RebellionStatus { ACTIVE, SUPPRESSED, SUCCEEDED }

data class RebellionState(
    val id: String,
    val civilizationId: String,
    val provinceId: String,
    val startedTick: Long,
    val lastUpdatedTick: Long,
    val severity: Double,
    val status: RebellionStatus = RebellionStatus.ACTIVE,
    val endedTick: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(provinceId.isNotBlank())
        require(startedTick >= 0L)
        require(lastUpdatedTick >= startedTick)
        require(severity.isFinite() && severity in 0.0..1.0)
        require(endedTick == null || endedTick >= startedTick)
    }

    val isActive: Boolean get() = status == RebellionStatus.ACTIVE
}

fun LivingPlanetState.taxPolicyFor(civilizationId: String): CivilizationTaxPolicy? =
    taxPolicies.firstOrNull { it.civilizationId == civilizationId }

fun LivingPlanetState.provincesFor(civilizationId: String): List<ProvinceState> =
    provinces.filter { it.civilizationId == civilizationId }

fun LivingPlanetState.elitesFor(civilizationId: String): List<EliteFactionState> =
    eliteFactions.filter { it.civilizationId == civilizationId }

fun LivingPlanetState.activeRebellionsFor(civilizationId: String): List<RebellionState> =
    rebellions.filter { it.civilizationId == civilizationId && it.isActive }

fun LivingPlanetState.internalPressure(civilizationId: String): Double {
    val provincePressure = provincesFor(civilizationId).maxOfOrNull { it.unrest } ?: 0.0
    val elitePressure = elitesFor(civilizationId)
        .maxOfOrNull { faction -> (1.0 - faction.loyalty) * faction.influence }
        ?: 0.0
    val rebellionPressure = activeRebellionsFor(civilizationId).maxOfOrNull { it.severity } ?: 0.0
    return maxOf(provincePressure, elitePressure, rebellionPressure).coerceIn(0.0, 1.0)
}
