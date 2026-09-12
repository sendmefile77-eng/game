package com.sendmefile77.chronosphere.llm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.GameBriefing
import com.sendmefile77.chronosphere.StatusPill
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState

/** Optional layer: if Tellama is not running the deterministic World panel remains unchanged. */
@Composable
internal fun LocalLlmWorldAdvisorCard(
    state: LivingPlanetState,
    civilization: Civilization,
    economyState: EconomyState,
    briefing: GameBriefing,
    enabled: Boolean,
) {
    var advice by remember(state.tick, civilization.id, briefing.headline, briefing.objective.title) {
        mutableStateOf<LlmWorldAdvice?>(null)
    }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.tick, civilization.id, briefing.headline, briefing.objective.title, enabled) {
        advice = null
        if (!enabled || !TellamaRuntime.client.hasApiKey()) return@LaunchedEffect
        advice = WorldLlmAdvisor.advise(
            state = state,
            civilization = civilization,
            economyState = economyState,
            briefing = briefing,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.055f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.26f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Локальний радник", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Tellama · необов’язковий текстовий шар",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = if (TellamaRuntime.client.hasApiKey()) "налаштовано" else "неактивно",
                    color = MaterialTheme.colorScheme.secondary,
                )
                TextButton(onClick = { showSettings = !showSettings }, enabled = enabled) {
                    Text(if (showSettings) "Сховати" else "Налаштувати")
                }
            }

            if (showSettings) {
                TellamaSettingsCard(enabled = enabled)
            }

            val current = advice
            if (current != null) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(current.titleUk, fontWeight = FontWeight.SemiBold)
                    Text(current.adviceUk, style = MaterialTheme.typography.bodySmall)
                    if (current.whyUk.isNotBlank()) {
                        Text(
                            current.whyUk,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    current.actionUk?.let { action ->
                        Text(
                            "Рекомендована дія · $action",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        "${current.model} · ${String.format("%.1f", current.elapsedMs / 1000.0)} с · лише порада",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
