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

/**
 * Structured visual intent exported by the optional adult module.
 *
 * The app consumes this contract without depending on feature:adult. It intentionally carries
 * semantic recipe data instead of prompt prose so renderers can map the same adult scene to
 * different visual backends while preserving participants, composition and setting.
 */
data class AdultVisualSceneDescriptor(
    val requestId: String,
    val intent: String,
    val eventCode: String,
    val participants: List<AdultParticipantRef>,
    val recipeId: String,
    val sceneFamily: String,
    val rigLayout: String,
    val poseKey: String,
    val wardrobeKey: String,
    val settingKey: String,
    val cameraKey: String,
    val lightingKey: String,
    val explicitness: String,
    val effectTags: Set<String> = emptySet(),
    val mediaTags: Set<String> = emptySet(),
) {
    init {
        require(requestId.isNotBlank())
        require(intent.isNotBlank())
        require(eventCode.isNotBlank())
        require(participants.isNotEmpty())
        require(participants.all { it.ageYears >= 18 }) { "Adult visual scenes accept adults only" }
        require(recipeId.isNotBlank())
        require(sceneFamily.isNotBlank())
        require(rigLayout.isNotBlank())
        require(poseKey.isNotBlank())
        require(wardrobeKey.isNotBlank())
        require(settingKey.isNotBlank())
        require(cameraKey.isNotBlank())
        require(lightingKey.isNotBlank())
        require(explicitness.isNotBlank())
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
    override val contractVersion = ADULT_CONTRACT_VERSION
    override fun evaluate(request: AdultEventRequest) = AdultModuleResult(
        requestId = request.requestId,
        eventCode = "NO_OP",
    )
}
