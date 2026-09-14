package com.sendmefile77.chronosphere.history

enum class FoundationKind {
    SETTLEMENT,
    PRODUCTION,
    EXCHANGE,
    AUTHORITY,
}

data class CivilizationFoundation(
    val id: String,
    val civilizationId: String,
    val kind: FoundationKind,
    val titleUk: String,
    val benefitUk: String,
    val costUk: String,
    val strength: Double,
    val originTick: Long,
    val lastChangedTick: Long,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(titleUk.isNotBlank())
        require(strength.isFinite() && strength in 0.0..1.0)
        require(originTick >= 0L && lastChangedTick >= originTick)
    }
}

enum class HistoricalProcessKind {
    SETTLEMENT_EXPANSION,
    MIGRATION,
    SHORTAGE,
    WAR,
    DIPLOMATIC_ALIGNMENT,
    TECHNOLOGICAL_TRANSITION,
    DYNASTIC_TRANSITION,
    POPULATION_DIVERGENCE,
    INTERNAL_CRISIS,
    INSTITUTIONAL_TRANSITION,
}

enum class HistoricalProcessStage {
    EMERGING,
    ACTIVE,
    CONSOLIDATING,
    STRAINED,
    RESOLVED,
}

data class HistoricalProcess(
    val id: String,
    val kind: HistoricalProcessKind,
    val civilizationIds: Set<String>,
    val titleUk: String,
    val startedTick: Long,
    val lastUpdatedTick: Long,
    val stage: HistoricalProcessStage,
    val intensity: Double,
    val sourceEventIds: List<String>,
    val latestEventCode: String,
    val resolvedTick: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(civilizationIds.isNotEmpty())
        require(titleUk.isNotBlank())
        require(startedTick >= 0L && lastUpdatedTick >= startedTick)
        require(intensity.isFinite() && intensity in 0.0..1.0)
        require(sourceEventIds.isNotEmpty())
        require(latestEventCode.isNotBlank())
        require(resolvedTick == null || resolvedTick >= startedTick)
    }

    val isActive: Boolean get() = stage != HistoricalProcessStage.RESOLVED
}

enum class HistoricalConsequenceStatus { OPEN, RESOLVED }

data class HistoricalConsequence(
    val id: String,
    val civilizationIds: Set<String>,
    val originEventId: String,
    val originTick: Long,
    val titleUk: String,
    val triggerUk: String,
    val status: HistoricalConsequenceStatus = HistoricalConsequenceStatus.OPEN,
    val resolvedTick: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(civilizationIds.isNotEmpty())
        require(originEventId.isNotBlank())
        require(originTick >= 0L)
        require(titleUk.isNotBlank())
        require(triggerUk.isNotBlank())
        require(resolvedTick == null || resolvedTick >= originTick)
    }
}

enum class HistoricalCommitmentStatus { ACTIVE, SUPERSEDED }

data class HistoricalCommitment(
    val id: String,
    val civilizationId: String,
    val family: String,
    val choiceId: String,
    val titleUk: String,
    val benefitUk: String,
    val recurringCostUk: String,
    val originTick: Long,
    val status: HistoricalCommitmentStatus = HistoricalCommitmentStatus.ACTIVE,
    val endedTick: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(family.isNotBlank())
        require(choiceId.isNotBlank())
        require(titleUk.isNotBlank())
        require(originTick >= 0L)
        require(endedTick == null || endedTick >= originTick)
    }
}

enum class HistoricalCausalRelation {
    ESCALATION,
    RESOLUTION,
    PRESSURE,
    DISPLACEMENT,
    TRANSITION,
    REINFORCEMENT,
}

data class HistoricalCausalLink(
    val id: String,
    val civilizationIds: Set<String>,
    val causeEventId: String,
    val effectEventId: String,
    val causeTick: Long,
    val effectTick: Long,
    val relation: HistoricalCausalRelation,
    val titleUk: String,
) {
    init {
        require(id.isNotBlank())
        require(civilizationIds.isNotEmpty())
        require(causeEventId.isNotBlank() && effectEventId.isNotBlank())
        require(causeEventId != effectEventId)
        require(causeTick >= 0L && effectTick >= causeTick)
        require(titleUk.isNotBlank())
    }
}

enum class HistoricalLegacyKind {
    WAR_MEMORY,
    TERRITORIAL_MEMORY,
    SCARCITY_MEMORY,
    MIGRATION_MEMORY,
    DYNASTIC_MEMORY,
    POPULATION_MEMORY,
    TECHNOLOGICAL_MEMORY,
    REBELLION_MEMORY,
    INSTITUTIONAL_MEMORY,
}

data class HistoricalLegacy(
    val id: String,
    val civilizationIds: Set<String>,
    val kind: HistoricalLegacyKind,
    val titleUk: String,
    val originTick: Long,
    val lastReinforcedTick: Long,
    val strength: Double,
    val sourceEventIds: List<String>,
) {
    init {
        require(id.isNotBlank())
        require(civilizationIds.isNotEmpty())
        require(titleUk.isNotBlank())
        require(originTick >= 0L && lastReinforcedTick >= originTick)
        require(strength.isFinite() && strength in 0.0..1.0)
        require(sourceEventIds.isNotEmpty())
    }
}

data class HistoricalMemoryState(
    val worldSeed: Long,
    val tick: Long,
    val foundations: List<CivilizationFoundation> = emptyList(),
    val processes: List<HistoricalProcess> = emptyList(),
    val consequences: List<HistoricalConsequence> = emptyList(),
    val commitments: List<HistoricalCommitment> = emptyList(),
    val lastProcessedTick: Long = -1L,
    val processedEventIdsAtLastTick: Set<String> = emptySet(),
    val causalLinks: List<HistoricalCausalLink> = emptyList(),
    val legacies: List<HistoricalLegacy> = emptyList(),
) {
    init {
        require(tick >= 0L)
        require(lastProcessedTick <= tick)
    }

    fun foundationsFor(civilizationId: String): List<CivilizationFoundation> =
        foundations.filter { it.civilizationId == civilizationId }

    fun activeProcessesFor(civilizationId: String): List<HistoricalProcess> =
        processes.filter { it.isActive && civilizationId in it.civilizationIds }

    fun activeCommitmentsFor(civilizationId: String): List<HistoricalCommitment> =
        commitments.filter { it.civilizationId == civilizationId && it.status == HistoricalCommitmentStatus.ACTIVE }

    fun causalLinksFor(civilizationId: String): List<HistoricalCausalLink> =
        causalLinks.filter { civilizationId in it.civilizationIds }

    fun legaciesFor(civilizationId: String): List<HistoricalLegacy> =
        legacies.filter { civilizationId in it.civilizationIds }
}

data class StructuralCommitmentDefinition(
    val id: String,
    val family: String,
    val titleUk: String,
    val benefitUk: String,
    val recurringCostUk: String,
    val annualTechnologyDelta: Double = 0.0,
    val annualStabilityDelta: Double = 0.0,
    val annualTreasuryDelta: Double = 0.0,
    val annualFoodPerPersonDelta: Double = 0.0,
)

data class ActiveStructuralCommitment(
    val definition: StructuralCommitmentDefinition,
    val originTick: Long,
)
