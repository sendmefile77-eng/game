package com.sendmefile77.chronosphere.evolution

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldTile
import kotlin.math.hypot

data class EvolutionAdvanceResult(
    val state: EvolutionState,
    val events: List<SimulationEvent>,
)

class EvolutionEngine(private val map: WorldMap) {
    fun initialize(world: LivingPlanetState): EvolutionState {
        require(world.worldSeed == map.seed.value) { "Evolution world/map seed mismatch" }
        val origin = originLineage()
        val lineages = mutableListOf(origin)
        val populations = mutableListOf<EvolutionPopulation>()
        world.settlements.sortedBy { it.id }.forEach { settlement ->
            val morphology = founderMorphology(world.worldSeed, settlement, tile(settlement))
            val lineage = PopulationLineage(
                id = lineageId(settlement.id, world.tick),
                parentLineageId = ORIGIN_LINEAGE_ID,
                label = "Лінія ${settlement.name}",
                originSettlementId = settlement.id,
                formedTick = world.tick,
                rank = classify(morphology.distanceTo(MorphologyProfile.HUMAN_BASELINE), BodyPlan()),
                generation = 1,
                morphology = morphology,
                divergenceFromOrigin = morphology.distanceTo(MorphologyProfile.HUMAN_BASELINE),
                tags = setOf("human_derived"),
            )
            lineages += lineage
            populations += EvolutionPopulation(
                id = "population-${settlement.id}",
                settlementId = settlement.id,
                lineageId = lineage.id,
                population = settlement.population,
                isolation = initialIsolation(settlement, world),
                geneFlow = 1.0 - initialIsolation(settlement, world),
            )
        }
        return EvolutionState(
            worldSeed = world.worldSeed,
            tick = world.tick,
            lineages = lineages,
            populations = populations,
        )
    }

    fun advance(state: EvolutionState, world: LivingPlanetState): EvolutionAdvanceResult {
        require(state.worldSeed == world.worldSeed && state.worldSeed == map.seed.value) {
            "Evolution state/world/map seed mismatch"
        }
        require(world.tick >= state.tick) { "Cannot move evolution backward" }
        val events = mutableListOf<SimulationEvent>()
        var current = reconcile(state, world, events)
        if (world.tick == state.tick) return EvolutionAdvanceResult(current.copy(tick = world.tick), events)

        var annualTick = ((state.tick / 12L) + 1L) * 12L
        while (annualTick <= world.tick) {
            current = annualStep(current, world, annualTick, events)
            annualTick += 12L
        }
        return EvolutionAdvanceResult(current.copy(tick = world.tick), events)
    }

    /** Future disasters, radiation and biotechnology can raise this without changing the evolution schema. */
    fun setMutationPressure(state: EvolutionState, settlementId: String, pressure: Double): EvolutionState {
        require(pressure.isFinite())
        return state.copy(
            populations = state.populations.map { population ->
                if (population.settlementId == settlementId) population.copy(mutationPressure = pressure.coerceIn(0.0, 1.0))
                else population
            },
        )
    }

    private fun reconcile(
        state: EvolutionState,
        world: LivingPlanetState,
        events: MutableList<SimulationEvent>,
    ): EvolutionState {
        val settlementsById = world.settlements.associateBy { it.id }
        val retained = state.populations
            .filter { it.settlementId in settlementsById }
            .map { population -> population.copy(population = settlementsById.getValue(population.settlementId).population) }
            .toMutableList()
        val lineages = state.lineages.toMutableList()
        val knownSettlementIds = retained.mapTo(hashSetOf()) { it.settlementId }

        world.settlements.sortedBy { it.id }.filter { it.id !in knownSettlementIds }.forEach { settlement ->
            val source = nearestPopulationForNewSettlement(settlement, retained, world, state)
            val parent = source?.let { state.lineage(it.lineageId) } ?: state.lineage(ORIGIN_LINEAGE_ID) ?: originLineage()
            if (lineages.none { it.id == parent.id }) lineages += parent
            val newLineage = PopulationLineage(
                id = lineageId(settlement.id, world.tick),
                parentLineageId = parent.id,
                label = "Лінія ${settlement.name}",
                originSettlementId = settlement.id,
                formedTick = world.tick,
                rank = BiologicalRank.POPULATION,
                generation = parent.generation + 1,
                morphology = parent.morphology,
                bodyPlan = parent.bodyPlan,
                divergenceFromOrigin = parent.morphology.distanceTo(MorphologyProfile.HUMAN_BASELINE),
                tags = parent.tags + "founder_split",
            )
            lineages += newLineage
            retained += EvolutionPopulation(
                id = "population-${settlement.id}",
                settlementId = settlement.id,
                lineageId = newLineage.id,
                population = settlement.population,
                isolation = initialIsolation(settlement, world),
                geneFlow = 1.0 - initialIsolation(settlement, world),
            )
            events += SimulationEvent(
                id = "lineage-founded-${settlement.id}-${world.tick}",
                tick = world.tick,
                code = "LINEAGE_FOUNDED",
                actorIds = listOf(settlement.civilizationId, newLineage.id),
                locationId = settlement.id,
                facts = mapOf("settlement" to settlement.name, "lineage" to newLineage.label),
            )
        }
        return state.copy(lineages = lineages.distinctBy { it.id }, populations = retained)
    }

