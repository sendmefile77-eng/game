package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.InstitutionState
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.institutionStrength
import com.sendmefile77.chronosphere.civilization.institutionsFor
import com.sendmefile77.chronosphere.civilization.taxPolicyFor
import com.sendmefile77.chronosphere.history.InterventionKind

@Composable
internal fun StateInstitutionPanel(
    state: LivingPlanetState,
    civilization: Civilization,
    hasPendingDecision: Boolean = false,
    isAdvancing: Boolean,
    onQueue: (InterventionKind, String, String, String, String?) -> Unit,
) {
    val taxPolicy = state.taxPolicyFor(civilization.id)
    val institutions = state.institutionsFor(civilization.id).sortedBy { it.kind.ordinal }
    val weakest = institutions.minByOrNull { institutionScore(it) }
    val lowerGate = GameplayLoop.gate(state, civilization.id, InterventionKind.TAX_LOWER, null, hasPendingDecision)
    val raiseGate = GameplayLoop.gate(state, civilization.id, InterventionKind.TAX_RAISE, null, hasPendingDecision)
    val reformGate = GameplayLoop.gate(state, civilization.id, InterventionKind.INSTITUTION_REFORM, null, hasPendingDecision)
    val policyPriorityYears = taxPolicy
        ?.let { ((it.playerPriorityUntilTick - state.tick).coerceAtLeast(0L) / 12L).toInt() }
        ?: 0

    SectionHeader(title = "Устрій держави", eyebrow = "Податки й інститути")
    PanelCard(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ПОДАТКОВА ПОЛІТИКА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Text(
                        taxPolicy?.kind?.titleUk ?: "Збалансовані податки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        buildString {
                            append("Ставка ${String.format("%.0f%%", (taxPolicy?.rate ?: 0.16) * 100.0)} · збір залежить від адміністрації та лояльності провінцій")
                            if (policyPriorityYears > 0) append(" · курс гравця ще ~$policyPriorityYears р.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    "інститути ${String.format("%.0f%%", state.institutionStrength(civilization.id) * 100.0)}",
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    title = "↓ Податки",
                    subtitle = "менше доходу · менше тиску",
                    enabled = !isAdvancing && lowerGate.enabled,
                    onClick = {
                        onQueue(
                            InterventionKind.TAX_LOWER,
                            "Знизити податки",
                            "Зменшити податковий тиск на одну сходинку.",
                            "Казна зростатиме повільніше, але провінціям стане легше.",
                            null,
                        )
                    },
                    accent = MaterialTheme.colorScheme.secondary,
                )
                ActionTile(
                    title = "↑ Податки",
                    subtitle = "більше доходу · більше напруги",
                    enabled = !isAdvancing && raiseGate.enabled,
                    onClick = {
                        onQueue(
                            InterventionKind.TAX_RAISE,
                            "Підвищити податки",
                            "Підняти податковий режим на одну сходинку.",
                            "Доходи зростуть, але еліти й провінції можуть чинити опір.",
                            null,
                        )
                    },
                    accent = MaterialTheme.colorScheme.primary,
                )
            }

            if (institutions.isNotEmpty()) {
                Text("ІНСТИТУТИ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                institutions.forEach { institution ->
                    InfoLine(
                        institution.kind.titleUk,
                        "спроможність ${String.format("%.0f%%", institution.capacity * 100.0)} · легітимність ${String.format("%.0f%%", institution.legitimacy * 100.0)}",
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    title = "Реформа інститутів",
                    subtitle = weakest?.let {
                        "посилити: ${it.kind.titleUk.lowercase()} · ${reformGate.treasuryCost.toInt()} казни"
                    } ?: "створити базові інститути · ${reformGate.treasuryCost.toInt()} казни",
                    enabled = !isAdvancing && reformGate.enabled,
                    onClick = {
                        onQueue(
                            InterventionKind.INSTITUTION_REFORM,
                            "Реформа державних інститутів",
                            weakest?.let { "Посилити найслабший інститут: ${it.kind.titleUk}." }
                                ?: "Закласти стійкі державні інститути.",
                            "Реформа коштує казни й дає результат поступово.",
                            null,
                        )
                    },
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }

            val reasons = listOfNotNull(lowerGate.reasonUk, raiseGate.reasonUk, reformGate.reasonUk).distinct()
            if (reasons.isNotEmpty() && (!lowerGate.enabled || !raiseGate.enabled || !reformGate.enabled)) {
                Text(
                    reasons.take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun institutionScore(institution: InstitutionState): Double =
    institution.capacity * 0.65 + institution.legitimacy * 0.35
