package com.sendmefile77.chronosphere.evolution

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import kotlin.math.abs

/** Direct, deterministic player influence over biological history. */
class PlayerEvolutionInterventionEngine {
    enum class Kind { DIVERGE, MUTATE, HYBRIDIZE }

    data class HybridCandidate(
        val settlementId: String,
        val lineageId: String,
        val lineageLabel: String,
        val difference: Double,
    )

    data class Result(
        val state: EvolutionState,
        val event: SimulationEvent,
    )

    fun bestHybridCandidate(state: EvolutionState, settlementId: String): HybridCandidate? {
        val sourcePopulation = state.population(settlementId) ?: return null
        val sourceLineage = state.lineage(sourcePopulation.lineageId) ?: return null
        return state.populations.asSequence()
            .filter { it.settlementId != settlementId && it.population > 0L }
            .mapNotNull { candidatePopulation ->
                val candidateLineage = state.lineage(candidatePopulation.lineageId) ?: return@mapNotNull null
                if (candidateLineage.id == sourceLineage.id) return@mapNotNull null
                val difference = lineageDifference(sourceLineage, candidateLineage)
                HybridCandidate(
                    settlementId = candidatePopulation.settlementId,
                    lineageId = candidateLineage.id,
                    lineageLabel = candidateLineage.label,
                    difference = difference,
                )
            }
            .filter { it.difference >= MIN_HYBRID_DIFFERENCE }
            .maxByOrNull { it.difference }
    }

    fun apply(
        kind: Kind,
        state: EvolutionState,
        world: LivingPlanetState,
        settlementId: String,
    ): Result {
        require(state.worldSeed == world.worldSeed)
        val settlement = world.settlements.firstOrNull { it.id == settlementId }
            ?: error("Unknown settlement $settlementId")
        val population = state.population(settlementId)
            ?: error("No evolution population for $settlementId")
        val lineage = state.lineage(population.lineageId)
            ?: error("Unknown lineage ${population.lineageId}")

        return when (kind) {
            Kind.DIVERGE -> diverge(state, settlement.id, settlement.name, settlement.civilizationId, population, lineage)
            Kind.MUTATE -> mutate(state, settlement.id, settlement.name, settlement.civilizationId, population, lineage)
            Kind.HYBRIDIZE -> hybridize(state, world, settlement.id, settlement.name, settlement.civilizationId, population, lineage)
        }
    }

    private fun diverge(
        state: EvolutionState,
        settlementId: String,
        settlementName: String,
        civilizationId: String,
        population: EvolutionPopulation,
        parent: PopulationLineage,
    ): Result {
        val salt = "diverge:${parent.id}:${state.tick}:${state.lineages.size}"
        val source = parent.morphology
        fun signed(name: String): Double = if (unit(state.worldSeed, "$salt:$name") < 0.5) -1.0 else 1.0
        fun scale(value: Double, name: String): Double = (value + signed(name) * 0.10).coerceIn(0.50, 1.60)
        fun trait(value: Double, name: String): Double = (value + signed(name) * 0.16).coerceIn(0.0, 1.0)
        val morphology = source.copy(
            heightScale = scale(source.heightScale, "height"),
            massScale = scale(source.massScale, "mass"),
            limbScale = scale(source.limbScale, "limbs"),
            cranialScale = scale(source.cranialScale, "cranial"),
            pigmentation = trait(source.pigmentation, "pigment"),
            hairCoverage = trait(source.hairCoverage, "hair"),
            eyeSize = trait(source.eyeSize, "eyes"),
            coldAdaptation = trait(source.coldAdaptation, "cold"),
            heatAdaptation = trait(source.heatAdaptation, "heat"),
            oxygenAdaptation = trait(source.oxygenAdaptation, "oxygen"),
        )
        val newId = interventionLineageId("diverged", settlementId, parent.id, state)
        val divergence = combinedDivergence(morphology, parent.bodyPlan)
        val child = PopulationLineage(
            id = newId,
            parentLineageId = parent.id,
            label = "Відокремлена лінія $settlementName",
            originSettlementId = settlementId,
            formedTick = state.tick,
            rank = rankFor(divergence, parent.bodyPlan),
            generation = parent.generation + 1,
            morphology = morphology,
            bodyPlan = parent.bodyPlan,
            divergenceFromOrigin = divergence,
            tags = parent.tags + setOf("player_directed", "accelerated_divergence"),
        )
        val nextPopulation = population.copy(
            lineageId = child.id,
            isolation = (population.isolation + 0.30).coerceIn(0.0, 1.0),
            geneFlow = (population.geneFlow - 0.30).coerceIn(0.0, 1.0),
            mutationPressure = (population.mutationPressure + 0.25).coerceIn(0.0, 0.85),
            ancestry = mapOf(child.id to 1.0),
        )
        return Result(
            state = replace(state, nextPopulation, child),
            event = SimulationEvent(
                id = "player-evolution-diverge-$settlementId-${state.tick}-${state.lineages.size}",
                tick = state.tick,
                code = "PLAYER_EVOLUTION_DIVERGENCE",
                actorIds = listOf(civilizationId, parent.id, child.id),
                locationId = settlementId,
                numbers = mapOf("divergence" to divergence),
                facts = mapOf("settlement" to settlementName, "lineage" to child.label, "parent" to parent.label),
            ),
        )
    }