    private fun annualStep(
        state: EvolutionState,
        world: LivingPlanetState,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): EvolutionState {
        val settlementById = world.settlements.associateBy { it.id }
        val lineageById = state.lineages.associateBy { it.id }.toMutableMap()
        val populations = state.populations.map { population ->
            val settlement = settlementById[population.settlementId] ?: return@map population
            val currentLineage = lineageById[population.lineageId] ?: return@map population
            val isolation = calculateIsolation(population, settlement, state, world)
            val geneFlow = (1.0 - isolation).coerceIn(0.0, 1.0)
            val evolvedMorphology = evolveMorphology(
                morphology = currentLineage.morphology,
                tile = tile(settlement),
                isolation = isolation,
                mutationPressure = population.mutationPressure,
                seed = state.worldSeed,
                key = "${currentLineage.id}:$tick",
            )
            val evolvedBodyPlan = maybeMutateBodyPlan(
                current = currentLineage.bodyPlan,
                lineage = currentLineage,
                pressure = population.mutationPressure,
                tick = tick,
                seed = state.worldSeed,
            )
            val divergence = (
                evolvedMorphology.distanceTo(MorphologyProfile.HUMAN_BASELINE) +
                    structuralDistance(evolvedBodyPlan) * 0.35
                ).coerceIn(0.0, 1.0)
            val newRank = classify(divergence, evolvedBodyPlan)
            val updatedLineage = currentLineage.copy(
                rank = newRank,
                morphology = evolvedMorphology,
                bodyPlan = evolvedBodyPlan,
                divergenceFromOrigin = divergence,
                tags = buildSet {
                    addAll(currentLineage.tags)
                    if (isolation >= 0.70) add("isolated")
                    if (population.mutationPressure >= 0.55) add("mutation_pressure")
                    if (evolvedBodyPlan != BodyPlan()) add("structural_divergence")
                },
            )
            lineageById[currentLineage.id] = updatedLineage

            if (newRank != currentLineage.rank) {
                events += SimulationEvent(
                    id = "bio-rank-${currentLineage.id}-${newRank.name}-$tick",
                    tick = tick,
                    code = "BIOLOGICAL_DIVERGENCE",
                    actorIds = listOf(settlement.civilizationId, currentLineage.id),
                    locationId = settlement.id,
                    numbers = mapOf("divergence" to divergence),
                    facts = mapOf(
                        "settlement" to settlement.name,
                        "lineage" to currentLineage.label,
                        "rank" to newRank.name,
                    ),
                )
            }
            if (evolvedBodyPlan != currentLineage.bodyPlan) {
                events += SimulationEvent(
                    id = "structural-mutation-${currentLineage.id}-$tick",
                    tick = tick,
                    code = "STRUCTURAL_MUTATION",
                    actorIds = listOf(settlement.civilizationId, currentLineage.id),
                    locationId = settlement.id,
                    facts = mapOf(
                        "settlement" to settlement.name,
                        "lineage" to currentLineage.label,
                        "bodyPlan" to bodyPlanKey(evolvedBodyPlan),
                    ),
                )
            }
            population.copy(
                population = settlement.population,
                isolation = isolation,
                geneFlow = geneFlow,
            )
        }
        return state.copy(tick = tick, lineages = lineageById.values.sortedBy { it.id }, populations = populations)
    }

