package com.sendmefile77.chronosphere.civilization

enum class InstitutionKind(val titleUk: String) {
    COUNCIL("Рада і представництво"),
    ADMINISTRATION("Адміністрація"),
    COURTS("Суд і право"),
    MILITARY_COMMAND("Військове командування"),
}

data class InstitutionState(
    val id: String,
    val civilizationId: String,
    val kind: InstitutionKind,
    val capacity: Double,
    val legitimacy: Double,
    val lastUpdatedTick: Long,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(capacity.isFinite() && capacity in 0.0..1.0)
        require(legitimacy.isFinite() && legitimacy in 0.0..1.0)
        require(lastUpdatedTick >= 0L)
    }
}

fun LivingPlanetState.institutionsFor(civilizationId: String): List<InstitutionState> =
    institutions.filter { it.civilizationId == civilizationId }

fun LivingPlanetState.institutionFor(civilizationId: String, kind: InstitutionKind): InstitutionState? =
    institutions.firstOrNull { it.civilizationId == civilizationId && it.kind == kind }

fun LivingPlanetState.institutionCapacity(civilizationId: String, kind: InstitutionKind): Double =
    institutionFor(civilizationId, kind)?.capacity ?: 0.0

fun LivingPlanetState.institutionLegitimacy(civilizationId: String, kind: InstitutionKind): Double =
    institutionFor(civilizationId, kind)?.legitimacy ?: 0.0

fun LivingPlanetState.institutionStrength(civilizationId: String): Double {
    val values = institutionsFor(civilizationId)
    if (values.isEmpty()) return 0.0
    return values.map { it.capacity * 0.62 + it.legitimacy * 0.38 }.average().coerceIn(0.0, 1.0)
}

object InstitutionEngine {
    fun reconcile(state: LivingPlanetState): LivingPlanetState {
        val civilizationIds = state.civilizations.mapTo(hashSetOf()) { it.id }
        val old = state.institutions.associateBy { it.id }
        val institutions = buildList {
            state.civilizations.forEach { civilization ->
                InstitutionKind.entries.forEach { kind ->
                    val id = institutionId(civilization.id, kind)
                    add(old[id]?.takeIf { it.civilizationId in civilizationIds } ?: initialInstitution(state, civilization, kind))
                }
            }
        }
        return state.copy(institutions = institutions)
    }

    fun advanceAnnual(state: LivingPlanetState): LivingPlanetState {
        var current = reconcile(state)
        if (current.tick % 12L != 0L) return current
        val civilizations = current.civilizations.associateBy { it.id }
        val settlementsByCivilization = current.settlements.groupBy { it.civilizationId }
        val updated = current.institutions.map { institution ->
            val civilization = civilizations[institution.civilizationId] ?: return@map institution
            val settlements = settlementsByCivilization[civilization.id].orEmpty()
            val urbanScale = (settlements.size / 7.0).coerceIn(0.0, 1.0)
            val treasuryScale = (civilization.treasury / 180.0).coerceIn(0.0, 1.0)
            val war = current.wars.any { it.civilizationA == civilization.id || it.civilizationB == civilization.id }
            val rebellion = current.activeRebellionsFor(civilization.id).maxOfOrNull { it.severity } ?: 0.0
            val targetCapacity = when (institution.kind) {
                InstitutionKind.COUNCIL -> 0.14 + civilization.technology * 0.30 + urbanScale * 0.20 + civilization.stability * 0.12
                InstitutionKind.ADMINISTRATION -> 0.12 + civilization.technology * 0.36 + urbanScale * 0.24 + treasuryScale * 0.14
                InstitutionKind.COURTS -> 0.12 + civilization.technology * 0.28 + civilization.stability * 0.24 + urbanScale * 0.12
                InstitutionKind.MILITARY_COMMAND -> 0.14 + civilization.technology * 0.30 + if (war) 0.24 else 0.08
            }.coerceIn(0.05, 0.96)
            val targetLegitimacy = when (institution.kind) {
                InstitutionKind.COUNCIL -> civilization.stability * 0.62 + (1.0 - rebellion) * 0.22 + 0.10
                InstitutionKind.ADMINISTRATION -> civilization.stability * 0.54 + treasuryScale * 0.18 + 0.16
                InstitutionKind.COURTS -> civilization.stability * 0.58 + (1.0 - rebellion) * 0.26 + 0.10
                InstitutionKind.MILITARY_COMMAND -> civilization.stability * 0.44 + if (war) 0.28 else 0.18
            }.coerceIn(0.05, 0.96)
            val noise = (hash01(current.worldSeed xor current.tick, institution.id.hashCode(), institution.kind.ordinal) - 0.5) * 0.018
            institution.copy(
                capacity = (institution.capacity * 0.82 + targetCapacity * 0.18 + noise).coerceIn(0.0, 1.0),
                legitimacy = (institution.legitimacy * 0.82 + targetLegitimacy * 0.18 + noise * 0.5).coerceIn(0.0, 1.0),
                lastUpdatedTick = current.tick,
            )
        }
        current = current.copy(institutions = updated)
        return applyInstitutionEffects(current)
    }