    private fun mutate(
        state: EvolutionState,
        settlementId: String,
        settlementName: String,
        civilizationId: String,
        population: EvolutionPopulation,
        parent: PopulationLineage,
    ): Result {
        val key = "player-mutation:${parent.id}:${state.tick}:${state.lineages.size}"
        val bodyPlan = structuralMutation(parent.bodyPlan, deterministicInt(state.worldSeed, key, 7))
        val newId = interventionLineageId("mutant", settlementId, parent.id, state)
        val divergence = combinedDivergence(parent.morphology, bodyPlan)
        val child = PopulationLineage(
            id = newId,
            parentLineageId = parent.id,
            label = "Мутантна лінія $settlementName",
            originSettlementId = settlementId,
            formedTick = state.tick,
            rank = rankFor(divergence, bodyPlan),
            generation = parent.generation + 1,
            morphology = parent.morphology,
            bodyPlan = bodyPlan,
            divergenceFromOrigin = divergence,
            tags = parent.tags + setOf("player_directed", "structural_divergence", "mutation_pressure"),
        )
        val nextPopulation = population.copy(
            lineageId = child.id,
            mutationPressure = 0.95,
            ancestry = mapOf(child.id to 1.0),
        )
        return Result(
            state = replace(state, nextPopulation, child),
            event = SimulationEvent(
                id = "player-structural-mutation-$settlementId-${state.tick}-${state.lineages.size}",
                tick = state.tick,
                code = "PLAYER_STRUCTURAL_MUTATION",
                actorIds = listOf(civilizationId, parent.id, child.id),
                locationId = settlementId,
                numbers = mapOf("divergence" to divergence),
                facts = mapOf(
                    "settlement" to settlementName,
                    "lineage" to child.label,
                    "parent" to parent.label,
                    "bodyPlan" to bodyPlanSummary(bodyPlan),
                ),
            ),
        )
    }

