package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.simulation.SimulationEvent

internal object HistoricalProcessReducer {
    fun applyEventToProcesses(
        existing: List<HistoricalProcess>,
        event: SimulationEvent,
        civilizationIds: Set<String>,
        world: LivingPlanetState,
    ): List<HistoricalProcess> {
        val kind = processKind(event.code) ?: return existing
        val stage = stageFor(event.code)
        val activeMatch = existing.lastOrNull { process ->
            process.isActive && process.kind == kind &&
                when (kind) {
                    HistoricalProcessKind.WAR, HistoricalProcessKind.DIPLOMATIC_ALIGNMENT -> process.civilizationIds == civilizationIds
                    else -> process.civilizationIds.firstOrNull() == civilizationIds.firstOrNull()
                }
        }
        val title = processTitle(kind, civilizationIds, world)
        if (activeMatch == null) {
            val created = HistoricalProcess(
                id = "process:${kind.name.lowercase()}:${event.id}",
                kind = kind,
                civilizationIds = civilizationIds,
                titleUk = title,
                startedTick = event.tick,
                lastUpdatedTick = event.tick,
                stage = stage,
                intensity = initialIntensity(event.code),
                sourceEventIds = listOf(event.id),
                latestEventCode = event.code,
                resolvedTick = if (stage == HistoricalProcessStage.RESOLVED) event.tick else null,
            )
            return existing + created
        }
        return existing.map { process ->
            if (process.id != activeMatch.id) process
            else process.copy(
                titleUk = title,
                lastUpdatedTick = event.tick,
                stage = stage,
                intensity = (process.intensity + intensityDelta(event.code)).coerceIn(0.0, 1.0),
                sourceEventIds = (process.sourceEventIds + event.id).distinct().takeLast(16),
                latestEventCode = event.code,
                resolvedTick = if (stage == HistoricalProcessStage.RESOLVED) event.tick else null,
            )
        }
    }

    private fun processKind(code: String): HistoricalProcessKind? = when (code) {
        "SETTLEMENT_FOUNDED", "COLONY_FOUNDED", "SETTLEMENT_GROWTH" -> HistoricalProcessKind.SETTLEMENT_EXPANSION
        "MIGRATION" -> HistoricalProcessKind.MIGRATION
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> HistoricalProcessKind.SHORTAGE
        "WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED", "PEACE_TREATY" -> HistoricalProcessKind.WAR
        "ALLIANCE_FORMED", "ALLIANCE_DISSOLVED", "INTERVENTION_EMBASSY" -> HistoricalProcessKind.DIPLOMATIC_ALIGNMENT
        "ERA_ADVANCED", "INTERVENTION_TECH_BOOST" -> HistoricalProcessKind.TECHNOLOGICAL_TRANSITION
        "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> HistoricalProcessKind.DYNASTIC_TRANSITION
        "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> HistoricalProcessKind.POPULATION_DIVERGENCE
        else -> null
    }

    private fun stageFor(code: String): HistoricalProcessStage = when (code) {
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE", "WAR_CASUALTIES", "CITY_CAPTURED" -> HistoricalProcessStage.STRAINED
        "PEACE_TREATY", "ALLIANCE_DISSOLVED" -> HistoricalProcessStage.RESOLVED
        "ERA_ADVANCED", "RULER_SUCCEEDED", "DYNASTY_FOUNDED",
        "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> HistoricalProcessStage.EMERGING
        else -> HistoricalProcessStage.ACTIVE
    }

    private fun initialIntensity(code: String): Double = when (code) {
        "WAR_STARTED", "CITY_CAPTURED", "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> 0.68
        "ERA_ADVANCED", "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED" -> 0.55
        else -> 0.42
    }

    private fun intensityDelta(code: String): Double = when (code) {
        "WAR_CASUALTIES", "CITY_CAPTURED", "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> 0.12
        "PEACE_TREATY", "ALLIANCE_DISSOLVED" -> -0.24
        else -> 0.05
    }

    private fun processTitle(
        kind: HistoricalProcessKind,
        civilizationIds: Set<String>,
        world: LivingPlanetState,
    ): String {
        val names = civilizationIds.mapNotNull { id -> world.civilizations.firstOrNull { it.id == id }?.name }
        val actor = names.joinToString(" — ").ifBlank { "невідома держава" }
        return when (kind) {
            HistoricalProcessKind.SETTLEMENT_EXPANSION -> "Розширення поселень · $actor"
            HistoricalProcessKind.MIGRATION -> "Міграційний рух · $actor"
            HistoricalProcessKind.SHORTAGE -> "Криза забезпечення · $actor"
            HistoricalProcessKind.WAR -> "Воєнний цикл · $actor"
            HistoricalProcessKind.DIPLOMATIC_ALIGNMENT -> "Перебудова союзів · $actor"
            HistoricalProcessKind.TECHNOLOGICAL_TRANSITION -> "Технологічний перехід · $actor"
            HistoricalProcessKind.DYNASTIC_TRANSITION -> "Перехід влади · $actor"
            HistoricalProcessKind.POPULATION_DIVERGENCE -> "Зміна популяційної лінії · $actor"
        }
    }

