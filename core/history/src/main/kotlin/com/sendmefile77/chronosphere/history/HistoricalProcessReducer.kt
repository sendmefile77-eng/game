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
        "SETTLEMENT_FOUNDED", "COLONY_FOUNDED", "SETTLEMENT_GROWTH", "STATE_FOUNDED" -> HistoricalProcessKind.SETTLEMENT_EXPANSION
        "MIGRATION" -> HistoricalProcessKind.MIGRATION
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> HistoricalProcessKind.SHORTAGE
        "WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED", "PEACE_TREATY" -> HistoricalProcessKind.WAR
        "ALLIANCE_FORMED", "ALLIANCE_DISSOLVED", "ALLIANCE_ENDED", "INTERVENTION_EMBASSY" -> HistoricalProcessKind.DIPLOMATIC_ALIGNMENT
        "ERA_ADVANCED", "INTERVENTION_TECH_BOOST" -> HistoricalProcessKind.TECHNOLOGICAL_TRANSITION
        "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> HistoricalProcessKind.DYNASTIC_TRANSITION
        "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> HistoricalProcessKind.POPULATION_DIVERGENCE
        "TAXES_RAISED", "TAXES_LOWERED", "INTERVENTION_TAX_RAISE", "INTERVENTION_TAX_LOWER",
        "PROVINCIAL_UNREST", "REBELLION_STARTED", "REBELLION_SUPPRESSED", "SECESSION" -> HistoricalProcessKind.INTERNAL_CRISIS
        "INTERVENTION_INSTITUTION_REFORM" -> HistoricalProcessKind.INSTITUTIONAL_TRANSITION
        else -> null
    }

    private fun stageFor(code: String): HistoricalProcessStage = when (code) {
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE", "WAR_CASUALTIES", "CITY_CAPTURED", "PROVINCIAL_UNREST", "REBELLION_STARTED" -> HistoricalProcessStage.STRAINED
        "PEACE_TREATY", "ALLIANCE_DISSOLVED", "ALLIANCE_ENDED", "REBELLION_SUPPRESSED", "SECESSION" -> HistoricalProcessStage.RESOLVED
        "ERA_ADVANCED", "RULER_SUCCEEDED", "DYNASTY_FOUNDED", "TAXES_RAISED", "TAXES_LOWERED",
        "INTERVENTION_TAX_RAISE", "INTERVENTION_TAX_LOWER", "INTERVENTION_INSTITUTION_REFORM",
        "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> HistoricalProcessStage.EMERGING
        else -> HistoricalProcessStage.ACTIVE
    }

    private fun initialIntensity(code: String): Double = when (code) {
        "REBELLION_STARTED" -> 0.80
        "WAR_STARTED", "CITY_CAPTURED", "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE", "PROVINCIAL_UNREST" -> 0.68
        "ERA_ADVANCED", "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED" -> 0.55
        "INTERVENTION_INSTITUTION_REFORM" -> 0.52
        else -> 0.42
    }

    private fun intensityDelta(code: String): Double = when (code) {
        "REBELLION_STARTED" -> 0.18
        "WAR_CASUALTIES", "CITY_CAPTURED", "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE", "PROVINCIAL_UNREST" -> 0.12
        "PEACE_TREATY", "ALLIANCE_DISSOLVED", "ALLIANCE_ENDED", "REBELLION_SUPPRESSED", "SECESSION" -> -0.24
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
            HistoricalProcessKind.INTERNAL_CRISIS -> "Внутрішня політична криза · $actor"
            HistoricalProcessKind.INSTITUTIONAL_TRANSITION -> "Інституційна перебудова · $actor"
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
            "PROVINCIAL_UNREST", "REBELLION_STARTED" -> "Тривала внутрішня політична напруга" to "стихає після відновлення лояльності провінцій і завершення повстань"
            "SECESSION" -> "Розкол держави та нова політична межа" to "стає історичною нормою лише після тривалого нового порядку"
            "INTERVENTION_INSTITUTION_REFORM" -> "Перебудова державних інститутів" to "закріплюється після тривалого періоду роботи нових правил"
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

    fun applyEventToCausalLinks(
        existing: List<HistoricalCausalLink>,
        event: SimulationEvent,
        civilizationIds: Set<String>,
        recentEvents: List<SimulationEvent>,
    ): List<HistoricalCausalLink> {
        if (existing.any { it.effectEventId == event.id }) return existing
        val cause = recentEvents.asSequence()
            .filter { candidate -> candidate.id != event.id && candidate.tick <= event.tick }
            .filter { candidate -> event.tick - candidate.tick <= causalWindow(candidate.code, event.code) }
            .filter { candidate -> sharesCivilization(candidate, civilizationIds) }
            .mapNotNull { candidate -> causalDefinition(candidate.code, event.code)?.let { candidate to it } }
            .sortedWith(compareBy<Pair<SimulationEvent, Pair<HistoricalCausalRelation, String>>> { it.first.tick }.thenBy { it.first.id })
            .lastOrNull()
            ?: return existing
        val causeEvent = cause.first
        val definition = cause.second
        val link = HistoricalCausalLink(
            id = "causal:${causeEvent.id}->${event.id}",
            civilizationIds = civilizationIds,
            causeEventId = causeEvent.id,
            effectEventId = event.id,
            causeTick = causeEvent.tick,
            effectTick = event.tick,
            relation = definition.first,
            titleUk = definition.second,
        )
        return (existing + link).takeLast(192)
    }

    private fun sharesCivilization(event: SimulationEvent, civilizationIds: Set<String>): Boolean =
        event.actorIds.any { it in civilizationIds } ||
            event.facts["targetCivilizationId"]?.let { it in civilizationIds } == true

    private fun causalWindow(causeCode: String, effectCode: String): Long = when {
        effectCode == "PEACE_TREATY" || effectCode == "SECESSION" -> 1200L
        causeCode in setOf("FOOD_SHORTAGE", "ECONOMIC_SHORTAGE", "TAXES_RAISED", "INTERVENTION_TAX_RAISE") -> 360L
        else -> 720L
    }

    private fun causalDefinition(
        causeCode: String,
        effectCode: String,
    ): Pair<HistoricalCausalRelation, String>? = when {
        causeCode == "WAR_STARTED" && effectCode == "WAR_CASUALTIES" ->
            HistoricalCausalRelation.ESCALATION to "Початок війни спричинив цикл воєнних втрат"
        causeCode in setOf("WAR_STARTED", "WAR_CASUALTIES") && effectCode == "CITY_CAPTURED" ->
            HistoricalCausalRelation.ESCALATION to "Воєнний тиск завершився зміною контролю над містом"
        causeCode in setOf("WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED") && effectCode == "PEACE_TREATY" ->
            HistoricalCausalRelation.RESOLUTION to "Воєнний цикл привів до мирної угоди"
        causeCode in setOf("FOOD_SHORTAGE", "ECONOMIC_SHORTAGE") && effectCode == "MIGRATION" ->
            HistoricalCausalRelation.DISPLACEMENT to "Дефіцит став поштовхом до переміщення населення"
        causeCode == "FOOD_SHORTAGE" && effectCode == "ECONOMIC_SHORTAGE" ->
            HistoricalCausalRelation.PRESSURE to "Продовольча нестача переросла в ширший економічний дефіцит"
        causeCode == "ECONOMIC_SHORTAGE" && effectCode == "FOOD_SHORTAGE" ->
            HistoricalCausalRelation.PRESSURE to "Економічний дефіцит посилив продовольчу нестачу"
        causeCode == "MIGRATION" && effectCode in setOf("SETTLEMENT_FOUNDED", "COLONY_FOUNDED") ->
            HistoricalCausalRelation.TRANSITION to "Міграційний рух закріпився заснуванням нового осередку"
        causeCode == "RULER_SUCCEEDED" && effectCode == "DYNASTY_FOUNDED" ->
            HistoricalCausalRelation.TRANSITION to "Зміна правителя закріпила нову династичну лінію"
        causeCode in setOf("TAXES_RAISED", "INTERVENTION_TAX_RAISE") && effectCode == "PROVINCIAL_UNREST" ->
            HistoricalCausalRelation.PRESSURE to "Зростання податкового тиску підштовхнуло провінцію до невдоволення"
        causeCode in setOf("FOOD_SHORTAGE", "ECONOMIC_SHORTAGE", "WAR_CASUALTIES") && effectCode == "PROVINCIAL_UNREST" ->
            HistoricalCausalRelation.PRESSURE to "Матеріальна або воєнна криза переросла у провінційне невдоволення"
        causeCode in setOf("PROVINCIAL_UNREST", "TAXES_RAISED", "INTERVENTION_TAX_RAISE") && effectCode == "REBELLION_STARTED" ->
            HistoricalCausalRelation.ESCALATION to "Накопичена внутрішня напруга переросла у відкрите повстання"
        causeCode == "REBELLION_STARTED" && effectCode == "REBELLION_SUPPRESSED" ->
            HistoricalCausalRelation.RESOLUTION to "Повстання завершилося відновленням контролю центру"
        causeCode == "REBELLION_STARTED" && effectCode == "SECESSION" ->
            HistoricalCausalRelation.TRANSITION to "Повстання завершилося політичним відокремленням"
        else -> null
    }

    fun applyEventToLegacies(
        existing: List<HistoricalLegacy>,
        event: SimulationEvent,
        civilizationIds: Set<String>,
    ): List<HistoricalLegacy> {
        val definition = legacyDefinition(event.code) ?: return existing
        val stableCivilizationIds = civilizationIds.toSortedSet()
        val id = "legacy:${definition.first.name.lowercase()}:${stableCivilizationIds.joinToString("+")}"
        val old = existing.firstOrNull { it.id == id }
        val updated = if (old == null) {
            HistoricalLegacy(
                id = id,
                civilizationIds = stableCivilizationIds,
                kind = definition.first,
                titleUk = definition.second,
                originTick = event.tick,
                lastReinforcedTick = event.tick,
                strength = definition.third,
                sourceEventIds = listOf(event.id),
            )
        } else {
            old.copy(
                titleUk = definition.second,
                lastReinforcedTick = event.tick,
                strength = (old.strength + legacyReinforcement(event.code)).coerceIn(0.0, 1.0),
                sourceEventIds = (old.sourceEventIds + event.id).distinct().takeLast(16),
            )
        }
        return (existing.filterNot { it.id == id } + updated)
            .sortedBy { it.lastReinforcedTick }
            .takeLast(96)
    }

    private fun legacyDefinition(code: String): Triple<HistoricalLegacyKind, String, Double>? = when (code) {
        "WAR_STARTED", "WAR_CASUALTIES", "PEACE_TREATY" -> Triple(
            HistoricalLegacyKind.WAR_MEMORY,
            "Пам'ять про війну та її ціну",
            0.48,
        )
        "CITY_CAPTURED" -> Triple(
            HistoricalLegacyKind.TERRITORIAL_MEMORY,
            "Пам'ять про втрату й зміну контролю над землею",
            0.68,
        )
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> Triple(
            HistoricalLegacyKind.SCARCITY_MEMORY,
            "Пам'ять про дефіцит і вразливість постачання",
            0.50,
        )
        "MIGRATION" -> Triple(
            HistoricalLegacyKind.MIGRATION_MEMORY,
            "Пам'ять про велике переміщення населення",
            0.44,
        )
        "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> Triple(
            HistoricalLegacyKind.DYNASTIC_MEMORY,
            "Пам'ять про зміну влади та династичну тяглість",
            0.42,
        )
        "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> Triple(
            HistoricalLegacyKind.POPULATION_MEMORY,
            "Пам'ять про зміну походження та вигляду населення",
            0.64,
        )
        "ERA_ADVANCED" -> Triple(
            HistoricalLegacyKind.TECHNOLOGICAL_MEMORY,
            "Пам'ять про технологічний перелом",
            0.54,
        )
        "PROVINCIAL_UNREST", "REBELLION_STARTED", "REBELLION_SUPPRESSED", "SECESSION" -> Triple(
            HistoricalLegacyKind.REBELLION_MEMORY,
            "Пам'ять про внутрішній конфлікт між центром і провінціями",
            0.62,
        )
        "INTERVENTION_INSTITUTION_REFORM" -> Triple(
            HistoricalLegacyKind.INSTITUTIONAL_MEMORY,
            "Пам'ять про перебудову державних інститутів",
            0.52,
        )
        else -> null
    }

    private fun legacyReinforcement(code: String): Double = when (code) {
        "CITY_CAPTURED", "WAR_CASUALTIES", "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE", "REBELLION_STARTED", "SECESSION" -> 0.14
        else -> 0.09
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
            HistoricalProcessKind.INTERNAL_CRISIS -> {
                val stillUnstable = primary?.let { id ->
                    world.rebellions.any { it.civilizationId == id && it.isActive } ||
                        world.provinces.any { it.civilizationId == id && it.unrest >= 0.62 }
                } ?: false
                when {
                    !stillUnstable && age >= 12L -> process.resolve(world.tick)
                    age >= 60L && process.stage == HistoricalProcessStage.STRAINED -> process.copy(stage = HistoricalProcessStage.CONSOLIDATING)
                    else -> process
                }
            }
            HistoricalProcessKind.INSTITUTIONAL_TRANSITION -> when {
                age >= 480L -> process.resolve(world.tick)
                age >= 120L && process.stage == HistoricalProcessStage.EMERGING -> process.copy(stage = HistoricalProcessStage.CONSOLIDATING)
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
            consequence.titleUk.contains("внутрішня", ignoreCase = true) -> {
                val stable = consequence.civilizationIds.all { id ->
                    world.rebellions.none { it.civilizationId == id && it.isActive } &&
                        world.provinces.none { it.civilizationId == id && it.unrest >= 0.45 }
                }
                stable && world.tick - consequence.originTick >= 120L
            }
            consequence.titleUk.contains("розкол", ignoreCase = true) -> world.tick - consequence.originTick >= 600L
            consequence.titleUk.contains("інститут", ignoreCase = true) -> !processStillActive || world.tick - consequence.originTick >= 480L
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
