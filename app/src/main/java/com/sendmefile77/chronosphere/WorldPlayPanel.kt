package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    onIntervene: (InterventionKind) -> Unit,
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
    var showEvolution by remember(civilization.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ОБРАНА ДЕРЖАВА",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(civilization.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                economy?.era?.displayNameUk ?: "Епоха формується",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (civilizationCount > 1) {
            OutlinedButton(onClick = onNextCivilization, enabled = !isAdvancing, shape = ChronosphereSmallShape) {
                Text("Наступна")
            }
        }
    }

    PanelCard(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Що відбувається", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (briefing.wars.isNotEmpty()) StatusPill("війна", color = MaterialTheme.colorScheme.error)
            }
            Text(briefing.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(briefing.pressure, style = MaterialTheme.typography.bodyMedium)
            Text(briefing.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (briefing.wars.isNotEmpty()) {
                Text("Війни · ${briefing.wars.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (briefing.allies.isNotEmpty()) {
                Text("Союзники · ${briefing.allies.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    PanelCard(accent = if (pendingDecisionTitle != null) MaterialTheme.colorScheme.secondary else null) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ПОТОЧНА МЕТА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            Text(briefing.objective.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(briefing.objective.detail, style = MaterialTheme.typography.bodySmall)
            StatusPill(briefing.objective.meter, color = MaterialTheme.colorScheme.secondary)
            if (pendingDecisionTitle != null) {
                Text(
                    pendingDecisionTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenChronicle, enabled = !isAdvancing, modifier = Modifier.fillMaxWidth(), shape = ChronosphereSmallShape) {
                    Text("Прийняти рішення у хроніці")
                }
            }
        }
    }

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

    SectionHeader(title = "Втручання", eyebrow = "Ваші дії")
    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Оберіть дію для ${civilization.name}. Наслідки проявляться після руху часу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Врожай", "їжа ↑ · запас міцності", !isAdvancing, { onIntervene(InterventionKind.HARVEST_AID) }, MaterialTheme.colorScheme.secondary)
                ActionTile("Посуха", "їжа ↓ · населення під тиском", !isAdvancing, { onIntervene(InterventionKind.DROUGHT) }, MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Прорив", "технологічний розвиток ↑", !isAdvancing, { onIntervene(InterventionKind.TECHNOLOGY_BOOST) }, MaterialTheme.colorScheme.primary)
                ActionTile("Порядок", "стабільність ↑", !isAdvancing, { onIntervene(InterventionKind.STABILITY_SUPPORT) }, MaterialTheme.colorScheme.secondary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Набіг", "удар по сусіду або ворогу", !isAdvancing, { onIntervene(InterventionKind.WAR_RAID) }, MaterialTheme.colorScheme.error)
                ActionTile("Свято", "стабільність ↑ · казна ↓", !isAdvancing, { onIntervene(InterventionKind.FESTIVAL) }, MaterialTheme.colorScheme.primary)
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
                            "відокремити нову лінію",
                            !isAdvancing,
                            { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.DIVERGE, representativeSettlement.id) },
                            MaterialTheme.colorScheme.secondary,
                        )
                        ActionTile(
                            "Мутація",
                            "змінити план тіла",
                            !isAdvancing,
                            { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.MUTATE, representativeSettlement.id) },
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                    ActionTileFullWidth(
                        title = "Гібридизація",
                        subtitle = hybridCandidate?.let { "поєднати з ${it.lineageLabel}" } ?: "потрібна відмінна друга лінія",
                        enabled = !isAdvancing && hybridCandidate != null,
                        onClick = { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.HYBRIDIZE, representativeSettlement.id) },
                    )
                    if (hybridCandidate == null) {
                        Text(
                            "Спочатку розведіть різні лінії окремими втручаннями, а потім поверніться до гібридизації.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
