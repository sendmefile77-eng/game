package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.SimulationEvent
import kotlin.math.abs

/**
 * Deterministic internal-politics layer. It derives political pressure only from authoritative
 * world state (settlements, treasury, stability, wars and tax policy) and writes bounded effects
 * back into civilization stability/treasury plus explicit chronicle events.
 */
object InternalPoliticsEngine {
    fun reconcile(state: LivingPlanetState): LivingPlanetState {
        val civilizationIds = state.civilizations.mapTo(hashSetOf()) { it.id }
        val settlementById = state.settlements.associateBy { it.id }

        val oldPolicies = state.taxPolicies.associateBy { it.civilizationId }
        val policies = state.civilizations.map { civilization ->
            oldPolicies[civilization.id]
                ?.takeIf { it.civilizationId in civilizationIds }
                ?: CivilizationTaxPolicy(civilization.id, TaxPolicyKind.BALANCED, state.tick)
        }

        val oldFactions = state.eliteFactions.associateBy { it.id }
        val factions = buildList {
            state.civilizations.forEach { civilization ->
                EliteFactionKind.entries.forEach { kind ->
                    val id = eliteId(civilization.id, kind)
                    add(oldFactions[id] ?: initialElite(state, civilization, kind))
                }
            }
        }

        val oldProvinces = state.provinces.associateBy { it.settlementId }
        val provinces = state.settlements.map { settlement ->
            val owner = settlement.civilizationId
            val old = oldProvinces[settlement.id]
            if (old != null) {
                old.copy(
                    civilizationId = owner,
                    lastUpdatedTick = minOf(old.lastUpdatedTick, state.tick),
                )
            } else {
                initialProvince(state, settlement)
            }
        }

        val validProvinceIds = provinces.mapTo(hashSetOf()) { it.id }
        val rebellions = state.rebellions.mapNotNull { rebellion ->
            if (rebellion.provinceId !in validProvinceIds) return@mapNotNull null
            val province = provinces.first { it.id == rebellion.provinceId }
            when {
                !rebellion.isActive -> rebellion
                province.civilizationId == rebellion.civilizationId -> rebellion
                else -> rebellion.copy(
                    status = RebellionStatus.SUPPRESSED,
                    endedTick = state.tick,
                    lastUpdatedTick = state.tick,
                )
            }
        }.sortedBy { it.startedTick }.takeLast(96)

        return state.copy(
            taxPolicies = policies,
            eliteFactions = factions,
            provinces = provinces,
            rebellions = rebellions,
        )
    }

    fun advance(state: LivingPlanetState): LivingPlanetState {
        var current = reconcile(state)
        val monthly = applyMonthlyFiscalPressure(current)
        current = current.copy(civilizations = monthly)
        if (current.tick % 12L != 0L) return current

        val events = mutableListOf<SimulationEvent>()
        val policies = updateTaxPolicies(current, events)
        current = current.copy(taxPolicies = policies)
        val factions = updateElites(current)
        current = current.copy(eliteFactions = factions)
        val provinces = updateProvinces(current, events)
        current = current.copy(provinces = provinces)
        val rebellions = updateRebellions(current, events)
        current = current.copy(rebellions = rebellions)
        val civilizations = applyAnnualPoliticalPressure(current)

        return current.copy(
            civilizations = civilizations,
            recentEvents = (current.recentEvents + events).takeLast(96),
        )
    }

    private fun applyMonthlyFiscalPressure(state: LivingPlanetState): List<Civilization> {
        val wealthByCivilization = state.settlements.groupBy { it.civilizationId }
            .mapValues { (_, settlements) -> settlements.sumOf { it.wealth } }
        return state.civilizations.map { civilization ->
            val policy = state.taxPolicyFor(civilization.id) ?: CivilizationTaxPolicy(civilization.id)
            val provinces = state.provincesFor(civilization.id)
            val compliance = if (provinces.isEmpty()) 1.0 else provinces
                .map { (1.0 - it.unrest * 0.72).coerceIn(0.18, 1.0) }
                .average()
            val grossTax = (wealthByCivilization[civilization.id] ?: 0.0) * policy.rate * 0.0018 * compliance
            val rebellionCost = state.activeRebellionsFor(civilization.id).sumOf { 0.10 + it.severity * 0.28 }
            val maxUnrest = provinces.maxOfOrNull { it.unrest } ?: 0.0
            val rebellionPressure = state.activeRebellionsFor(civilization.id).maxOfOrNull { it.severity } ?: 0.0
            val stabilityDelta = -maxUnrest * 0.00030 - rebellionPressure * 0.00070 +
                if (policy.kind == TaxPolicyKind.RELIEF) 0.00010 else 0.0
            civilization.copy(
                treasury = (civilization.treasury + grossTax - rebellionCost).coerceAtLeast(0.0),
                stability = (civilization.stability + stabilityDelta).coerceIn(0.15, 0.95),
            )
        }
    }

