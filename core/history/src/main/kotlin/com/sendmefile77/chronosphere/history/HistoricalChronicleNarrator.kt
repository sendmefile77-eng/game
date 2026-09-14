package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.simulation.SimulationEvent

data class HistoricalChronicleBridge(
    val titleUk: String,
    val bodyUk: String,
    val tracesUk: List<String>,
)

/**
 * Deterministic causal prose for the Chronicle. It only summarizes facts already present in
 * simulation events / historical memory; it never invents a cause or a world-state change.
 */
object HistoricalChronicleNarrator {
    fun fromEvents(
        events: List<SimulationEvent>,
        civilizationNames: Map<String, String> = emptyMap(),
    ): HistoricalChronicleBridge? {
        val relevant = events.takeLast(96).mapNotNull { event ->
            processKind(event.code)?.let { kind -> event to kind }
        }
        if (relevant.isEmpty()) return null

        val focusId = relevant.asReversed().firstNotNullOfOrNull { (event, _) ->
            event.actorIds.firstOrNull { it in civilizationNames }
        }
        val focused = if (focusId == null) relevant else relevant.filter { (event, _) -> focusId in event.actorIds }
        val source = focused.ifEmpty { relevant }
        val grouped = source.groupBy { it.second }
        val latestKinds = grouped.entries
            .sortedByDescending { (_, values) -> values.maxOf { it.first.tick } }
            .take(3)
        if (latestKinds.isEmpty()) return null

        val civName = focusId?.let(civilizationNames::get)
            ?: source.asReversed().firstNotNullOfOrNull { (event, _) ->
                event.facts["civilization"] ?: event.facts["a"]
            }
            ?: "Ця цивілізація"

        if (focusId != null) {
            val persistent = ActiveHistoricalContextRegistry.snapshot()
                ?.historicalMemory
                ?.let { memory -> fromMemory(memory, focusId, civName) }
            if (persistent != null) return persistent
        }

        val primary = latestKinds.first()
        val firstEvent = primary.value.minWith(compareBy<Pair<SimulationEvent, HistoricalProcessKind>> { it.first.tick }.thenBy { it.first.id }).first
        val lastEvent = primary.value.maxWith(compareBy<Pair<SimulationEvent, HistoricalProcessKind>> { it.first.tick }.thenBy { it.first.id }).first
        val body = buildString {
            append("${processLabel(primary.key)} для $civName ")
            append("почався з ${eventCause(firstEvent.code)}")
            if (firstEvent.id != lastEvent.id) append(" і дійшов до ${eventResult(lastEvent.code)}")
            append(". Це не окремий запис літопису, а процес, що тягнеться крізь наступні події.")
        }
        val traces = latestKinds.map { (kind, values) ->
            val oldest = values.minBy { it.first.tick }.first
            val newest = values.maxBy { it.first.tick }.first
            val count = values.map { it.first.id }.distinct().size
            "${processLabel(kind)} · ${eventCause(oldest.code)} → ${eventResult(newest.code)} · $count фактів"
        }
        return HistoricalChronicleBridge(
            titleUk = "Чому світ став таким",
            bodyUk = body,
            tracesUk = traces,
        )
    }