    private fun evolveMorphology(
        morphology: MorphologyProfile,
        tile: WorldTile,
        isolation: Double,
        mutationPressure: Double,
        seed: Long,
        key: String,
    ): MorphologyProfile {
        val cold = (1.0 - tile.temperature).coerceIn(0.0, 1.0)
        val heat = tile.temperature.coerceIn(0.0, 1.0)
        val highAltitude = ((tile.elevation - 0.55) / 0.45).coerceIn(0.0, 1.0)
        val rate = (0.0012 + isolation * 0.0014 + mutationPressure * 0.0018).coerceAtMost(0.005)
        fun move(value: Double, target: Double, salt: String, min: Double, max: Double): Double {
            val noise = (unit(seed, "$key:$salt") - 0.5) * rate * (0.18 + mutationPressure * 0.65)
            return (value + (target - value) * rate + noise).coerceIn(min, max)
        }
        return MorphologyProfile(
            heightScale = move(morphology.heightScale, 0.96 + heat * 0.10, "height", 0.50, 1.60),
            massScale = move(morphology.massScale, 0.92 + cold * 0.20, "mass", 0.50, 1.60),
            limbScale = move(morphology.limbScale, 0.94 + heat * 0.14, "limbs", 0.50, 1.60),
            shoulderHipRatio = move(morphology.shoulderHipRatio, 1.0 + (cold - 0.5) * 0.08, "frame", 0.50, 1.60),
            cranialScale = move(morphology.cranialScale, 1.0, "cranial", 0.50, 1.60),
            pigmentation = move(morphology.pigmentation, 0.22 + heat * 0.68, "pigment", 0.0, 1.0),
            hairCoverage = move(morphology.hairCoverage, 0.14 + cold * 0.66, "hair", 0.0, 1.0),
            eyeSize = move(morphology.eyeSize, 0.44 + (1.0 - tile.moisture) * 0.10 + cold * 0.06, "eyes", 0.0, 1.0),
            coldAdaptation = move(morphology.coldAdaptation, cold, "cold", 0.0, 1.0),
            heatAdaptation = move(morphology.heatAdaptation, heat, "heat", 0.0, 1.0),
            oxygenAdaptation = move(morphology.oxygenAdaptation, 0.25 + highAltitude * 0.70, "oxygen", 0.0, 1.0),
            radiationTolerance = move(morphology.radiationTolerance, 0.10 + mutationPressure * 0.35, "radiation", 0.0, 1.0),
            boneDensity = move(morphology.boneDensity, 0.96 + highAltitude * 0.10, "bone", 0.50, 1.60),
            fertilityBaseline = move(morphology.fertilityBaseline, 0.55, "fertility", 0.0, 1.0),
            longevityScale = move(morphology.longevityScale, 1.0, "longevity", 0.50, 1.60),
            sexualDimorphism = move(morphology.sexualDimorphism, 0.45, "dimorphism", 0.0, 1.0),
        )
    }

    private fun maybeMutateBodyPlan(
        current: BodyPlan,
        lineage: PopulationLineage,
        pressure: Double,
        tick: Long,
        seed: Long,
    ): BodyPlan {
        if (pressure < 0.55) return current
        val ageYears = ((tick - lineage.formedTick).coerceAtLeast(0L) / 12L).toInt()
        if (ageYears < 600 && pressure < 0.90) return current
        val chance = ((pressure - 0.50) * 0.0022).coerceIn(0.0, 0.0011)
        if (unit(seed, "structural:${lineage.id}:$tick") >= chance) return current
        return when (deterministicInt(seed, "structural-kind:${lineage.id}:$tick", 6)) {
            0 -> current.copy(armPairs = (current.armPairs + 1).coerceAtMost(3))
            1 -> current.copy(eyeCount = (current.eyeCount + 2).coerceAtMost(6))
            2 -> current.copy(hasTail = true)
            3 -> current.copy(digitCount = if (current.digitCount <= 5) current.digitCount + 1 else current.digitCount - 1)
            4 -> current.copy(covering = if (current.covering == SkinCovering.BARE_SKIN) SkinCovering.FINE_FUR else current.covering)
            else -> current.copy(posture = Posture.SEMI_UPRIGHT)
        }
    }

    private fun calculateIsolation(
        population: EvolutionPopulation,
        settlement: Settlement,
        state: EvolutionState,
        world: LivingPlanetState,
    ): Double {
        val settlementById = world.settlements.associateBy { it.id }
        val other = state.populations.asSequence()
            .filter { it.id != population.id }
            .mapNotNull { candidate ->
                val otherSettlement = settlementById[candidate.settlementId] ?: return@mapNotNull null
                val distance = hypot(
                    (settlement.x - otherSettlement.x).toDouble(),
                    (settlement.y - otherSettlement.y).toDouble(),
                )
                Triple(candidate, otherSettlement, distance)
            }
            .minByOrNull { it.third }
            ?: return 1.0
        val diagonal = hypot(map.width.toDouble(), map.height.toDouble()).coerceAtLeast(1.0)
        var isolation = (other.third / diagonal * 2.1).coerceIn(0.0, 1.0)
        if (other.second.civilizationId != settlement.civilizationId) isolation += 0.18
        if (world.wars.any { it.matches(settlement.civilizationId, other.second.civilizationId) }) isolation += 0.18
        return isolation.coerceIn(0.0, 1.0)
    }

    private fun nearestPopulationForNewSettlement(
        settlement: Settlement,
        populations: List<EvolutionPopulation>,
        world: LivingPlanetState,
        state: EvolutionState,
    ): EvolutionPopulation? {
        val settlementById = world.settlements.associateBy { it.id }
        return populations.asSequence()
            .filter { candidate -> settlementById[candidate.settlementId]?.civilizationId == settlement.civilizationId }
            .filter { state.lineage(it.lineageId) != null }
            .minByOrNull { candidate ->
                val source = settlementById.getValue(candidate.settlementId)
                hypot((settlement.x - source.x).toDouble(), (settlement.y - source.y).toDouble())
            }
    }

