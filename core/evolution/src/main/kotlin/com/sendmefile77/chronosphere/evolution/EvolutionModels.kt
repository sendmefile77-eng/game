package com.sendmefile77.chronosphere.evolution

import kotlin.math.abs

enum class BiologicalRank {
    POPULATION,
    MORPH,
    SUBSPECIES,
    SPECIES,
}

enum class Posture { UPRIGHT, SEMI_UPRIGHT }
enum class SkinCovering { BARE_SKIN, DENSE_HAIR, FINE_FUR, SCALES }

data class BodyPlan(
    val armPairs: Int = 1,
    val legPairs: Int = 1,
    val eyeCount: Int = 2,
    val digitCount: Int = 5,
    val hasTail: Boolean = false,
    val posture: Posture = Posture.UPRIGHT,
    val covering: SkinCovering = SkinCovering.BARE_SKIN,
) {
    init {
        require(armPairs in 1..3)
        require(legPairs in 1..2)
        require(eyeCount in 1..6)
        require(digitCount in 3..7)
    }
}

data class MorphologyProfile(
    val heightScale: Double = 1.0,
    val massScale: Double = 1.0,
    val limbScale: Double = 1.0,
    val shoulderHipRatio: Double = 1.0,
    val cranialScale: Double = 1.0,
    val pigmentation: Double = 0.50,
    val hairCoverage: Double = 0.35,
    val eyeSize: Double = 0.50,
    val coldAdaptation: Double = 0.50,
    val heatAdaptation: Double = 0.50,
    val oxygenAdaptation: Double = 0.35,
    val radiationTolerance: Double = 0.10,
    val boneDensity: Double = 1.0,
    val fertilityBaseline: Double = 0.55,
    val longevityScale: Double = 1.0,
    val sexualDimorphism: Double = 0.45,
) {
    init {
        listOf(heightScale, massScale, limbScale, shoulderHipRatio, cranialScale, boneDensity, longevityScale)
            .forEach { require(it.isFinite() && it in 0.50..1.60) }
        listOf(
            pigmentation,
            hairCoverage,
            eyeSize,
            coldAdaptation,
            heatAdaptation,
            oxygenAdaptation,
            radiationTolerance,
            fertilityBaseline,
            sexualDimorphism,
        ).forEach { require(it.isFinite() && it in 0.0..1.0) }
    }

    fun distanceTo(other: MorphologyProfile): Double {
        val normalized = listOf(
            abs(heightScale - other.heightScale) / 1.10,
            abs(massScale - other.massScale) / 1.10,
            abs(limbScale - other.limbScale) / 1.10,
            abs(shoulderHipRatio - other.shoulderHipRatio) / 1.10,
            abs(cranialScale - other.cranialScale) / 1.10,
            abs(pigmentation - other.pigmentation),
            abs(hairCoverage - other.hairCoverage),
            abs(eyeSize - other.eyeSize),
            abs(coldAdaptation - other.coldAdaptation),
            abs(heatAdaptation - other.heatAdaptation),
            abs(oxygenAdaptation - other.oxygenAdaptation),
            abs(radiationTolerance - other.radiationTolerance),
            abs(boneDensity - other.boneDensity) / 1.10,
            abs(fertilityBaseline - other.fertilityBaseline),
            abs(longevityScale - other.longevityScale) / 1.10,
            abs(sexualDimorphism - other.sexualDimorphism),
        )
        return normalized.average().coerceIn(0.0, 1.0)
    }

    companion object {
        val HUMAN_BASELINE = MorphologyProfile()
    }
}

data class PopulationLineage(
    val id: String,
    val parentLineageId: String?,
    val label: String,
    val originSettlementId: String?,
    val formedTick: Long,
    val rank: BiologicalRank,
    val generation: Int,
    val morphology: MorphologyProfile,
    val bodyPlan: BodyPlan = BodyPlan(),
    val divergenceFromOrigin: Double = 0.0,
    val tags: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(formedTick >= 0L)
        require(generation >= 0)
        require(divergenceFromOrigin.isFinite() && divergenceFromOrigin in 0.0..1.0)
    }
}

data class EvolutionPopulation(
    val id: String,
    val settlementId: String,
    val lineageId: String,
    val population: Long,
    val isolation: Double,
    val geneFlow: Double,
    val mutationPressure: Double = 0.0,
) {
    init {
        require(id.isNotBlank() && settlementId.isNotBlank() && lineageId.isNotBlank())
        require(population >= 0L)
        require(isolation.isFinite() && isolation in 0.0..1.0)
        require(geneFlow.isFinite() && geneFlow in 0.0..1.0)
        require(mutationPressure.isFinite() && mutationPressure in 0.0..1.0)
    }
}

data class MorphologyVisualDescriptor(
    val lineageId: String,
    val rank: BiologicalRank,
    val bodyPlan: BodyPlan,
    val numeric: Map<String, Double>,
    val tags: Set<String>,
)

data class EvolutionState(
    val worldSeed: Long,
    val tick: Long,
    val lineages: List<PopulationLineage>,
    val populations: List<EvolutionPopulation>,
) {
    fun population(settlementId: String): EvolutionPopulation? =
        populations.firstOrNull { it.settlementId == settlementId }

    fun lineage(lineageId: String): PopulationLineage? = lineages.firstOrNull { it.id == lineageId }

    fun lineageForSettlement(settlementId: String): PopulationLineage? =
        population(settlementId)?.let { lineage(it.lineageId) }

    fun visualDescriptor(settlementId: String): MorphologyVisualDescriptor? {
        val lineage = lineageForSettlement(settlementId) ?: return null
        val morphology = lineage.morphology
        val tags = buildSet {
            add("lineage:${lineage.id}")
            add("bio_rank:${lineage.rank.name.lowercase()}")
            add("posture:${lineage.bodyPlan.posture.name.lowercase()}")
            add("covering:${lineage.bodyPlan.covering.name.lowercase()}")
            add("arms:${lineage.bodyPlan.armPairs * 2}")
            add("legs:${lineage.bodyPlan.legPairs * 2}")
            add("eyes:${lineage.bodyPlan.eyeCount}")
            if (lineage.bodyPlan.hasTail) add("tail")
            addAll(lineage.tags.map { it.lowercase() })
        }.toSortedSet()
        return MorphologyVisualDescriptor(
            lineageId = lineage.id,
            rank = lineage.rank,
            bodyPlan = lineage.bodyPlan,
            numeric = linkedMapOf(
                "morph_height" to morphology.heightScale,
                "morph_mass" to morphology.massScale,
                "morph_limbs" to morphology.limbScale,
                "morph_cranial" to morphology.cranialScale,
                "morph_pigmentation" to morphology.pigmentation,
                "morph_hair" to morphology.hairCoverage,
                "morph_eye_size" to morphology.eyeSize,
                "morph_dimorphism" to morphology.sexualDimorphism,
                "morph_divergence" to lineage.divergenceFromOrigin,
            ),
            tags = tags,
        )
    }
}