    fun fromMemory(
        memory: HistoricalMemoryState,
        civilizationId: String,
        civilizationName: String,
    ): HistoricalChronicleBridge? {
        val processes = memory.activeProcessesFor(civilizationId)
            .sortedWith(compareByDescending<HistoricalProcess> { it.intensity }.thenByDescending { it.lastUpdatedTick })
        val commitments = memory.activeCommitmentsFor(civilizationId).sortedByDescending { it.originTick }
        val consequences = memory.consequences
            .filter { civilizationId in it.civilizationIds && it.status == HistoricalConsequenceStatus.OPEN }
            .sortedByDescending { it.originTick }
        val causalLinks = memory.causalLinksFor(civilizationId)
            .sortedWith(compareByDescending<HistoricalCausalLink> { it.effectTick }.thenByDescending { it.causeTick })
        val legacies = memory.legaciesFor(civilizationId)
            .sortedWith(compareByDescending<HistoricalLegacy> { it.strength }.thenByDescending { it.lastReinforcedTick })
        if (processes.isEmpty() && commitments.isEmpty() && consequences.isEmpty() && causalLinks.isEmpty() && legacies.isEmpty()) return null

        val primary = processes.firstOrNull()
        val latestCausal = causalLinks.firstOrNull()
        val strongestLegacy = legacies.firstOrNull()
        val body = when {
            latestCausal != null -> "$civilizationName живе всередині причинного ланцюга: ${latestCausal.titleUk.lowercase()}. Ця залежність уже записана в історичній пам'яті й не зникне разом з останнім повідомленням хроніки."
            primary != null -> "${primary.titleUk} формує теперішній стан $civilizationName. Стадія: ${stageLabel(primary.stage)}; сила процесу — ${intensityLabel(primary.intensity)}."
            strongestLegacy != null -> "$civilizationName зберігає історичну пам'ять: ${strongestLegacy.titleUk.lowercase()}. Її вага — ${intensityLabel(strongestLegacy.strength)}."
            commitments.isNotEmpty() -> "$civilizationName і далі живе з наслідками раніше обраного курсу: ${commitments.first().titleUk}."
            else -> "У $civilizationName залишилися незакриті наслідки попередніх подій, які ще можуть змінити наступний хід історії."
        }
        val traces = buildList {
            causalLinks.take(3).forEach { add("Причина → наслідок: ${it.titleUk}") }
            processes.take(2).forEach { add("${it.titleUk} · ${stageLabel(it.stage)}") }
            legacies.take(2).forEach { add("Пам'ять: ${it.titleUk} · ${intensityLabel(it.strength)}") }
            commitments.take(1).forEach { add("Курс: ${it.titleUk} · ціна: ${it.recurringCostUk}") }
            consequences.take(1).forEach { add("Незакритий наслідок: ${it.titleUk}") }
        }.take(7)
        return HistoricalChronicleBridge("Чому світ став таким", body, traces)
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

    private fun processLabel(kind: HistoricalProcessKind): String = when (kind) {
        HistoricalProcessKind.SETTLEMENT_EXPANSION -> "Розширення поселень"
        HistoricalProcessKind.MIGRATION -> "Міграція"
        HistoricalProcessKind.SHORTAGE -> "Криза забезпечення"
        HistoricalProcessKind.WAR -> "Воєнний цикл"
        HistoricalProcessKind.DIPLOMATIC_ALIGNMENT -> "Перебудова союзів"
        HistoricalProcessKind.TECHNOLOGICAL_TRANSITION -> "Технологічний перехід"
        HistoricalProcessKind.DYNASTIC_TRANSITION -> "Династичний перехід"
        HistoricalProcessKind.POPULATION_DIVERGENCE -> "Зміна населення"
    }

    private fun eventCause(code: String): String = when (code) {
        "SETTLEMENT_FOUNDED", "COLONY_FOUNDED" -> "заснування нового осередку"
        "SETTLEMENT_GROWTH" -> "зростання поселень"
        "MIGRATION" -> "руху населення"
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> "дефіциту ресурсів"
        "WAR_STARTED" -> "початку війни"
        "WAR_CASUALTIES" -> "воєнних втрат"
        "CITY_CAPTURED" -> "втрати або захоплення міста"
        "PEACE_TREATY" -> "мирної угоди"
        "ALLIANCE_FORMED" -> "утворення союзу"
        "ALLIANCE_DISSOLVED" -> "розпаду союзу"
        "INTERVENTION_EMBASSY" -> "дипломатичного зближення"
        "ERA_ADVANCED" -> "переходу технологічного рубежу"
        "INTERVENTION_TECH_BOOST" -> "свідомої ставки на розвиток"
        "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> "зміни влади"
        else -> "зафіксованої зміни"
    }

    private fun eventResult(code: String): String = when (code) {
        "PEACE_TREATY" -> "миру"
        "CITY_CAPTURED" -> "зміни контролю над містом"
        "WAR_CASUALTIES" -> "накопичення воєнних втрат"
        "ERA_ADVANCED" -> "нової технологічної епохи"
        "ALLIANCE_FORMED" -> "нової системи союзів"
        "ALLIANCE_DISSOLVED" -> "розриву дипломатичного порядку"
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> "гострої нестачі"
        "MIGRATION" -> "переміщення населення"
        "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> "закріплення нової влади"
        "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> "стійкої зміни населення"
        else -> "нового стану"
    }

    private fun stageLabel(stage: HistoricalProcessStage): String = when (stage) {
        HistoricalProcessStage.EMERGING -> "зароджується"
        HistoricalProcessStage.ACTIVE -> "активний"
        HistoricalProcessStage.CONSOLIDATING -> "закріплюється"
        HistoricalProcessStage.STRAINED -> "кризовий"
        HistoricalProcessStage.RESOLVED -> "завершений"
    }

    private fun intensityLabel(value: Double): String = when {
        value >= 0.75 -> "визначальна"
        value >= 0.50 -> "сильна"
        value >= 0.25 -> "помітна"
        else -> "слабка"
    }
}
