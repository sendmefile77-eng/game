package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TurnDecisionDialog(
    decision: ChronicleDecision,
    onSelect: (ChronicleDecisionOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Рішення перед ходом", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(decision.titleUk, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(decision.promptUk, style = MaterialTheme.typography.bodyMedium)
                decision.options.forEachIndexed { index, option ->
                    if (index == 0) {
                        Button(
                            onClick = { onSelect(option) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ChronosphereSmallShape,
                        ) { Text(option.titleUk) }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(option) },
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Пізніше") }
        },
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
            Button(onClick = onDismiss, shape = ChronosphereSmallShape) { Text("Далі") }
        },
    )
}
