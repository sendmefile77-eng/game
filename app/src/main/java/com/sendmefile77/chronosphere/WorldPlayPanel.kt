package com.sendmefile77.chronosphere

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.PlayerEvolutionInterventionEngine
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.llm.LocalLlmWorldAdvisorCard
import com.sendmefile77.chronosphere.people.PeopleState

/**
 * The World screen is the play surface, not an administration dashboard.
 *
 * One glance should answer three questions: what is happening, what shape is the state in, and
 * what can I do before the next 100-year turn. Secondary systems stay collapsed or contextual.
 */
@Composable
internal fun WorldPlayPanel(
    civilization: Civilization,
    civilizationCount: Int,
    economyState: EconomyState,
    peopleState: PeopleState,
    evolutionState: EvolutionState,
    session: GameSession,
    pendingDecisionTitle: String?,
    isAdvancing: Boolean,
    onOpenChronicle: () -> Unit,
    onNextCivilization: () -> Unit,
    onEvolutionIntervene: (PlayerEvolutionInterventionEngine.Kind, String) -> Unit,
) {
    val economy = economyState.economy(civilization.id)
    val briefing = GameSituation.briefing(
        state = session.state,
        civilization = civilization,
        economy = economyState,
        pendingDecisionTitle = pendingDecisionTitle,
    )
    val neighbors = briefing.neighbors
    val ruler = peopleState.ruler(civilization.id)
    val societyProfile = peopleState.profile(civilization.id)
    var actionNonce by remember(session.state.tick) { mutableIntStateOf(0) }
    var showDiplomacy by remember(civilization.id) { mutableStateOf(false) }
    var showEvolution by remember(civilization.id) { mutableStateOf(false) }
    var showDetails by remember(civilization.id) { mutableStateOf(false) }
    var selectedCounterpartId by remember(civilization.id, session.state.tick, civilizationCount) {
        mutableStateOf(GameSituation.defaultCounterpartId(session.state, civilization.id))
    }
    val queuedAction = remember(session.state.tick, actionNonce) { GameplayLoop.queuedAction(session.state) }
    val selectedCounterpart = neighbors.firstOrNull { it.civilizationId == selectedCounterpartId }
        ?: neighbors.firstOrNull()

    val representativeSettlement = session.state.settlements
        .filter { it.civilizationId == civilization.id }
        .maxByOrNull { it.population }
    val representativePopulation = representativeSettlement?.let { evolutionState.population(it.id) }
    val representativeLineage = representativePopulation?.let { evolutionState.lineage(it.lineageId) }
    val evolutionEngine = remember { PlayerEvolutionInterventionEngine() }
    val hybridCandidate = remember(evolutionState, representativeSettlement?.id) {
        representativeSettlement?.let { evolutionEngine.bestHybridCandidate(evolutionState, it.id) }
    }

    fun queueAction(
        kind: InterventionKind,
        title: String,
        effect: String,
        risk: String,
        counterpartId: String? = null,
    ) {
        GameplayLoop.queueAction(
            state = session.state,
            civilizationId = civilization.id,
            kind = kind,
            titleUk = title,
            effectUk = effect,
            riskUk = risk,
            counterpartCivilizationId = counterpartId,
        )
        actionNonce += 1
    }

    PanelCard(accent = if (pendingDecisionTitle != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (pendingDecisionTitle == null) "ЦЕ СТОЛІТТЯ" else "ПОТРІБНЕ РІШЕННЯ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        pendingDecisionTitle ?: briefing.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (civilizationCount > 1) {
                    TextButton(onClick = onNextCivilization, enabled = !isAdvancing) { Text("Інша") }
                }
            }
            Text(
                if (pendingDecisionTitle == null) briefing.pressure else briefing.objective.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingDecisionTitle != null) {
                Button(
                    onClick = onOpenChronicle,
                    enabled = !isAdvancing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ChronosphereSmallShape,
                ) {
                    Text("Відкрити подію")
                }
            } else {
                Text(
                    "Мета · ${briefing.objective.title} · ${briefing.objective.meter}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("Населення", compactNumber(civilization.population), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
        MetricTile("Стабільність", qualityBand(civilization.stability), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
        MetricTile("Казна", compactNumber(civilization.treasury), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
    }
    Text(
        buildString {
            append(economy?.era?.displayNameUk ?: "Епоха формується")
            if (economy != null) {
                append(" · ресурси ").append(shortageBand(economy.shortageIndex))
                append(" · торгівля ").append(tradeBand(economy.tradeBalance))
            }
            if (briefing.wars.isNotEmpty()) append(" · війна")
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (briefing.wars.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (queuedAction != null) {
        PanelCard(accent = MaterialTheme.colorScheme.secondary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ПЕРЕД ХОДОМ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
                    Text(queuedAction.option.titleUk, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(queuedAction.option.effectUk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = {
                        GameplayLoop.cancelQueuedAction(session.state)
                        actionNonce += 1
                    },
                    enabled = !isAdvancing,
                ) { Text("Скасувати") }
            }
        }
    } else {
        PanelCard(accent = MaterialTheme.colorScheme.secondary) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ОДНА КОМАНДА ДО ХОДУ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
                Text(
                    "За потреби підштовхніть державу в одному напрямі. Головні історичні рішення приймаються у самому ході.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CompactCommandButton(
                        label = "Запаси",
                        gate = GameplayLoop.gate(session.state, civilization.id, InterventionKind.HARVEST_AID, null, false),
                        enabled = !isAdvancing,
                        modifier = Modifier.weight(1f),
                    ) {
                        queueAction(InterventionKind.HARVEST_AID, "Поповнити запаси", "Додаткове продовольство перед століттям.", "Частина казни піде на резерви.")
                    }
                    CompactCommandButton(
                        label = "Розвиток",
                        gate = GameplayLoop.gate(session.state, civilization.id, InterventionKind.TECHNOLOGY_BOOST, null, false),
                        enabled = !isAdvancing,
                        modifier = Modifier.weight(1f),
                    ) {
                        queueAction(InterventionKind.TECHNOLOGY_BOOST, "Прискорити розвиток", "Ремесла й знання отримають додатковий імпульс.", "Це дороге вкладення.")
                    }
                    CompactCommandButton(
                        label = "Порядок",
                        gate = GameplayLoop.gate(session.state, civilization.id, InterventionKind.STABILITY_SUPPORT, null, false),
                        enabled = !isAdvancing,
                        modifier = Modifier.weight(1f),
                    ) {
                        queueAction(InterventionKind.STABILITY_SUPPORT, "Укріпити порядок", "Підтримати стабільність перед наступним століттям.", "Потрібні кошти й політичний ресурс.")
                    }
                }
            }
        }
    }

    if (neighbors.isNotEmpty()) {
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Дипломатія", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            selectedCounterpart?.let { "${it.name} · ${it.status}" } ?: "Сусідні держави",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showDiplomacy = !showDiplomacy }, enabled = !isAdvancing) {
                        Text(if (showDiplomacy) "Згорнути" else "Відкрити")
                    }
                }
                if (showDiplomacy) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        neighbors.forEach { neighbor ->
                            FilterChip(
                                selected = neighbor.civilizationId == selectedCounterpart?.civilizationId,
                                onClick = { selectedCounterpartId = neighbor.civilizationId },
                                label = { Text(neighbor.name) },
                            )
                        }
                    }
                    selectedCounterpart?.let { target ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (target.atWar) {
                                CompactCommandButton(
                                    label = "Мир",
                                    gate = GameplayLoop.gate(session.state, civilization.id, InterventionKind.MAKE_PEACE, target.civilizationId, false),
                                    enabled = !isAdvancing,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    queueAction(InterventionKind.MAKE_PEACE, "Запропонувати мир", "Спробувати завершити війну з ${target.name}.", "Супротивник може не прийняти умови.", target.civilizationId)
                                }
                                CompactCommandButton(
                                    label = "Набіг",
                                    gate = GameplayLoop.gate(session.state, civilization.id, InterventionKind.WAR_RAID, target.civilizationId, false),
                                    enabled = !isAdvancing,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    queueAction(InterventionKind.WAR_RAID, "Набіг на ${target.name}", "Посилити військовий тиск.", "Можливі втрати й виснаження.", target.civilizationId)
                                }
                            } else {
                                CompactCommandButton(
                                    label = "Посольство",
                                    gate = GameplayLoop.gate(session.state, civilization.id, InterventionKind.EMBASSY, target.civilizationId, false),
                                    enabled = !isAdvancing,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    queueAction(InterventionKind.EMBASSY, "Посольство до ${target.name}", "Покращити відносини й відкрити дипломатичний канал.", "Потребує коштів і часу.", target.civilizationId)
                                }
                                val secondKind = if (target.relation >= 0.30 && !target.allied) InterventionKind.FORM_ALLIANCE else InterventionKind.DECLARE_WAR
                                CompactCommandButton(
                                    label = if (secondKind == InterventionKind.FORM_ALLIANCE) "Союз" else "Війна",
                                    gate = GameplayLoop.gate(session.state, civilization.id, secondKind, target.civilizationId, false),
                                    enabled = !isAdvancing,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (secondKind == InterventionKind.FORM_ALLIANCE) {
                                        queueAction(secondKind, "Союз із ${target.name}", "Закріпити дружні відносини формальним союзом.", "Союз створює нові зобов’язання.", target.civilizationId)
                                    } else {
                                        queueAction(secondKind, "Війна з ${target.name}", "Розпочати відкритий конфлікт.", "Війна може дорого коштувати обом сторонам.", target.civilizationId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (representativeSettlement != null && representativeLineage != null && representativePopulation != null) {
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Біологічна лінія", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${representativeLineage.label} · відхилення ${String.format("%.0f%%", representativeLineage.divergenceFromOrigin * 100.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showEvolution = !showEvolution }, enabled = !isAdvancing) {
                        Text(if (showEvolution) "Згорнути" else "Втрутитися")
                    }
                }
                if (showEvolution) {
                    val gate = GameplayLoop.evolutionGate(session.state, civilization.id, false)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.DIVERGE, representativeSettlement.id) },
                            enabled = !isAdvancing && gate.enabled,
                            modifier = Modifier.weight(1f),
                            shape = ChronosphereSmallShape,
                        ) { Text("Розходження") }
                        OutlinedButton(
                            onClick = { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.MUTATE, representativeSettlement.id) },
                            enabled = !isAdvancing && gate.enabled,
                            modifier = Modifier.weight(1f),
                            shape = ChronosphereSmallShape,
                        ) { Text("Мутація") }
                        OutlinedButton(
                            onClick = { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.HYBRIDIZE, representativeSettlement.id) },
                            enabled = !isAdvancing && gate.enabled && hybridCandidate != null,
                            modifier = Modifier.weight(1f),
                            shape = ChronosphereSmallShape,
                        ) { Text("Гібрид") }
                    }
                    if (!gate.enabled && !gate.reasonUk.isNullOrBlank()) {
                        Text(gate.reasonUk, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Деталі держави", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Правитель · культура · устрій · Qwen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Згорнути" else "Відкрити")
                }
            }

            if (showDetails) {
                ruler?.let {
                    InfoLine("Правитель", "${it.name} · ${it.ageYearsAt(session.state.tick)} р.")
                }
                val cultureTags = (civilization.cultureTags + societyProfile?.tags.orEmpty())
                    .asSequence()
                    .filterNot { it.startsWith("era-choice:") || it.startsWith("foundation:") || it.startsWith("policy:") || it.startsWith("hist:") }
                    .distinct()
                    .take(8)
                    .map(::displayTag)
                    .toList()
                InfoLine(
                    "Культура",
                    cultureTags.joinToString(" · ").ifBlank { "Власна традиція ще формується" },
                )
                societyProfile?.let {
                    InfoLine("Соціальна напруга", String.format("%.0f%%", it.socialTension.coerceIn(0.0, 1.0) * 100.0))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Розвиток", qualityBand(civilization.technology), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                    if (economy != null) {
                        MetricTile("Ресурси", shortageBand(economy.shortageIndex), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                        MetricTile("Торгівля", tradeBand(economy.tradeBalance), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                    }
                }
                if (representativeLineage != null) {
                    val body = representativeLineage.bodyPlan
                    InfoLine(
                        "Біологія",
                        buildString {
                            append(representativeLineage.label)
                            append(" · рук ").append(body.armPairs * 2)
                            append(" · ніг ").append(body.legPairs * 2)
                            append(" · очей ").append(body.eyeCount)
                            if (body.hasTail) append(" · хвіст")
                        },
                    )
                }

                StateInstitutionPanel(
                    state = session.state,
                    civilization = civilization,
                    hasPendingDecision = pendingDecisionTitle != null,
                    isAdvancing = isAdvancing,
                    onQueue = ::queueAction,
                )

                LocalLlmWorldAdvisorCard(
                    state = session.state,
                    civilization = civilization,
                    economyState = economyState,
                    briefing = briefing,
                    enabled = !isAdvancing,
                )
            }
        }
    }
}

@Composable
private fun CompactCommandButton(
    label: String,
    gate: GameplayActionGate,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && gate.enabled,
        modifier = modifier,
        shape = ChronosphereSmallShape,
    ) {
        Text(label, maxLines = 1)
    }
}

private fun displayTag(value: String): String = value
    .substringAfter(':', value)
    .replace('_', ' ')
    .replace('-', ' ')

private fun qualityBand(value: Double): String = when {
    value >= 0.78 -> "висока"
    value >= 0.58 -> "добра"
    value >= 0.38 -> "середня"
    value >= 0.20 -> "низька"
    else -> "критична"
}

private fun shortageBand(value: Double): String = when {
    value <= 0.12 -> "стабільні"
    value <= 0.28 -> "напружені"
    value <= 0.48 -> "дефіцит"
    else -> "криза"
}

private fun tradeBand(value: Double): String = when {
    value > 12.0 -> "сильна"
    value > 2.0 -> "плюс"
    value < -12.0 -> "провал"
    value < -2.0 -> "мінус"
    else -> "рівна"
}
