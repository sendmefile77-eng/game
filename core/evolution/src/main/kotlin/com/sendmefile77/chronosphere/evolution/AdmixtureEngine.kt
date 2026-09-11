package com.sendmefile77.chronosphere.evolution

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.worldgen.WorldMap
import kotlin.math.hypot

/**
 * Population contact layer. Biological ancestry changes slowly through gene flow.
 * Cultural identity assimilates independently and generally faster.
 */
class AdmixtureEngine(private val map: WorldMap) {
    fun annualStep(
        state: EvolutionState,
        world: LivingPlanetState,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): EvolutionState {
        val settlements = world.settlements.associateBy { it.id }
        val snapshot = state.populations.sortedBy { it.id }
        val lineages = state.lineages.associateBy { it.id }.toMutableMap()
        val diagonal = hypot(map.width.toDouble(), map.height.toDouble()).coerceAtLeast(1.0)

        val updated = snapshot.map { recipient ->
            val settlement = settlements[recipient.settlementId] ?: return@map recipient
            val donor = snapshot.asSequence()
                .filter { it.id != recipient.id && it.population > 0L }
                .mapNotNull { candidate ->
                    val other = settlements[candidate.settlementId] ?: return@mapNotNull null
                    val distance = hypot(
                        (settlement.x - other.x).toDouble(),
                        (settlement.y - other.y).toDouble(),
                    )
                    Triple(candidate, other, distance)
                }
                .filter { it.third / diagonal <= 0.34 }
                .minByOrNull { it.third }

            val oldIdentity = if (recipient.culturalIdentity.isEmpty()) {
                mapOf(settlement.civilizationId to 1.0)
            } else recipient.culturalIdentity
            val assimilationRate = (0.008 + recipient.geneFlow * 0.030).coerceIn(0.004, 0.045)
            val nextIdentity = normalize(
                scale(oldIdentity, 1.0 - assimilationRate) + mapOf(settlement.civilizationId to assimilationRate),
            )

            if (donor == null) {
                return@map recipient.copy(culturalIdentity = nextIdentity)
            }

            val donorPopulation = donor.first
            val donorSettlement = donor.second
            val distanceFactor = (1.0 - donor.third / (diagonal * 0.34)).coerceIn(0.0, 1.0)
            val atWar = world.wars.any { it.matches(settlement.civilizationId, donorSettlement.civilizationId) }
            val polityFactor = when {
                atWar -> 0.05
                settlement.civilizationId == donorSettlement.civilizationId -> 1.0
                else -> 0.55
            }
            val compatibility = hybridCompatibility(
                lineages[recipient.lineageId],
                lineages[donorPopulation.lineageId],
            )
            val contact = distanceFactor * minOf(recipient.geneFlow, donorPopulation.geneFlow) * polityFactor
            val geneRate = (contact * compatibility * 0.012).coerceIn(0.0, 0.014)
            if (geneRate <= 0.000_001) return@map recipient.copy(culturalIdentity = nextIdentity)

            val mixedAncestry = mix(
                recipient.ancestry,
                donorPopulation.ancestry,
                geneRate,
            )
            val admixture = (1.0 - (mixedAncestry.values.maxOrNull() ?: 1.0)).coerceIn(0.0, 1.0)
            val meaningful = mixedAncestry.entries.filter { it.value >= 0.10 }.sortedByDescending { it.value }

            var lineageId = recipient.lineageId
            if (meaningful.size >= 2 && admixture >= HYBRID_THRESHOLD) {
                val primaryId = meaningful[0].key
                val secondaryId = meaningful[1].key
                val hybridId = hybridLineageId(settlement.id, primaryId, secondaryId)
                val existing = lineages[hybridId]
                if (existing == null) {
                    val primary = lineages[primaryId] ?: lineages[recipient.lineageId]
                    val secondary = lineages[secondaryId] ?: lineages[donorPopulation.lineageId]
                    if (primary != null && secondary != null) {
                        val morphology = blendMorphology(primary.morphology, secondary.morphology, meaningful[0].value, meaningful[1].value)
                        val bodyPlan = inheritBodyPlan(
                            primary.bodyPlan,
                            secondary.bodyPlan,
                            meaningful[0].value,
                            state.worldSeed,
                            "$hybridId:$tick",
                        )
                        val divergence = (
                            morphology.distanceTo(MorphologyProfile.HUMAN_BASELINE) +
                                if (bodyPlan == BodyPlan()) 0.0 else 0.12
                            ).coerceIn(0.0, 1.0)
                        val hybrid = PopulationLineage(
                            id = hybridId,
                            parentLineageId = primary.id,
                            secondaryParentLineageId = secondary.id,
                            label = "Гібридна лінія ${settlement.name}",
                            originSettlementId = settlement.id,
                            formedTick = tick,
                            rank = hybridRank(primary.rank, secondary.rank, divergence),
                            generation = maxOf(primary.generation, secondary.generation) + 1,
                            morphology = morphology,
                            bodyPlan = bodyPlan,
                            divergenceFromOrigin = divergence,
                            tags = (primary.tags + secondary.tags + setOf("hybrid", "mixed_ancestry")).toSortedSet(),
                        )
                        lineages[hybrid.id] = hybrid
                        events += SimulationEvent(
                            id = "hybrid-lineage-${settlement.id}-$tick-${stableToken(primary.id + secondary.id)}",
                            tick = tick,
                            code = "HYBRID_LINEAGE_FORMED",
                            actorIds = listOf(settlement.civilizationId, primary.id, secondary.id, hybrid.id),
                            locationId = settlement.id,
                            numbers = mapOf(
                                "primaryShare" to meaningful[0].value,
                                "secondaryShare" to meaningful[1].value,
                                "admixture" to admixture,
                            ),
                            facts = mapOf(
                                "settlement" to settlement.name,
                                "lineage" to hybrid.label,
                                "primary" to primary.label,
                                "secondary" to secondary.label,
                            ),
                        )
                    }
                }
                if (lineages.containsKey(hybridId)) lineageId = hybridId
            }

            val oldOwnerShare = oldIdentity[settlement.civilizationId] ?: 0.0
            val newOwnerShare = nextIdentity[settlement.civilizationId] ?: 0.0
            if (oldOwnerShare < 0.60 && newOwnerShare >= 0.60) {
                events += SimulationEvent(
                    id = "cultural-assimilation-${settlement.id}-${settlement.civilizationId}-$tick",
                    tick = tick,
                    code = "CULTURAL_ASSIMILATION",
                    actorIds = listOf(settlement.civilizationId),
                    locationId = settlement.id,
                    numbers = mapOf("identityShare" to newOwnerShare),
                    facts = mapOf("settlement" to settlement.name),
                )
            }

            recipient.copy(
                lineageId = lineageId,
                ancestry = mixedAncestry,
                culturalIdentity = nextIdentity,
            )
        }
        return state.copy(tick = tick, lineages = lineages.values.sortedBy { it.id }, populations = updated)
    }