    private fun initialIsolation(settlement: Settlement, world: LivingPlanetState): Double {
        val diagonal = hypot(map.width.toDouble(), map.height.toDouble()).coerceAtLeast(1.0)
        val nearest = world.settlements.asSequence()
            .filter { it.id != settlement.id }
            .minOfOrNull { other -> hypot((settlement.x - other.x).toDouble(), (settlement.y - other.y).toDouble()) }
            ?: diagonal
        return (nearest / diagonal * 1.8).coerceIn(0.0, 1.0)
    }

    private fun founderMorphology(seed: Long, settlement: Settlement, tile: WorldTile): MorphologyProfile {
        val baseline = MorphologyProfile.HUMAN_BASELINE
        fun jitter(key: String): Double = (unit(seed, "founder:${settlement.id}:$key") - 0.5) * 0.024
        val heat = tile.temperature
        return baseline.copy(
            heightScale = (baseline.heightScale + jitter("height") + (heat - 0.5) * 0.012).coerceIn(0.50, 1.60),
            massScale = (baseline.massScale + jitter("mass") + (0.5 - heat) * 0.012).coerceIn(0.50, 1.60),
            limbScale = (baseline.limbScale + jitter("limbs")).coerceIn(0.50, 1.60),
            pigmentation = (baseline.pigmentation + jitter("pigment") + (heat - 0.5) * 0.025).coerceIn(0.0, 1.0),
            hairCoverage = (baseline.hairCoverage + jitter("hair") + (0.5 - heat) * 0.020).coerceIn(0.0, 1.0),
        )
    }

    private fun classify(divergence: Double, bodyPlan: BodyPlan): BiologicalRank = when {
        structuralDistance(bodyPlan) >= 0.30 || divergence >= 0.20 -> BiologicalRank.SPECIES
        divergence >= 0.10 -> BiologicalRank.SUBSPECIES
        divergence >= 0.045 -> BiologicalRank.MORPH
        else -> BiologicalRank.POPULATION
    }

    private fun structuralDistance(bodyPlan: BodyPlan): Double {
        var score = 0.0
        score += (bodyPlan.armPairs - 1).coerceAtLeast(0) * 0.28
        score += (bodyPlan.legPairs - 1).coerceAtLeast(0) * 0.25
        score += kotlin.math.abs(bodyPlan.eyeCount - 2) * 0.055
        score += kotlin.math.abs(bodyPlan.digitCount - 5) * 0.035
        if (bodyPlan.hasTail) score += 0.18
        if (bodyPlan.posture != Posture.UPRIGHT) score += 0.12
        if (bodyPlan.covering != SkinCovering.BARE_SKIN) score += 0.12
        return score.coerceIn(0.0, 1.0)
    }

    private fun originLineage(): PopulationLineage = PopulationLineage(
        id = ORIGIN_LINEAGE_ID,
        parentLineageId = null,
        label = "Початкова людська лінія",
        originSettlementId = null,
        formedTick = 0L,
        rank = BiologicalRank.POPULATION,
        generation = 0,
        morphology = MorphologyProfile.HUMAN_BASELINE,
        bodyPlan = BodyPlan(),
        divergenceFromOrigin = 0.0,
        tags = setOf("human_derived", "origin"),
    )

    private fun lineageId(settlementId: String, tick: Long): String = "lineage-$settlementId-$tick"

    private fun tile(settlement: Settlement): WorldTile {
        val x = settlement.x.coerceIn(0, map.width - 1)
        val y = settlement.y.coerceIn(0, map.height - 1)
        return map.tiles[y * map.width + x]
    }

    private fun bodyPlanKey(bodyPlan: BodyPlan): String = listOf(
        "arms${bodyPlan.armPairs * 2}",
        "legs${bodyPlan.legPairs * 2}",
        "eyes${bodyPlan.eyeCount}",
        "digits${bodyPlan.digitCount}",
        if (bodyPlan.hasTail) "tail" else "no-tail",
        bodyPlan.posture.name.lowercase(),
        bodyPlan.covering.name.lowercase(),
    ).joinToString("-")

    private fun unit(seed: Long, key: String): Double {
        var z = deriveSeed(seed, key)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    private fun deterministicInt(seed: Long, key: String, bound: Int): Int =
        (unit(seed, key) * bound).toInt().coerceIn(0, bound - 1)

    private fun deriveSeed(seed: Long, key: String): Long {
        var hash = seed xor -3750763034362895579L
        key.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
        return hash
    }

    companion object {
        const val ORIGIN_LINEAGE_ID = "lineage-origin-human"
    }
}