    private fun updateTaxPolicies(
        state: LivingPlanetState,
        events: MutableList<SimulationEvent>,
    ): List<CivilizationTaxPolicy> {
        val old = state.taxPolicies.associateBy { it.civilizationId }
        return state.civilizations.map { civilization ->
            val current = old[civilization.id] ?: CivilizationTaxPolicy(civilization.id)
            if (state.tick - current.changedTick < 36L) return@map current
            val activeRebellion = state.activeRebellionsFor(civilization.id).isNotEmpty()
            val target = when {
                activeRebellion || civilization.stability < 0.36 -> TaxPolicyKind.RELIEF
                civilization.treasury < 18.0 && civilization.stability > 0.62 -> TaxPolicyKind.EXTRACTION
                civilization.treasury < 42.0 && civilization.stability > 0.50 -> TaxPolicyKind.HIGH
                civilization.treasury > 170.0 && civilization.stability < 0.58 -> TaxPolicyKind.RELIEF
                else -> TaxPolicyKind.BALANCED
            }
            if (target == current.kind) return@map current
            val code = if (target.rate > current.rate) "TAXES_RAISED" else "TAXES_LOWERED"
            events += SimulationEvent(
                id = "tax-${civilization.id}-${state.tick}",
                tick = state.tick,
                code = code,
                actorIds = listOf(civilization.id),
                numbers = mapOf("oldRate" to current.rate, "newRate" to target.rate),
                facts = mapOf("from" to current.kind.name, "to" to target.name),
            )
            CivilizationTaxPolicy(civilization.id, target, state.tick)
        }
    }

    private fun updateElites(state: LivingPlanetState): List<EliteFactionState> {
        val civilizations = state.civilizations.associateBy { it.id }
        return state.eliteFactions.map { faction ->
            val civilization = civilizations[faction.civilizationId] ?: return@map faction
            val policy = state.taxPolicyFor(civilization.id)?.kind ?: TaxPolicyKind.BALANCED
            val atWar = state.wars.any { it.civilizationA == civilization.id || it.civilizationB == civilization.id }
            val policyAffinity = when (faction.kind) {
                EliteFactionKind.LANDHOLDERS -> when (policy) {
                    TaxPolicyKind.RELIEF -> 0.06
                    TaxPolicyKind.BALANCED -> 0.03
                    TaxPolicyKind.HIGH -> -0.04
                    TaxPolicyKind.EXTRACTION -> -0.12
                }
                EliteFactionKind.MERCHANTS -> when (policy) {
                    TaxPolicyKind.RELIEF -> 0.10
                    TaxPolicyKind.BALANCED -> 0.04
                    TaxPolicyKind.HIGH -> -0.07
                    TaxPolicyKind.EXTRACTION -> -0.16
                }
                EliteFactionKind.MILITARY -> if (atWar) 0.10 else 0.01
                EliteFactionKind.BUREAUCRACY -> when (policy) {
                    TaxPolicyKind.RELIEF -> -0.04
                    TaxPolicyKind.BALANCED -> 0.04
                    TaxPolicyKind.HIGH -> 0.08
                    TaxPolicyKind.EXTRACTION -> 0.05
                }
            }
            val treasuryEffect = when {
                civilization.treasury < 12.0 -> -0.12
                civilization.treasury > 110.0 -> 0.05
                else -> 0.0
            }
            val target = (0.24 + civilization.stability * 0.62 + policyAffinity + treasuryEffect).coerceIn(0.05, 0.96)
            val noise = (hash01(state.worldSeed xor state.tick, faction.id.hashCode(), state.tick.toInt()) - 0.5) * 0.025
            faction.copy(
                loyalty = (faction.loyalty * 0.76 + target * 0.24 + noise).coerceIn(0.0, 1.0),
                influence = (faction.influence + influenceDrift(faction.kind, state, civilization.id)).coerceIn(0.08, 0.72),
                lastUpdatedTick = state.tick,
            )
        }
    }