    private fun hybridCompatibility(a: PopulationLineage?, b: PopulationLineage?): Double {
        if (a == null || b == null) return 0.0
        val morphology = (1.0 - a.morphology.distanceTo(b.morphology)).coerceIn(0.0, 1.0)
        var structural = 1.0
        structural -= kotlin.math.abs(a.bodyPlan.armPairs - b.bodyPlan.armPairs) * 0.20
        structural -= kotlin.math.abs(a.bodyPlan.legPairs - b.bodyPlan.legPairs) * 0.24
        structural -= kotlin.math.abs(a.bodyPlan.eyeCount - b.bodyPlan.eyeCount) * 0.035
        if (a.bodyPlan.posture != b.bodyPlan.posture) structural -= 0.08
        return (morphology * structural.coerceIn(0.05, 1.0)).coerceIn(0.02, 1.0)
    }

    private fun mix(a: Map<String, Double>, b: Map<String, Double>, donorShare: Double): Map<String, Double> {
        val combined = linkedMapOf<String, Double>()
        a.forEach { (id, share) -> combined[id] = (combined[id] ?: 0.0) + share * (1.0 - donorShare) }
        b.forEach { (id, share) -> combined[id] = (combined[id] ?: 0.0) + share * donorShare }
        return normalize(combined)
    }

    private fun scale(values: Map<String, Double>, factor: Double): Map<String, Double> =
        values.mapValues { (_, value) -> value * factor }

