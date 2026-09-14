package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.SimulationEvent

/**
 * Replays internal politics month by month across a coarser civilization-engine step. This keeps
 * fiscal pressure, elite loyalty and rebellion timing deterministic regardless of batch size.
 */
object InternalPoliticsBatchEngine {
    fun advance(finalWorld: LivingPlanetState, fromTick: Long): LivingPlanetState {
        require(fromTick >= 0L && finalWorld.tick >= fromTick)
        if (finalWorld.tick == fromTick) return InternalPoliticsEngine.reconcile(finalWorld)

        val finalTick = finalWorld.tick
        var current = InternalPoliticsEngine.reconcile(finalWorld.copy(tick = fromTick))
        for (tick in (fromTick + 1L)..finalTick) {
            current = InternalPoliticsEngine.advance(current.copy(tick = tick))
            current = InternalSecessionEngine.advance(current)
        }
        return current.copy(tick = finalTick)
    }
}

/** Converts a sufficiently mature rebellion into an actual successor state, not a text-only event. */
internal object InternalSecessionEngine {
    fun advance(state: LivingPlanetState): LivingPlanetState {
        if (state.tick % 12L != 0L || state.civilizations.size >= MAX_CIVILIZATIONS) return state

        val candidate = state.rebellions.asSequence()
            .filter { it.isActive && it.severity >= SECESSION_THRESHOLD && state.tick - it.startedTick >= MIN_REBELLION_AGE }
            .sortedWith(compareByDescending<RebellionState> { it.severity }.thenBy { it.id })
            .firstOrNull { rebellion -> eligibleProvince(state, rebellion) != null }
            ?: return state
        val province = eligibleProvince(state, candidate) ?: return state
        val settlement = state.settlements.firstOrNull { it.id == province.settlementId } ?: return state
        val parent = state.civilizations.firstOrNull { it.id == candidate.civilizationId } ?: return state

        val chance = if (candidate.severity >= GUARANTEED_THRESHOLD) 1.0
        else ((candidate.severity - SECESSION_THRESHOLD) * 2.8 + 0.12).coerceIn(0.12, 0.58)
        val roll = hash01(state.worldSeed xor state.tick, candidate.id.hashCode(), settlement.id.hashCode())
        if (roll >= chance) return state

        val nextOrdinal = state.civilizations.asSequence()
            .mapNotNull { it.id.substringAfterLast('-').toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: (state.civilizations.size + 1)
        val successorId = "civ-$nextOrdinal"
        val successorName = successorName(settlement.name, state.civilizations.mapTo(hashSetOf()) { it.name })
        val transferredTreasury = (parent.treasury * 0.16).coerceIn(0.0, 30.0)
        val successor = Civilization(
            id = successorId,
            name = successorName,
            population = settlement.population,
            stability = (0.43 + province.autonomy * 0.18 + (1.0 - candidate.severity) * 0.08).coerceIn(0.40, 0.62),
            technology = (parent.technology * 0.93).coerceIn(0.0, 1.0),
            treasury = transferredTreasury,
            cultureTags = parent.cultureTags + setOf("successor_state", "rebellion_origin", "frontier_identity"),
        )
        val settlements = state.settlements.map { current ->
            if (current.id == settlement.id) current.copy(civilizationId = successorId) else current
        }
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != parent.id) civilization
            else civilization.copy(
                population = settlements.filter { it.civilizationId == parent.id }.sumOf { it.population },
                stability = (civilization.stability - 0.09 - candidate.severity * 0.04).coerceAtLeast(0.15),
                treasury = (civilization.treasury - transferredTreasury).coerceAtLeast(0.0),
            )
        } + successor
        val relations = state.relations + state.civilizations.map { other ->
            val value = if (other.id == parent.id) -0.72 else {
                (hash01(state.worldSeed xor state.tick, successorId.hashCode(), other.id.hashCode()) * 0.30 - 0.15)
                    .coerceIn(-1.0, 1.0)
            }
            DiplomaticRelation(successorId, other.id, value, state.tick)
        }
        val rebellions = state.rebellions.map { rebellion ->
            if (rebellion.id != candidate.id) rebellion
            else rebellion.copy(
                severity = candidate.severity,
                status = RebellionStatus.SUCCEEDED,
                lastUpdatedTick = state.tick,
                endedTick = state.tick,
            )
        }
        val provinces = state.provinces.map { current ->
            if (current.settlementId != settlement.id) current
            else current.copy(
                civilizationId = successorId,
                loyalty = 0.62,
                unrest = 0.30,
                autonomy = maxOf(current.autonomy, 0.52),
                taxBurden = TaxPolicyKind.BALANCED.rate / TaxPolicyKind.EXTRACTION.rate,
                lastUpdatedTick = state.tick,
            )
        }
        val event = SimulationEvent(
            id = "secession-${candidate.id}-${state.tick}",
            tick = state.tick,
            code = "SECESSION",
            actorIds = listOf(successorId, parent.id),
            locationId = settlement.id,
            numbers = mapOf("severity" to candidate.severity, "population" to settlement.population.toDouble()),
            facts = mapOf(
                "civilization" to successorName,
                "parent" to parent.name,
                "settlement" to settlement.name,
            ),
        )
        return InternalPoliticsEngine.reconcile(
            state.copy(
                civilizations = civilizations,
                settlements = settlements,
                relations = relations,
                provinces = provinces,
                rebellions = rebellions,
                recentEvents = (state.recentEvents + event).takeLast(96),
            ),
        )
    }

    private fun eligibleProvince(state: LivingPlanetState, rebellion: RebellionState): ProvinceState? {
        val province = state.provinces.firstOrNull {
            it.id == rebellion.provinceId && it.civilizationId == rebellion.civilizationId
        } ?: return null
        val centers = state.settlements.filter { it.civilizationId == rebellion.civilizationId }
        if (centers.size < 2) return null
        val capital = centers.minWithOrNull(
            compareBy<Settlement> { it.foundedTick }.thenByDescending { it.population }.thenBy { it.id },
        ) ?: return null
        return province.takeUnless { it.settlementId == capital.id }
    }

    private fun successorName(centerName: String, existingNames: Set<String>): String {
        val forms = listOf("Вільна земля", "Вільний союз", "Нова держава", "Співдружність")
        val start = positiveIndex(centerName.hashCode().toLong(), forms.size)
        for (offset in forms.indices) {
            val candidate = "${forms[(start + offset) % forms.size]} $centerName"
            if (candidate !in existingNames) return candidate
        }
        return "Держава $centerName ${existingNames.size + 1}"
    }

    private fun positiveIndex(value: Long, bound: Int): Int =
        ((value xor (value ushr 32)) and Long.MAX_VALUE).rem(bound.toLong()).toInt()

    private fun hash01(seed: Long, x: Int, y: Int): Double {
        var z = seed xor (x.toLong() * -7046029254386353131L) xor (y.toLong() * -4658895280553007687L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    private const val MIN_REBELLION_AGE = 24L
    private const val SECESSION_THRESHOLD = 0.82
    private const val GUARANTEED_THRESHOLD = 0.93
    private const val MAX_CIVILIZATIONS = 12
}