    private fun updateProvinces(
        state: LivingPlanetState,
        events: MutableList<SimulationEvent>,
    ): List<ProvinceState> {
        val civilizations = state.civilizations.associateBy { it.id }
        val settlementsByCivilization = state.settlements.groupBy { it.civilizationId }
        val factionsByCivilization = state.eliteFactions.groupBy { it.civilizationId }
        val old = state.provinces.associateBy { it.settlementId }
        return state.settlements.map { settlement ->
            val civilization = civilizations[settlement.civilizationId] ?: return@map old[settlement.id] ?: initialProvince(state, settlement)
            val centers = settlementsByCivilization[civilization.id].orEmpty()
            val capital = centers.minWithOrNull(compareBy<Settlement> { it.foundedTick }.thenByDescending { it.population }.thenBy { it.id })
                ?: settlement
            val previous = old[settlement.id] ?: initialProvince(state, settlement)
            val policy = state.taxPolicyFor(civilization.id) ?: CivilizationTaxPolicy(civilization.id)
            val distancePressure = (manhattan(settlement, capital) / 36.0).coerceIn(0.0, 0.48)
            val foodRatio = settlement.foodStock / settlement.population.coerceAtLeast(1L)
            val foodPressure = ((0.18 - foodRatio) / 0.18).coerceIn(0.0, 1.0)
            val warPressure = if (state.wars.any { it.civilizationA == civilization.id || it.civilizationB == civilization.id }) 0.14 else 0.0
            val eliteGroups = factionsByCivilization[civilization.id].orEmpty()
            val eliteLoyalty = weightedEliteLoyalty(eliteGroups)
            val taxPressure = (policy.rate / TaxPolicyKind.EXTRACTION.rate).coerceIn(0.0, 1.0)
            val targetUnrest = (
                (1.0 - civilization.stability) * 0.34 +
                    distancePressure * 0.24 +
                    foodPressure * 0.24 +
                    taxPressure * 0.20 +
                    warPressure +
                    (1.0 - eliteLoyalty) * 0.16 -
                    previous.autonomy * 0.20
                ).coerceIn(0.0, 1.0)
            val noise = (hash01(state.worldSeed xor state.tick, settlement.id.hashCode(), civilization.id.hashCode()) - 0.5) * 0.035
            val unrest = (previous.unrest * 0.68 + targetUnrest * 0.32 + noise).coerceIn(0.0, 1.0)
            val loyaltyTarget = (1.0 - unrest * 0.72 - distancePressure * 0.10 + civilization.stability * 0.18).coerceIn(0.0, 1.0)
            val loyalty = (previous.loyalty * 0.72 + loyaltyTarget * 0.28).coerceIn(0.0, 1.0)
            val updated = previous.copy(
                civilizationId = civilization.id,
                loyalty = loyalty,
                unrest = unrest,
                taxBurden = taxPressure,
                lastUpdatedTick = state.tick,
            )
            if (previous.unrest < UNREST_EVENT_THRESHOLD && updated.unrest >= UNREST_EVENT_THRESHOLD) {
                events += SimulationEvent(
                    id = "unrest-${settlement.id}-${state.tick}",
                    tick = state.tick,
                    code = "PROVINCIAL_UNREST",
                    actorIds = listOf(civilization.id),
                    locationId = settlement.id,
                    numbers = mapOf("unrest" to updated.unrest, "loyalty" to updated.loyalty, "taxRate" to policy.rate),
                    facts = mapOf("settlement" to settlement.name),
                )
            }
            updated
        }
    }

