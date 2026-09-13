package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TurnDecisionDialog(
    decision: ChronicleDecision,
    onConfirm: (List<ChronicleDecisionOption>) -> Unit,
) {
    val multi = EraTurnChoiceCatalog.isEraTurn(decision)
    var selectedIds by remember(decision.eventId) { mutableStateOf(emptySet<String>()) }
    val selected = decision.options.filter { it.id in selectedIds }
    val selectedEra = selected.filter(TurnChoiceComposer::isEraOption)
    val requiredEventSources = remember(decision) { TurnChoiceComposer.requiredHistoricalSources(decision) }
    val resolvedEventSources = selected.asSequence()
        .filterNot(TurnChoiceComposer::isEraOption)
        .map { it.sourceEventId }
        .toSet()
    val historicalReady = requiredEventSources.all { it in resolvedEventSources }

    fun toggle(option: ChronicleDecisionOption) {
        if (!multi) {
            onConfirm(listOf(option))
            return
        }
        if (option.id in selectedIds) {
            selectedIds = selectedIds - option.id
            return
        }
        val group = TurnChoiceComposer.selectionGroup(option)
        val withoutSameGroup = selectedIds.filterTo(linkedSetOf()) { id ->
            val old = decision.options.firstOrNull { it.id == id }
            old == null || TurnChoiceComposer.selectionGroup(old) != group
        }
        val eraCountAfterReplacement = decision.options.count { candidate ->
            candidate.id in withoutSameGroup && TurnChoiceComposer.isEraOption(candidate)
        }
        if (TurnChoiceComposer.isEraOption(option) && eraCountAfterReplacement >= 3) {
            selectedIds = withoutSameGroup
            return
        }
        selectedIds = withoutSameGroup + option.id
    }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (multi) "Вибір століття · до 3 напрямів" else "Історична розвилка",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(decision.titleUk, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(decision.promptUk, style = MaterialTheme.typography.bodyMedium)
                if (multi) {
                    Text(
                        "Відкриття можна накопичувати. Спосіб життя, суспільний курс і мобільність змінюють попередній вибір того ж типу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                var shownHistoricalHeader = false
                var shownEraHeader = false
                decision.options.forEach { option ->
                    val eraOption = TurnChoiceComposer.isEraOption(option)
                    if (!eraOption && !shownHistoricalHeader) {
                        Text(
                            "ІСТОРИЧНА РОЗВИЛКА · ОБОВ’ЯЗКОВО",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                        shownHistoricalHeader = true
                    }
                    if (eraOption && !shownEraHeader) {
                        Text(
                            "НАПРЯМИ ЕПОХИ · ОБЕРІТЬ 1–3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                        shownEraHeader = true
                    }
                    val isSelected = option.id in selectedIds
                    if (isSelected) {
                        Button(
                            onClick = { toggle(option) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ChronosphereSmallShape,
                        ) { Text("✓ ${option.titleUk}") }
                    } else {
                        OutlinedButton(
                            onClick = { toggle(option) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ChronosphereSmallShape,
                        ) { Text(option.titleUk) }
                    }
                    Text(
                        "Наслідок · ${option.effectUk}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Ризик · ${option.riskUk}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (multi) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Напрямів ${selectedEra.size}/3",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        if (requiredEventSources.isNotEmpty()) {
                            Text(
                                if (historicalReady) "Розвилку вирішено" else "Оберіть відповідь на подію",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (historicalReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multi) {
                Button(
                    onClick = { onConfirm(selected) },
                    enabled = selectedEra.isNotEmpty() && historicalReady,
                    shape = ChronosphereSmallShape,
                ) { Text("Прожити 100 років") }
            }
        },
        dismissButton = {},
    )
}

@Composable
internal fun TurnConsequenceDialog(
    report: GameplayTurnReport,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Розвиток світу · ${report.yearsAdvanced} р.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(report.headlineUk, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Населення ${GameplayLoop.signedLong(report.populationDelta)} · " +
                        "стабільність ${GameplayLoop.signedDouble(report.stabilityDelta)} · " +
                        "розвиток ${GameplayLoop.signedDouble(report.technologyDelta)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Казна ${GameplayLoop.signedDouble(report.treasuryDelta)} · " +
                        "їжа ${GameplayLoop.signedDouble(report.foodDelta)} · " +
                        "війни ${report.warsBefore} → ${report.warsAfter}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.highlights.isNotEmpty()) {
                    Text("Що змінилось", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    report.highlights.forEach { highlight ->
                        Text("• $highlight", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = ChronosphereSmallShape) { Text("Закрити") }
        },
    )
}