    private operator fun Map<String, Double>.plus(other: Map<String, Double>): Map<String, Double> {
        val result = toMutableMap()
        other.forEach { (key, value) -> result[key] = (result[key] ?: 0.0) + value }
        return result
    }

    private fun normalize(values: Map<String, Double>): Map<String, Double> {
        val filtered = values.filterValues { it.isFinite() && it > 0.000_000_1 }
        val total = filtered.values.sum().coerceAtLeast(0.000_000_1)
        val normalized = filtered.mapValues { (_, value) -> value / total }.toMutableMap()
        val key = normalized.maxByOrNull { it.value }?.key ?: return emptyMap()
        val correction = 1.0 - normalized.values.sum()
        normalized[key] = (normalized.getValue(key) + correction).coerceAtLeast(0.0)
        return normalized.toSortedMap()
    }

    private fun blendMorphology(a: MorphologyProfile, b: MorphologyProfile, aShare: Double, bShare: Double): MorphologyProfile {
        val total = (aShare + bShare).coerceAtLeast(0.0001)
        val wa = aShare / total
        val wb = bShare / total
        fun blend(x: Double, y: Double) = x * wa + y * wb
        return MorphologyProfile(
            heightScale = blend(a.heightScale, b.heightScale), massScale = blend(a.massScale, b.massScale),
            limbScale = blend(a.limbScale, b.limbScale), shoulderHipRatio = blend(a.shoulderHipRatio, b.shoulderHipRatio),
            cranialScale = blend(a.cranialScale, b.cranialScale), pigmentation = blend(a.pigmentation, b.pigmentation),
            hairCoverage = blend(a.hairCoverage, b.hairCoverage), eyeSize = blend(a.eyeSize, b.eyeSize),
            coldAdaptation = blend(a.coldAdaptation, b.coldAdaptation), heatAdaptation = blend(a.heatAdaptation, b.heatAdaptation),
            oxygenAdaptation = blend(a.oxygenAdaptation, b.oxygenAdaptation), radiationTolerance = blend(a.radiationTolerance, b.radiationTolerance),
            boneDensity = blend(a.boneDensity, b.boneDensity), fertilityBaseline = blend(a.fertilityBaseline, b.fertilityBaseline),
            longevityScale = blend(a.longevityScale, b.longevityScale), sexualDimorphism = blend(a.sexualDimorphism, b.sexualDimorphism),
        )
    }

    private fun inheritBodyPlan(a: BodyPlan, b: BodyPlan, aShare: Double, seed: Long, key: String): BodyPlan {
        if (a == b) return a
        fun choose(salt: String): Boolean = unit(seed, "$key:$salt") < aShare.coerceIn(0.0, 1.0)
        return BodyPlan(
            armPairs = if (choose("arms")) a.armPairs else b.armPairs,
            legPairs = if (choose("legs")) a.legPairs else b.legPairs,
            eyeCount = if (choose("eyes")) a.eyeCount else b.eyeCount,
            digitCount = if (choose("digits")) a.digitCount else b.digitCount,
            hasTail = if (choose("tail")) a.hasTail else b.hasTail,
            posture = if (choose("posture")) a.posture else b.posture,
            covering = if (choose("covering")) a.covering else b.covering,
        )
    }

    private fun hybridRank(a: BiologicalRank, b: BiologicalRank, divergence: Double): BiologicalRank = when {
        a == BiologicalRank.SPECIES || b == BiologicalRank.SPECIES || divergence >= 0.20 -> BiologicalRank.SPECIES
        a == BiologicalRank.SUBSPECIES || b == BiologicalRank.SUBSPECIES || divergence >= 0.10 -> BiologicalRank.SUBSPECIES
        divergence >= 0.045 -> BiologicalRank.MORPH
        else -> BiologicalRank.POPULATION
    }

    private fun hybridLineageId(settlementId: String, a: String, b: String): String {
        val parents = listOf(a, b).sorted().joinToString("|")
        return "hybrid-$settlementId-${stableToken(parents)}"
    }

    private fun stableToken(value: String): String {
        var hash = -3750763034362895579L
        value.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
        return java.lang.Long.toUnsignedString(hash, 16)
    }

    private fun unit(seed: Long, key: String): Double {
        var hash = seed xor -3750763034362895579L
        key.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
        var z = hash
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    companion object { private const val HYBRID_THRESHOLD = 0.12 }
}