    private fun updateRebellions(
        state: LivingPlanetState,
        events: MutableList<SimulationEvent>,
    ): List<RebellionState> {
        val provinces = state.provinces.associateBy { it.id }
        val settlements = state.settlements.associateBy { it.id }
        val result = state.rebellions.toMutableList()
        val activeByProvince = result.filter { it.isActive }.associateBy { it.provinceId }.toMutableMap()

        result.indices.forEach { index ->
            val rebellion = result[index]
            if (!rebellion.isActive) return@forEach
            val province = provinces[rebellion.provinceId]
            if (province == null || province.civilizationId != rebellion.civilizationId) {
                result[index] = rebellion.copy(
                    status = RebellionStatus.SUPPRESSED,
                    endedTick = state.tick,
                    lastUpdatedTick = state.tick,
                )
                activeByProvince.remove(rebellion.provinceId)
                return@forEach
            }
            val age = state.tick - rebellion.startedTick
            val elitePressure = 1.0 - weightedEliteLoyalty(state.elitesFor(rebellion.civilizationId))
            val targetSeverity = (province.unrest * 0.78 + elitePressure * 0.22).coerceIn(0.0, 1.0)
            val severity = (rebellion.severity * 0.68 + targetSeverity * 0.32).coerceIn(0.0, 1.0)
            if (age >= 24L && province.unrest < 0.38 && severity < 0.50) {
                val settlement = settlements[province.settlementId]
                result[index] = rebellion.copy(
                    severity = severity,
                    status = RebellionStatus.SUPPRESSED,
                    endedTick = state.tick,
                    lastUpdatedTick = state.tick,
                )
                activeByProvince.remove(rebellion.provinceId)
                events += SimulationEvent(
                    id = "rebellion-suppressed-${rebellion.id}-${state.tick}",
                    tick = state.tick,
                    code = "REBELLION_SUPPRESSED",
                    actorIds = listOf(rebellion.civilizationId),
                    locationId = province.settlementId,
                    facts = mapOf("settlement" to (settlement?.name ?: province.settlementId)),
                )
            } else {
                result[index] = rebellion.copy(severity = severity, lastUpdatedTick = state.tick)
            }
        }

        state.provinces.sortedByDescending { it.unrest }.forEach { province ->
            if (province.unrest < REBELLION_THRESHOLD || province.id in activeByProvince) return@forEach
            val centers = state.settlements.filter { it.civilizationId == province.civilizationId }
            if (centers.size < 2) return@forEach
            val capital = centers.minWithOrNull(compareBy<Settlement> { it.foundedTick }.thenByDescending { it.population }.thenBy { it.id }) ?: return@forEach
            if (province.settlementId == capital.id) return@forEach
            val chance = ((province.unrest - REBELLION_THRESHOLD) * 1.8 + 0.10).coerceIn(0.10, 0.44)
            val roll = hash01(state.worldSeed xor state.tick, province.id.hashCode(), province.civilizationId.hashCode())
            if (roll >= chance) return@forEach
            val rebellion = RebellionState(
                id = "rebellion-${province.settlementId}-${state.tick}",
                civilizationId = province.civilizationId,
                provinceId = province.id,
                startedTick = state.tick,
                lastUpdatedTick = state.tick,
                severity = province.unrest,
            )
            result += rebellion
            activeByProvince[province.id] = rebellion
            val settlement = settlements[province.settlementId]
            events += SimulationEvent(
                id = "rebellion-start-${rebellion.id}",
                tick = state.tick,
                code = "REBELLION_STARTED",
                actorIds = listOf(province.civilizationId),
                locationId = province.settlementId,
                numbers = mapOf("severity" to rebellion.severity, "unrest" to province.unrest),
                facts = mapOf("settlement" to (settlement?.name ?: province.settlementId)),
            )
        }

        return result.sortedBy { it.startedTick }.takeLast(96)
    }

    private fun applyAnnualPoliticalPressure(state: LivingPlanetState): List<Civilization> = state.civilizations.map { civilization ->
        val provinces = state.provincesFor(civilization.id)
        val provinceLoyalty = provinces.map { it.loyalty }.averageOr(0.70)
        val eliteLoyalty = weightedEliteLoyalty(state.elitesFor(civilization.id))
        val rebellion = state.activeRebellionsFor(civilization.id).maxOfOrNull { it.severity } ?: 0.0
        val cohesion = provinceLoyalty * 0.55 + eliteLoyalty * 0.45
        val delta = ((cohesion - 0.58) * 0.018 - rebellion * 0.028).coerceIn(-0.032, 0.014)
        civilization.copy(stability = (civilization.stability + delta).coerceIn(0.15, 0.95))
    }

