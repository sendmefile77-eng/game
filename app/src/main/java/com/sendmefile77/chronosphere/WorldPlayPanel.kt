package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.PlayerEvolutionInterventionEngine
import com.sendmefile77.chronosphere.history.InterventionKind
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

    Text("Як грати", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "1. Натисни державу на карті. 2. Зроби втручання нижче. 3. Прокрути +1 / +10 / +100 років і дивись, що змінилось. У «Хроніці» інколи з’являються рішення.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Зараз", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(briefing.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(briefing.pressure, style = MaterialTheme.typography.bodySmall)
            Text(briefing.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (briefing.wars.isNotEmpty()) {
                Text("Війни: ${briefing.wars.joinToString(", ")}", color = MaterialTheme.colorScheme.error)
            }
            if (briefing.allies.isNotEmpty()) {
                Text("Союзники: ${briefing.allies.joinToString(", ")}")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Завдання", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(briefing.objective.title, fontWeight = FontWeight.SemiBold)
            Text(briefing.objective.detail, style = MaterialTheme.typography.bodySmall)
            Text(briefing.objective.meter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pendingDecisionTitle != null) {
                OutlinedButton(onClick = onOpenChronicle, enabled = !isAdvancing, modifier = Modifier.fillMaxWidth()) {
                    Text("Відкрити хроніку")
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(civilization.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                economy?.era?.displayNameUk ?: "Епоха формується",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (civilizationCount > 1) {
            OutlinedButton(onClick = onNextCivilization, enabled = !isAdvancing) { Text("Наступна") }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard("Населення", compactNumber(civilization.population), Modifier.weight(1f))
        MetricCard("Стабільність", qualityBand(civilization.stability), Modifier.weight(1f))
        MetricCard("Розвиток", qualityBand(civilization.technology), Modifier.weight(1f))
    }

    if (economy != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard("Казна", compactNumber(civilization.treasury), Modifier.weight(1f))
            MetricCard("Ресурси", shortageBand(economy.shortageIndex), Modifier.weight(1f))
            MetricCard("Торгівля", tradeBand(economy.tradeBalance), Modifier.weight(1f))
        }
    }

    if (representativeLineage != null && representativePopulation != null) {
        InfoLine(
            "Походження",
            "${representativeLineage.label} · ${rankDisplayName(representativeLineage.rank.name)} · домішка ${String.format("%.0f%%", representativePopulation.admixture * 100.0)}",
        )
    }
    if (ruler != null) {
        val dynasty = ruler.dynastyId?.let { dynastyId -> peopleState.dynasties.firstOrNull { it.id == dynastyId }?.name }
        InfoLine(
            "Правитель",
            "${ruler.name}, ${ruler.ageYearsAt(session.state.tick)} р.${dynasty?.let { " · $it" } ?: ""}",
        )
    }
    if (profile != null) {
        val culture = profile.tags.sorted().take(8).joinToString(separator = " · ", transform = ::humanizeTag)
        InfoLine("Культура", culture.ifBlank { "Без виразної домінантної традиції" })
        InfoLine("Суспільство", tensionBand(profile.socialTension))
    }

    if (representativeSettlement != null && representativePopulation != null && representativeLineage != null) {
        val bodyPlan = representativeLineage.bodyPlan
        Text("Керування еволюцією", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        InfoLine(
            "Активна лінія",
            "${representativeLineage.label} · відхилення ${String.format("%.0f%%", representativeLineage.divergenceFromOrigin * 100.0)} · тиск мутацій ${String.format("%.0f%%", representativePopulation.mutationPressure * 100.0)}",
        )
        InfoLine(
            "План тіла",
            "рук ${bodyPlan.armPairs * 2} · ніг ${bodyPlan.legPairs * 2} · очей ${bodyPlan.eyeCount}" +
                if (bodyPlan.hasTail) " · хвіст" else "",
        )
        InfoLine(
            "Гібридизація",
            if (hybridCandidate != null) {
                "${hybridCandidate.lineageLabel}${hybridSettlementName?.let { " ($it)" } ?: ""} · відмінність ${String.format("%.0f%%", hybridCandidate.difference * 100.0)}"
            } else {
                "Поки немає достатньо відмінної другої лінії"
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InterventionButton("Розходження", Modifier.weight(1f), !isAdvancing) {
                onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.DIVERGE, representativeSettlement.id)
            }
            InterventionButton("Мутація", Modifier.weight(1f), !isAdvancing) {
                onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.MUTATE, representativeSettlement.id)
            }
        }
        OutlinedButton(
            onClick = { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.HYBRIDIZE, representativeSettlement.id) },
            enabled = !isAdvancing && hybridCandidate != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Гібридизувати з найвідміннішою доступною лінією")
        }
        if (hybridCandidate == null) {
            Text(
                "Спочатку розведіть лінії: змінюйте різні держави окремо, а потім поверніться до гібридизації.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Text("Втручання", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Кожна кнопка б’є тільки по вибраній державі. Ефект видно після прокрутки часу.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InterventionButton("Врожай", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.HARVEST_AID) }
        InterventionButton("Посуха", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.DROUGHT) }
    }
    Text("Врожай — їжа ↑. Посуха — їжа і люди ↓.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InterventionButton("Прорив", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.TECHNOLOGY_BOOST) }
        InterventionButton("Порядок", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.STABILITY_SUPPORT) }
    }
    Text("Прорив — розвиток ↑. Порядок — стабільність ↑.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InterventionButton("Набіг", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.WAR_RAID) }
        InterventionButton("Свято", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.FESTIVAL) }
    }
    Text("Набіг палить запаси ворога або сусіда. Свято піднімає порядок ціною казни.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun compactNumber(value: Long): String = when {
    kotlin.math.abs(value) >= 1_000_000_000L -> String.format("%.1f млрд", value / 1_000_000_000.0)
    kotlin.math.abs(value) >= 1_000_000L -> String.format("%.1f млн", value / 1_000_000.0)
    kotlin.math.abs(value) >= 1_000L -> String.format("%.1f тис.", value / 1_000.0)
    else -> value.toString()
}

private fun compactNumber(value: Double): String = when {
    kotlin.math.abs(value) >= 1_000_000_000.0 -> String.format("%.1f млрд", value / 1_000_000_000.0)
    kotlin.math.abs(value) >= 1_000_000.0 -> String.format("%.1f млн", value / 1_000_000.0)
    kotlin.math.abs(value) >= 1_000.0 -> String.format("%.1f тис.", value / 1_000.0)
    else -> String.format("%.1f", value)
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InterventionButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
}
