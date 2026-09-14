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
import com.sendmefile77.chronosphere.llm.LocalLlmDiplomacyVoiceCard
import com.sendmefile77.chronosphere.llm.LocalLlmTurnNarrativeCard
import com.sendmefile77.chronosphere.llm.LocalLlmWorldAdvisorCard
import com.sendmefile77.chronosphere.people.PeopleState

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
    val ruler = peopleState.ruler(civilization.id)
    val profile = peopleState.profile(civilization.id)
    val representativeSettlement = session.state.settlements
        .filter { it.civilizationId == civilization.id }
        .maxByOrNull { it.population }
    val representativePopulation = representativeSettlement?.let { evolutionState.population(it.id) }
    val representativeLineage = representativePopulation?.let { evolutionState.lineage(it.lineageId) }
    val evolutionInterventionEngine = remember { PlayerEvolutionInterventionEngine() }
    val hybridCandidate = remember(evolutionState, representativeSettlement?.id) {
        representativeSettlement?.let { evolutionInterventionEngine.bestHybridCandidate(evolutionState, it.id) }
    }
    val hybridSettlementName = hybridCandidate?.let { candidate ->
        session.state.settlements.firstOrNull { it.id == candidate.settlementId }?.name
    }
    val briefing = GameSituation.briefing(
        state = session.state,
        civilization = civilization,
        economy = economyState,
        pendingDecisionTitle = pendingDecisionTitle,
    )
    val neighbors = briefing.neighbors
    var showEvolution by remember(civilization.id) { mutableStateOf(false) }
    var actionNonce by remember(session.state.tick) { mutableIntStateOf(0) }
    var selectedCounterpartId by remember(civilization.id, session.state.tick, civilizationCount) {
        mutableStateOf(GameSituation.defaultCounterpartId(session.state, civilization.id))
    }
    val queuedAction = remember(session.state.tick, actionNonce) { GameplayLoop.queuedAction(session.state) }
    val directActionSpent = GameplayLoop.actionSpent(session.state)
    val storyChoiceQueued = session.state.recentEvents.any { ChronicleDecisionMailbox.contains(it.id) }
    val turnReport = GameplayTurnReportStore.latestFor(civilization.id)
    val selectedCounterpart = neighbors.firstOrNull { it.civilizationId == selectedCounterpartId }
        ?: neighbors.firstOrNull()
    if (selectedCounterpart != null && selectedCounterpartId != selectedCounterpart.civilizationId) {
        selectedCounterpartId = selectedCounterpart.civilizationId
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                civilization.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                economy?.era?.displayNameUk ?: "Епоха формується",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (civilizationCount > 1) {
            OutlinedButton(onClick = onNextCivilization, enabled = !isAdvancing, shape = ChronosphereSmallShape) {
                Text("Інша держава")
            }
        }
    }

    PanelCard(accent = if (pendingDecisionTitle != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ПОТОЧНА МЕТА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
            Text(briefing.objective.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(briefing.objective.detail, style = MaterialTheme.typography.bodySmall)
            StatusPill(briefing.objective.meter, color = MaterialTheme.colorScheme.secondary)
            if (pendingDecisionTitle != null) {
                Button(onClick = onOpenChronicle, enabled = !isAdvancing, modifier = Modifier.fillMaxWidth(), shape = ChronosphereSmallShape) {
                    Text("Переглянути розвилку")
                }
            }
        }
    }

    TurnStateCard(
        state = session.state,
        civilization = civilization,
        queuedAction = queuedAction,
        directActionSpent = directActionSpent,
        storyChoiceQueued = storyChoiceQueued,
        pendingDecisionTitle = pendingDecisionTitle,
        isAdvancing = isAdvancing,
        onCancelQueued = {
            GameplayLoop.cancelQueuedAction(session.state)
            actionNonce += 1
        },
        onOpenChronicle = onOpenChronicle,
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("Населення", compactNumber(civilization.population), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
        MetricTile("Стабільність", qualityBand(civilization.stability), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
        MetricTile("Розвиток", qualityBand(civilization.technology), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
    }
    if (economy != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Казна", compactNumber(civilization.treasury), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            MetricTile("Ресурси", shortageBand(economy.shortageIndex), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            MetricTile("Торгівля", tradeBand(economy.tradeBalance), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
        }
    }

    SectionHeader(title = "Команда на хід", eyebrow = "Швидкі дії")
    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DomesticActionRow(
                session = session,
                civilization = civilization,
                hasPendingDecision = false,
                isAdvancing = isAdvancing,
                firstKind = InterventionKind.HARVEST_AID,
                firstTitle = "Резерви",
                firstEffect = "поповнити їжу",
                firstRisk = "казна зменшиться",
                secondKind = InterventionKind.TECHNOLOGY_BOOST,
                secondTitle = "Дослідження",
                secondEffect = "прискорити розвиток",
                secondRisk = "дороге вкладення",
                onQueue = ::queueAction,
            )
            DomesticActionRow(
                session = session,
                civilization = civilization,
                hasPendingDecision = false,
                isAdvancing = isAdvancing,
                firstKind = InterventionKind.STABILITY_SUPPORT,
                firstTitle = "Порядок",
                firstEffect = "підняти стабільність",
                firstRisk = "витрати з казни",
                secondKind = InterventionKind.FESTIVAL,
                secondTitle = "Свято",
                secondEffect = "стабільність + їжа",
                secondRisk = "помітні витрати",
                onQueue = ::queueAction,
            )
        }
    }

    if (neighbors.isNotEmpty()) {
        SectionHeader(title = "Дипломатія", eyebrow = "Швидка дія")
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Відносини ${String.format("%+.0f", target.relation * 100)} · ${target.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(
                            target.status,
                            color = when {
                                target.atWar -> MaterialTheme.colorScheme.error
                                target.allied -> MaterialTheme.colorScheme.secondary
                                target.relation < -0.30 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }

                    DiplomacyActions(
                        session = session,
                        civilization = civilization,
                        target = target,
                        hasPendingDecision = false,
                        isAdvancing = isAdvancing,
                        onQueue = ::queueAction,
                    )

                    LocalLlmDiplomacyVoiceCard(
                        tick = session.state.tick,
                        ownName = civilization.name,
                        target = target,
                        enabled = !isAdvancing,
                    )
                }
            }
        }
    }

    PanelCard(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ситуація", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (briefing.wars.isNotEmpty()) StatusPill("війна", color = MaterialTheme.colorScheme.error)
            }
            Text(briefing.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(briefing.pressure, style = MaterialTheme.typography.bodySmall)
            if (briefing.wars.isNotEmpty()) {
                Text("Війни · ${briefing.wars.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    LocalLlmWorldAdvisorCard(
        state = session.state,
        civilization = civilization,
        economyState = economyState,
        briefing = briefing,
        enabled = !isAdvancing,
    )

    if (turnReport != null) {
        TurnReportCard(turnReport)
        LocalLlmTurnNarrativeCard(turnReport, enabled = !isAdvancing)
    }

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("Портрет держави", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (ruler != null) {
                val dynasty = ruler.dynastyId?.let { dynastyId -> peopleState.dynasties.firstOrNull { it.id == dynastyId }?.name }
                InfoLine(
                    "Правитель",
                    "${ruler.name}, ${ruler.ageYearsAt(session.state.tick)} р.${dynasty?.let { " · $it" } ?: ""}",
                )
            }
            if (representativeLineage != null && representativePopulation != null) {
                InfoLine(
                    "Походження",
                    "${representativeLineage.label} · ${rankDisplayName(representativeLineage.rank.name)} · домішка ${String.format("%.0f%%", representativePopulation.admixture * 100.0)}",
                )
            }
            if (profile != null) {
                val culture = profile.tags.sorted().take(7).joinToString(separator = " · ", transform = ::humanizeTag)
                InfoLine("Культура", culture.ifBlank { "Без виразної домінантної традиції" })
                InfoLine("Суспільство", tensionBand(profile.socialTension))
            }
        }
    }

    if (representativeSettlement != null && representativePopulation != null && representativeLineage != null) {
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Еволюція", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Розширене керування біологічною лінією",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showEvolution = !showEvolution }) {
                        Text(if (showEvolution) "Згорнути" else "Відкрити")
                    }
                }
                if (showEvolution) {
                    val bodyPlan = representativeLineage.bodyPlan
                    val evolutionGate = GameplayLoop.evolutionGate(
                        state = session.state,
                        civilizationId = civilization.id,
                        hasPendingDecision = false,
                    )
                    val evolutionEnabled = !isAdvancing && evolutionGate.enabled
                    InfoLine(
                        "Активна лінія",
                        "${representativeLineage.label} · відхилення ${String.format("%.0f%%", representativeLineage.divergenceFromOrigin * 100.0)} · мутації ${String.format("%.0f%%", representativePopulation.mutationPressure * 100.0)}",
                    )
                    InfoLine(
                        "План тіла",
                        "рук ${bodyPlan.armPairs * 2} · ніг ${bodyPlan.legPairs * 2} · очей ${bodyPlan.eyeCount}" + if (bodyPlan.hasTail) " · хвіст" else "",
                    )
                    InfoLine(
                        "Гібридизація",
                        if (hybridCandidate != null) {
                            "${hybridCandidate.lineageLabel}${hybridSettlementName?.let { " ($it)" } ?: ""} · відмінність ${String.format("%.0f%%", hybridCandidate.difference * 100.0)}"
                        } else {
                            "Поки немає достатньо відмінної другої лінії"
                        },
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile(
                            "Розходження",
                            "велика зміна · ${evolutionGate.treasuryCost.toInt()} казни",
                            evolutionEnabled,
                            { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.DIVERGE, representativeSettlement.id) },
                            MaterialTheme.colorScheme.secondary,
                        )
                        ActionTile(
                            "Мутація",
                            "план тіла · ${evolutionGate.treasuryCost.toInt()} казни",
                            evolutionEnabled,
                            { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.MUTATE, representativeSettlement.id) },
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                    ActionTileFullWidth(
                        title = "Гібридизація",
                        subtitle = hybridCandidate?.let { "${it.lineageLabel} · ${evolutionGate.treasuryCost.toInt()} казни" }
                            ?: "потрібна відмінна друга лінія",
                        enabled = evolutionEnabled && hybridCandidate != null,
                        onClick = { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.HYBRIDIZE, representativeSettlement.id) },
                    )
                    if (!evolutionGate.enabled && evolutionGate.reasonUk != null) {
                        Text(
                            evolutionGate.reasonUk,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnStateCard(
    state: com.sendmefile77.chronosphere.civilization.LivingPlanetState,
    civilization: Civilization,
    queuedAction: PendingChronicleDecision?,
    directActionSpent: Boolean,
    storyChoiceQueued: Boolean,
    pendingDecisionTitle: String?,
    isAdvancing: Boolean,
    onCancelQueued: () -> Unit,
    onOpenChronicle: () -> Unit,
) {
    val accent = when {
        pendingDecisionTitle != null -> MaterialTheme.colorScheme.error
        queuedAction != null || storyChoiceQueued -> MaterialTheme.colorScheme.secondary
        directActionSpent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    PanelCard(accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ХІД", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                StatusPill(
                    text = when {
                        pendingDecisionTitle != null -> "потрібне рішення"
                        storyChoiceQueued -> "рішення обрано"
                        queuedAction != null -> "команду заплановано"
                        directActionSpent -> "команду використано"
                        else -> "1 команда доступна"
                    },
                    color = accent,
                )
            }
            when {
                pendingDecisionTitle != null -> {
                    Text(pendingDecisionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Розвилка з’явиться разом із вибором епохи після натискання «Хід».", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onOpenChronicle, enabled = !isAdvancing) { Text("Переглянути") }
                }
                storyChoiceQueued -> {
                    Text("Рішення Хроніки готове", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Воно застосовується разом із найближчим переходом часу.", style = MaterialTheme.typography.bodySmall)
                }
                queuedAction != null -> {
                    Text(queuedAction.option.titleUk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(queuedAction.option.effectUk, style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Заплановано на наступний хід", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        TextButton(onClick = onCancelQueued, enabled = !isAdvancing) { Text("Скасувати") }
                    }
                }
                directActionSpent -> {
                    Text("Команду цього ходу вже використано", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                else -> {
                    Text("Можна обрати одну команду або одразу перейти до ходу", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TurnReportCard(report: GameplayTurnReport) {
    PanelCard(accent = if (report.survived) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ПІДСУМКИ · ${report.yearsAdvanced} р.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text(report.headlineUk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Люди", GameplayLoop.signedLong(report.populationDelta), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                MetricTile("Порядок", GameplayLoop.signedDouble(report.stabilityDelta * 100.0, " п.п."), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                MetricTile("Розвиток", GameplayLoop.signedDouble(report.technologyDelta * 100.0, " п.п."), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Казна", GameplayLoop.signedDouble(report.treasuryDelta), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                MetricTile("Запаси", GameplayLoop.signedDouble(report.foodDelta), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            }
            if (report.warsAfter != report.warsBefore) {
                Text("Війни: ${report.warsBefore} → ${report.warsAfter}", style = MaterialTheme.typography.bodySmall)
            }
            report.highlights.take(4).forEach { highlight ->
                Text("• $highlight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DomesticActionRow(
    session: GameSession,
    civilization: Civilization,
    hasPendingDecision: Boolean,
    isAdvancing: Boolean,
    firstKind: InterventionKind,
    firstTitle: String,
    firstEffect: String,
    firstRisk: String,
    secondKind: InterventionKind,
    secondTitle: String,
    secondEffect: String,
    secondRisk: String,
    onQueue: (InterventionKind, String, String, String, String?) -> Unit,
) {
    val firstGate = GameplayLoop.gate(session.state, civilization.id, firstKind, null, hasPendingDecision)
    val secondGate = GameplayLoop.gate(session.state, civilization.id, secondKind, null, hasPendingDecision)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile(
            firstTitle,
            "$firstEffect · ${firstGate.treasuryCost.toInt()} казни",
            !isAdvancing && firstGate.enabled,
            { onQueue(firstKind, firstTitle, firstEffect, firstRisk, null) },
            if (firstKind == InterventionKind.STABILITY_SUPPORT) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        )
        ActionTile(
            secondTitle,
            "$secondEffect · ${secondGate.treasuryCost.toInt()} казни",
            !isAdvancing && secondGate.enabled,
            { onQueue(secondKind, secondTitle, secondEffect, secondRisk, null) },
            MaterialTheme.colorScheme.secondary,
        )
    }
    val reason = listOfNotNull(firstGate.reasonUk, secondGate.reasonUk).distinct().firstOrNull()
    if (reason != null && !firstGate.enabled && !secondGate.enabled) {
        Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiplomacyActions(
    session: GameSession,
    civilization: Civilization,
    target: NeighborStanding,
    hasPendingDecision: Boolean,
    isAdvancing: Boolean,
    onQueue: (InterventionKind, String, String, String, String?) -> Unit,
) {
    fun gate(kind: InterventionKind) = GameplayLoop.gate(
        state = session.state,
        civilizationId = civilization.id,
        kind = kind,
        targetCivilizationId = target.civilizationId,
        hasPendingDecision = hasPendingDecision,
    )
    val embassy = gate(InterventionKind.EMBASSY)
    val alliance = gate(InterventionKind.FORM_ALLIANCE)
    val war = gate(InterventionKind.DECLARE_WAR)
    val peace = gate(InterventionKind.MAKE_PEACE)
    val raid = gate(InterventionKind.WAR_RAID)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile(
            "Посольство",
            "відносини ↑ · ${embassy.treasuryCost.toInt()} казни",
            !isAdvancing && embassy.enabled,
            { onQueue(InterventionKind.EMBASSY, "Посольство до ${target.name}", "Покращити відносини з ${target.name}.", "Казна зменшиться; результат не гарантує союзу.", target.civilizationId) },
            MaterialTheme.colorScheme.secondary,
        )
        ActionTile(
            "Союз",
            "потрібні відносини +30 · ${alliance.treasuryCost.toInt()} казни",
            !isAdvancing && alliance.enabled,
            { onQueue(InterventionKind.FORM_ALLIANCE, "Союз із ${target.name}", "Закріпити військово-політичний союз.", "Союз коштує ресурсів і впливає на майбутні конфлікти.", target.civilizationId) },
            MaterialTheme.colorScheme.primary,
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (target.atWar) {
            ActionTile(
                "Мир",
                "завершити війну · ${peace.treasuryCost.toInt()} казни",
                !isAdvancing && peace.enabled,
                { onQueue(InterventionKind.MAKE_PEACE, "Мир із ${target.name}", "Завершити поточну війну.", "Відносини залишаться прохолодними.", target.civilizationId) },
                MaterialTheme.colorScheme.secondary,
            )
            ActionTile(
                "Набіг",
                "вдарити по запасах · ${raid.treasuryCost.toInt()} казни",
                !isAdvancing && raid.enabled,
                { onQueue(InterventionKind.WAR_RAID, "Набіг на ${target.name}", "Виснажити продовольчі запаси противника.", "Погіршить відносини і коштуватиме казни.", target.civilizationId) },
                MaterialTheme.colorScheme.error,
            )
        } else {
            ActionTile(
                "Війна",
                "відкрити фронт · ${war.treasuryCost.toInt()} казни",
                !isAdvancing && war.enabled,
                { onQueue(InterventionKind.DECLARE_WAR, "Війна з ${target.name}", "Оголосити війну і відкрити фронт.", "Стабільність і казна впадуть; конфлікт може стати довгим.", target.civilizationId) },
                MaterialTheme.colorScheme.error,
            )
            ActionTile(
                "Тиск",
                "спершу посольство або війна",
                false,
                {},
                MaterialTheme.colorScheme.primary,
            )
        }
    }
    val reasons = listOf(embassy, alliance, war, peace, raid).mapNotNull { it.reasonUk }.distinct()
    if (reasons.isNotEmpty()) {
        Text(reasons.take(2).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionTileFullWidth(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ActionTile(
            title = title,
            subtitle = subtitle,
            enabled = enabled,
            onClick = onClick,
            accent = MaterialTheme.colorScheme.secondary,
        )
    }
}

private fun humanizeTag(tag: String): String = when (tag.lowercase()) {
    "dynastic" -> "династична традиція"
    "fertility_cult" -> "культ родючості"
    "highland" -> "гірська культура"
    "sacred" -> "сакральні звичаї"
    "temperate-climate" -> "помірний клімат"
    "maritime" -> "морська культура"
    "agrarian" -> "землеробська культура"
    "urban" -> "міська культура"
    "nomadic" -> "кочова культура"
    "mercantile" -> "торгова культура"
    "warlike" -> "войовничі"
    "isolationist" -> "ізоляціоністи"
    "technological" -> "винахідники"
    "rapid_mutation" -> "швидка мутація"
    "hybrid_friendly" -> "відкриті до гібридів"
    "body_cult" -> "культ тіла"
    "matriarchal" -> "матріархальні"
    else -> tag.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}

private fun rankDisplayName(rank: String): String = when (rank.lowercase()) {
    "population" -> "популяція"
    "morph" -> "морф"
    "subspecies" -> "підвид"
    "species" -> "вид"
    else -> rank.lowercase()
}

private fun qualityBand(value: Double): String = when {
    value >= 0.82 -> "дуже висока"
    value >= 0.64 -> "висока"
    value >= 0.45 -> "середня"
    value >= 0.25 -> "низька"
    else -> "критична"
}

private fun shortageBand(value: Double): String = when {
    value < 0.08 -> "достатньо"
    value < 0.20 -> "напружено"
    value < 0.40 -> "дефіцит"
    else -> "криза"
}

private fun tradeBand(value: Double): String = when {
    value > 10.0 -> "профіцит"
    value < -10.0 -> "збиткова"
    else -> "збалансована"
}

private fun tensionBand(value: Double): String = when {
    value < 0.20 -> "спокійне"
    value < 0.45 -> "стабільне"
    value < 0.70 -> "напружене"
    else -> "на межі кризи"
}