    private fun initialElite(
        state: LivingPlanetState,
        civilization: Civilization,
        kind: EliteFactionKind,
    ): EliteFactionState {
        val seed = civilization.id.hashCode() xor (kind.ordinal * 7919)
        val influence = (0.16 + hash01(state.worldSeed + 211L, seed, kind.ordinal) * 0.24).coerceIn(0.08, 0.72)
        val loyalty = (0.46 + civilization.stability * 0.34 + (hash01(state.worldSeed + 499L, seed, kind.ordinal) - 0.5) * 0.16)
            .coerceIn(0.18, 0.94)
        return EliteFactionState(
            id = eliteId(civilization.id, kind),
            civilizationId = civilization.id,
            kind = kind,
            influence = influence,
            loyalty = loyalty,
            lastUpdatedTick = state.tick,
        )
    }

    private fun initialProvince(state: LivingPlanetState, settlement: Settlement): ProvinceState {
        val centers = state.settlements.filter { it.civilizationId == settlement.civilizationId }
        val capital = centers.minWithOrNull(compareBy<Settlement> { it.foundedTick }.thenByDescending { it.population }.thenBy { it.id })
            ?: settlement
        val distance = manhattan(settlement, capital)
        val autonomy = (0.08 + (distance / 42.0).coerceIn(0.0, 0.34) + hash01(state.worldSeed + 733L, settlement.id.hashCode(), settlement.civilizationId.hashCode()) * 0.10)
            .coerceIn(0.05, 0.52)
        return ProvinceState(
            id = provinceId(settlement.id),
            civilizationId = settlement.civilizationId,
            settlementId = settlement.id,
            loyalty = 0.68,
            unrest = 0.18,
            autonomy = autonomy,
            taxBurden = TaxPolicyKind.BALANCED.rate / TaxPolicyKind.EXTRACTION.rate,
            lastUpdatedTick = state.tick,
        )
    }

    private fun weightedEliteLoyalty(factions: List<EliteFactionState>): Double {
        if (factions.isEmpty()) return 0.70
        val totalInfluence = factions.sumOf { it.influence }.takeIf { it > 0.0 } ?: return 0.70
        return factions.sumOf { it.loyalty * it.influence } / totalInfluence
    }

    private fun influenceDrift(kind: EliteFactionKind, state: LivingPlanetState, civilizationId: String): Double {
        val cityCount = state.settlements.count { it.civilizationId == civilizationId }
        val atWar = state.wars.any { it.civilizationA == civilizationId || it.civilizationB == civilizationId }
        return when (kind) {
            EliteFactionKind.LANDHOLDERS -> if (cityCount >= 4) 0.002 else -0.001
            EliteFactionKind.MERCHANTS -> if (cityCount >= 3) 0.002 else 0.0
            EliteFactionKind.MILITARY -> if (atWar) 0.006 else -0.001
            EliteFactionKind.BUREAUCRACY -> if (cityCount >= 5) 0.003 else 0.001
        }
    }

    private fun eliteId(civilizationId: String, kind: EliteFactionKind): String =
        "elite:$civilizationId:${kind.name.lowercase()}"

    private fun provinceId(settlementId: String): String = "province:$settlementId"

    private fun manhattan(a: Settlement, b: Settlement): Int = abs(a.x - b.x) + abs(a.y - b.y)

    private fun Iterable<Double>.averageOr(default: Double): Double {
        val values = toList()
        return if (values.isEmpty()) default else values.average()
    }

    private fun hash01(seed: Long, x: Int, y: Int): Double {
        var z = seed xor (x.toLong() * -7046029254386353131L) xor (y.toLong() * -4658895280553007687L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    private const val UNREST_EVENT_THRESHOLD = 0.62
    private const val REBELLION_THRESHOLD = 0.74
}