    private fun hybridize(
        state: EvolutionState,
        world: LivingPlanetState,
        settlementId: String,
        settlementName: String,
        civilizationId: String,
        population: EvolutionPopulation,
        primary: PopulationLineage,
    ): Result {
        val candidate = bestHybridCandidate(state, settlementId)
            ?: error("Спочатку створіть достатньо відмінну біологічну лінію")
        val secondary = state.lineage(candidate.lineageId) ?: error("Missing hybrid donor lineage")
        val secondarySettlement = world.settlements.firstOrNull { it.id == candidate.settlementId }
        val aShare = 0.55
        val bShare = 0.45
        val morphology = blend(primary.morphology, secondary.morphology, aShare)
        val bodyPlan = inheritBodyPlan(primary.bodyPlan, secondary.bodyPlan, aShare, state.worldSeed, "hybrid:${primary.id}:${secondary.id}:${state.tick}")
        val newId = interventionLineageId("hybrid", settlementId, primary.id + secondary.id, state)
        val divergence = combinedDivergence(morphology, bodyPlan)
        val hybrid = PopulationLineage(
            id = newId,
            parentLineageId = primary.id,
            secondaryParentLineageId = secondary.id,
            label = "Гібридна лінія $settlementName",
            originSettlementId = settlementId,
            formedTick = state.tick,
            rank = rankFor(divergence, bodyPlan),
            generation = maxOf(primary.generation, secondary.generation) + 1,
            morphology = morphology,
            bodyPlan = bodyPlan,
            divergenceFromOrigin = divergence,
            tags = primary.tags + secondary.tags + setOf("player_directed", "hybrid", "mixed_ancestry"),
        )
        val nextPopulation = population.copy(
            lineageId = hybrid.id,
            mutationPressure = (population.mutationPressure * 0.6).coerceIn(0.0, 1.0),
            ancestry = linkedMapOf(primary.id to aShare, secondary.id to bShare),
        )
        return Result(
            state = replace(state, nextPopulation, hybrid),
            event = SimulationEvent(
                id = "player-hybridization-$settlementId-${state.tick}-${state.lineages.size}",
                tick = state.tick,
                code = "PLAYER_HYBRIDIZATION",
                actorIds = listOf(civilizationId, primary.id, secondary.id, hybrid.id),
                locationId = settlementId,
                numbers = mapOf("primaryShare" to aShare, "secondaryShare" to bShare, "difference" to candidate.difference),
                facts = mapOf(
                    "settlement" to settlementName,
                    "lineage" to hybrid.label,
                    "primary" to primary.label,
                    "secondary" to secondary.label,
                    "secondarySettlement" to (secondarySettlement?.name ?: candidate.settlementId),
                ),
            ),
        )
    }

    private fun replace(state: EvolutionState, population: EvolutionPopulation, lineage: PopulationLineage): EvolutionState =
        state.copy(
            lineages = (state.lineages + lineage).distinctBy { it.id }.sortedBy { it.id },
            populations = state.populations.map { if (it.id == population.id) population else it },
        )

    private fun structuralMutation(current: BodyPlan, index: Int): BodyPlan = when (index) {
        0 -> current.copy(armPairs = if (current.armPairs < 3) current.armPairs + 1 else 1)
        1 -> current.copy(legPairs = if (current.legPairs < 2) current.legPairs + 1 else 1)
        2 -> current.copy(eyeCount = if (current.eyeCount <= 4) current.eyeCount + 2 else 2)
        3 -> current.copy(hasTail = !current.hasTail)
        4 -> current.copy(digitCount = if (current.digitCount < 7) current.digitCount + 1 else 3)
        5 -> current.copy(covering = when (current.covering) {
            SkinCovering.BARE_SKIN -> SkinCovering.FINE_FUR
            SkinCovering.FINE_FUR -> SkinCovering.SCALES
            SkinCovering.SCALES -> SkinCovering.DENSE_HAIR
            SkinCovering.DENSE_HAIR -> SkinCovering.BARE_SKIN
        })
        else -> current.copy(posture = if (current.posture == Posture.UPRIGHT) Posture.SEMI_UPRIGHT else Posture.UPRIGHT)
    }

    private fun blend(a: MorphologyProfile, b: MorphologyProfile, aShare: Double): MorphologyProfile {
        val bShare = 1.0 - aShare
        fun f(x: Double, y: Double) = x * aShare + y * bShare
        return MorphologyProfile(
            heightScale = f(a.heightScale, b.heightScale), massScale = f(a.massScale, b.massScale),
            limbScale = f(a.limbScale, b.limbScale), shoulderHipRatio = f(a.shoulderHipRatio, b.shoulderHipRatio),
            cranialScale = f(a.cranialScale, b.cranialScale), pigmentation = f(a.pigmentation, b.pigmentation),
            hairCoverage = f(a.hairCoverage, b.hairCoverage), eyeSize = f(a.eyeSize, b.eyeSize),
            coldAdaptation = f(a.coldAdaptation, b.coldAdaptation), heatAdaptation = f(a.heatAdaptation, b.heatAdaptation),
            oxygenAdaptation = f(a.oxygenAdaptation, b.oxygenAdaptation), radiationTolerance = f(a.radiationTolerance, b.radiationTolerance),
            boneDensity = f(a.boneDensity, b.boneDensity), fertilityBaseline = f(a.fertilityBaseline, b.fertilityBaseline),
            longevityScale = f(a.longevityScale, b.longevityScale), sexualDimorphism = f(a.sexualDimorphism, b.sexualDimorphism),
        )
    }

