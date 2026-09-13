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

    fun toggle(option: ChronicleDecisionOption) {
        if (!multi) {
            onConfirm(listOf(option))
            return
        }
        if (option.id in selectedIds) {
            selectedIds = selectedIds - option.id
            return
        }
        val family = EraTurnChoiceCatalog.family(option)
        val withoutFamily = if (family == null) selectedIds else {
            selectedIds.filterTo(linkedSetOf()) { id ->
                val old = decision.options.firstOrNull { it.id == id }
                old == null || EraTurnChoiceCatalog.family(old) != family
            }
        }
        selectedIds = if (withoutFamily.size >= 3) withoutFamily else withoutFamily + option.id
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
                        "Можна поєднати кілька різних напрямів. Два варіанти одного типу взаємно замінюються.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                decision.options.forEach { option ->
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
                            "Обрано ${selected.size}/3",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (multi) {
                Button(
                    onClick = { onConfirm(selected) },
                    enabled = selected.isNotEmpty(),
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