    fun reform(state: LivingPlanetState, civilizationId: String, strength: Double): LivingPlanetState {
        val reconciled = reconcile(state)
        val candidates = reconciled.institutionsFor(civilizationId)
        if (candidates.isEmpty()) return reconciled
        val target = candidates.minWithOrNull(
            compareBy<InstitutionState> { it.capacity * 0.65 + it.legitimacy * 0.35 }.thenBy { it.kind.ordinal },
        ) ?: return reconciled
        val gain = 0.07 + strength.coerceIn(0.0, 1.0) * 0.13
        val institutions = reconciled.institutions.map { institution ->
            if (institution.id != target.id) institution
            else institution.copy(
                capacity = (institution.capacity + gain).coerceAtMost(1.0),
                legitimacy = (institution.legitimacy + gain * 0.65).coerceAtMost(1.0),
                lastUpdatedTick = reconciled.tick,
            )
        }
        return reconciled.copy(institutions = institutions)
    }

    private fun applyInstitutionEffects(state: LivingPlanetState): LivingPlanetState {
        val civilizations = state.civilizations.map { civilization ->
            val council = state.institutionFor(civilization.id, InstitutionKind.COUNCIL)
            val courts = state.institutionFor(civilization.id, InstitutionKind.COURTS)
            val administration = state.institutionFor(civilization.id, InstitutionKind.ADMINISTRATION)
            val legitimacy = listOfNotNull(council, courts, administration).map { it.legitimacy }.averageOr(0.45)
            val capacity = listOfNotNull(council, courts, administration).map { it.capacity }.averageOr(0.35)
            val stabilityDelta = ((legitimacy - 0.50) * 0.009 + (capacity - 0.45) * 0.004).coerceIn(-0.010, 0.010)
            civilization.copy(stability = (civilization.stability + stabilityDelta).coerceIn(0.15, 0.95))
        }
        return state.copy(civilizations = civilizations)
    }

    private fun initialInstitution(
        state: LivingPlanetState,
        civilization: Civilization,
        kind: InstitutionKind,
    ): InstitutionState {
        val base = when (kind) {
            InstitutionKind.COUNCIL -> 0.19
            InstitutionKind.ADMINISTRATION -> 0.16
            InstitutionKind.COURTS -> 0.15
            InstitutionKind.MILITARY_COMMAND -> 0.20
        }
        val variation = (hash01(state.worldSeed + 1201L, civilization.id.hashCode(), kind.ordinal) - 0.5) * 0.10
        val capacity = (base + civilization.technology * 0.34 + variation).coerceIn(0.08, 0.82)
        val legitimacy = (0.24 + civilization.stability * 0.55 + variation * 0.5).coerceIn(0.12, 0.90)
        return InstitutionState(
            id = institutionId(civilization.id, kind),
            civilizationId = civilization.id,
            kind = kind,
            capacity = capacity,
            legitimacy = legitimacy,
            lastUpdatedTick = state.tick,
        )
    }

    private fun institutionId(civilizationId: String, kind: InstitutionKind): String =
        "institution:$civilizationId:${kind.name.lowercase()}"

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
}