    private fun inheritBodyPlan(a: BodyPlan, b: BodyPlan, aShare: Double, seed: Long, key: String): BodyPlan {
        fun choose(salt: String): Boolean = unit(seed, "$key:$salt") < aShare
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

    private fun lineageDifference(a: PopulationLineage, b: PopulationLineage): Double {
        val morph = a.morphology.distanceTo(b.morphology)
        var structural = 0.0
        structural += abs(a.bodyPlan.armPairs - b.bodyPlan.armPairs) * 0.18
        structural += abs(a.bodyPlan.legPairs - b.bodyPlan.legPairs) * 0.22
        structural += abs(a.bodyPlan.eyeCount - b.bodyPlan.eyeCount) * 0.035
        structural += if (a.bodyPlan.hasTail != b.bodyPlan.hasTail) 0.10 else 0.0
        structural += if (a.bodyPlan.covering != b.bodyPlan.covering) 0.08 else 0.0
        structural += if (a.bodyPlan.posture != b.bodyPlan.posture) 0.06 else 0.0
        return (morph * 0.65 + structural).coerceIn(0.0, 1.0)
    }

    private fun combinedDivergence(morphology: MorphologyProfile, bodyPlan: BodyPlan): Double =
        (morphology.distanceTo(MorphologyProfile.HUMAN_BASELINE) + structuralDistance(bodyPlan)).coerceIn(0.0, 1.0)

    private fun structuralDistance(bodyPlan: BodyPlan): Double {
        var value = 0.0
        value += abs(bodyPlan.armPairs - 1) * 0.12
        value += abs(bodyPlan.legPairs - 1) * 0.14
        value += abs(bodyPlan.eyeCount - 2) * 0.025
        value += abs(bodyPlan.digitCount - 5) * 0.018
        if (bodyPlan.hasTail) value += 0.08
        if (bodyPlan.posture != Posture.UPRIGHT) value += 0.06
        if (bodyPlan.covering != SkinCovering.BARE_SKIN) value += 0.07
        return value.coerceIn(0.0, 0.65)
    }

    private fun rankFor(divergence: Double, bodyPlan: BodyPlan): BiologicalRank = when {
        bodyPlan.armPairs != 1 || bodyPlan.legPairs != 1 || divergence >= 0.22 -> BiologicalRank.SPECIES
        divergence >= 0.12 -> BiologicalRank.SUBSPECIES
        divergence >= 0.055 -> BiologicalRank.MORPH
        else -> BiologicalRank.POPULATION
    }

    private fun bodyPlanSummary(bodyPlan: BodyPlan): String = listOf(
        "arms=${bodyPlan.armPairs * 2}", "legs=${bodyPlan.legPairs * 2}", "eyes=${bodyPlan.eyeCount}",
        "digits=${bodyPlan.digitCount}", "tail=${bodyPlan.hasTail}", "covering=${bodyPlan.covering.name.lowercase()}",
        "posture=${bodyPlan.posture.name.lowercase()}",
    ).joinToString(",")

    private fun interventionLineageId(prefix: String, settlementId: String, parentKey: String, state: EvolutionState): String =
        "$prefix-$settlementId-${stableToken("$parentKey:${state.tick}:${state.lineages.size}")}" 

    private fun deterministicInt(seed: Long, key: String, bound: Int): Int =
        (unit(seed, key) * bound).toInt().coerceIn(0, bound - 1)

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

    companion object {
        const val MIN_HYBRID_DIFFERENCE = 0.075
    }
}