    fun applyEventToConsequences(
        existing: List<HistoricalConsequence>,
        event: SimulationEvent,
        civilizationIds: Set<String>,
    ): List<HistoricalConsequence> {
        val definition = when (event.code) {
            "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> "Тривалий тиск дефіциту" to "зникає, коли дефіцит економіки стабільно спадає"
            "WAR_STARTED", "WAR_CASUALTIES" -> "Порушення торгівлі та мобілізаційний тиск" to "зникає після завершення війни"
            "CITY_CAPTURED" -> "Перерозподіл території та населення" to "стихає після тривалого мирного періоду"
            "ERA_ADVANCED" -> "Адаптація до нової епохи" to "стихає після періоду консолідації"
            "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
            "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" ->
                "Наслідки нової популяційної лінії" to "стають нормою лише після тривалої інтеграції"
            else -> null
        } ?: return existing
        val id = "consequence:${event.id}"
        if (existing.any { it.id == id }) return existing
        return existing + HistoricalConsequence(
            id = id,
            civilizationIds = civilizationIds,
            originEventId = event.id,
            originTick = event.tick,
            titleUk = definition.first,
            triggerUk = definition.second,
        )
    }

    fun ageProcesses(
        processes: List<HistoricalProcess>,
        world: LivingPlanetState,
        economy: EconomyState?,
    ): List<HistoricalProcess> = processes.map { process ->
        if (!process.isActive) return@map process
        val age = world.tick - process.lastUpdatedTick
        val primary = process.civilizationIds.firstOrNull()
        when (process.kind) {
            HistoricalProcessKind.WAR -> {
                val activeWar = world.wars.any { war ->
                    setOf(war.civilizationA, war.civilizationB) == process.civilizationIds
                }
                if (!activeWar && age >= 1L) process.resolve(world.tick) else process
            }
            HistoricalProcessKind.DIPLOMATIC_ALIGNMENT -> {
                if (process.civilizationIds.size < 2) process
                else {
                    val activeAlliance = world.alliances.any { alliance ->
                        setOf(alliance.civilizationA, alliance.civilizationB) == process.civilizationIds
                    }
                    if (!activeAlliance && age >= 120L) process.resolve(world.tick) else process
                }
            }
            HistoricalProcessKind.SHORTAGE -> {
                val shortage = primary?.let { economy?.economy(it)?.shortageIndex }
                if (shortage != null && shortage < 0.08 && age >= 12L) process.resolve(world.tick)
                else if (age >= 60L && process.stage == HistoricalProcessStage.STRAINED) process.copy(stage = HistoricalProcessStage.CONSOLIDATING)
                else process
            }
            HistoricalProcessKind.TECHNOLOGICAL_TRANSITION -> when {
                age >= 360L -> process.resolve(world.tick)
                age >= 120L && process.stage == HistoricalProcessStage.EMERGING -> process.copy(stage = HistoricalProcessStage.CONSOLIDATING)
                else -> process
            }
            HistoricalProcessKind.DYNASTIC_TRANSITION -> when {
                age >= 240L -> process.resolve(world.tick)
                age >= 60L && process.stage == HistoricalProcessStage.EMERGING -> process.copy(stage = HistoricalProcessStage.CONSOLIDATING)
                else -> process
            }
            HistoricalProcessKind.SETTLEMENT_EXPANSION, HistoricalProcessKind.MIGRATION -> when {
                age >= 600L -> process.resolve(world.tick)
                age >= 240L -> process.copy(stage = HistoricalProcessStage.CONSOLIDATING)
                else -> process
            }
            HistoricalProcessKind.POPULATION_DIVERGENCE -> when {
                age >= 1200L -> process.resolve(world.tick)
                age >= 480L -> process.copy(stage = HistoricalProcessStage.CONSOLIDATING)
                else -> process
            }
        }
    }

    private fun HistoricalProcess.resolve(tick: Long): HistoricalProcess = copy(
        stage = HistoricalProcessStage.RESOLVED,
        intensity = (intensity * 0.45).coerceIn(0.0, 1.0),
        resolvedTick = tick,
    )

    fun resolveConsequences(
        consequences: List<HistoricalConsequence>,
        world: LivingPlanetState,
        economy: EconomyState?,
        processes: List<HistoricalProcess>,
    ): List<HistoricalConsequence> = consequences.map { consequence ->
        if (consequence.status == HistoricalConsequenceStatus.RESOLVED) return@map consequence
        val processStillActive = processes.any { process ->
            process.isActive && process.sourceEventIds.contains(consequence.originEventId)
        }
        val civShortage = consequence.civilizationIds.maxOfOrNull { id -> economy?.economy(id)?.shortageIndex ?: 0.0 } ?: 0.0
        val oldEnough = world.tick - consequence.originTick >= 240L
        val resolve = when {
            consequence.titleUk.contains("дефіциту", ignoreCase = true) -> civShortage < 0.08 && oldEnough
            consequence.titleUk.contains("торгівлі", ignoreCase = true) -> !processStillActive
            consequence.titleUk.contains("території", ignoreCase = true) -> oldEnough && world.wars.none { war ->
                war.civilizationA in consequence.civilizationIds || war.civilizationB in consequence.civilizationIds
            }
            consequence.titleUk.contains("епохи", ignoreCase = true) -> !processStillActive || world.tick - consequence.originTick >= 360L
            consequence.titleUk.contains("популяційної", ignoreCase = true) -> !processStillActive || world.tick - consequence.originTick >= 1200L
            else -> false
        }
        if (resolve) consequence.copy(status = HistoricalConsequenceStatus.RESOLVED, resolvedTick = world.tick) else consequence
    }

    fun trimProcesses(processes: List<HistoricalProcess>): List<HistoricalProcess> {
        if (processes.size <= 160) return processes
        val activeIds = processes.filter { it.isActive }.mapTo(hashSetOf()) { it.id }
        val newestResolved = processes.filterNot { it.isActive }.sortedByDescending { it.lastUpdatedTick }.take(160 - activeIds.size.coerceAtMost(160))
        return (processes.filter { it.id in activeIds } + newestResolved)
            .sortedBy { it.startedTick }
            .takeLast(160)
    }
}
