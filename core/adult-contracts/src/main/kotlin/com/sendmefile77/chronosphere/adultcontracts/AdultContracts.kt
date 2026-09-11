package com.sendmefile77.chronosphere.adultcontracts

const val ADULT_CONTRACT_VERSION = 1

data class AdultParticipantRef(
    val entityId: String,
    val ageYears: Int,
) {
    init { require(entityId.isNotBlank()) }
}

data class AdultWorldContext(
    val worldSeed: Long,
    val tick: Long,
    val cultureTags: Set<String> = emptySet(),
    val numericContext: Map<String, Double> = emptyMap(),
)

data class AdultEventRequest(
    val requestId: String,
    val participants: List<AdultParticipantRef>,
    val context: AdultWorldContext,
) {
    init {
        require(requestId.isNotBlank())
        require(participants.isNotEmpty())
        require(participants.all { it.ageYears >= 18 }) { "Adult module accepts adults only" }
    }
}

enum class CoreEffectKind { RELATIONSHIP, REPUTATION, DEMOGRAPHY, CULTURE }

data class CoreEffectProposal(
    val kind: CoreEffectKind,
    val targetId: String,
    val magnitude: Double,
    val reasonCode: String,
)

data class MediaCue(
    val assetKey: String,
    val tags: Set<String> = emptySet(),
)

data class AdultModuleResult(
    val requestId: String,
    val eventCode: String,
    val effects: List<CoreEffectProposal> = emptyList(),
    val mediaCue: MediaCue? = null,
)

interface AdultModule {
    val contractVersion: Int
    fun evaluate(request: AdultEventRequest): AdultModuleResult
}

object NoOpAdultModule : AdultModule {
    override val contractVersion: Int = ADULT_CONTRACT_VERSION
    override fun evaluate(request: AdultEventRequest) = AdultModuleResult(
        requestId = request.requestId,
        eventCode = "NO_OP",
    )
}
