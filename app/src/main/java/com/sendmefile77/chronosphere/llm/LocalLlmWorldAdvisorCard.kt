package com.sendmefile77.chronosphere.llm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.GameBriefing
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

    LaunchedEffect(state.tick, civilization.id, briefing.headline, briefing.objective.title, enabled) {
        advice = null
        if (!enabled) return@LaunchedEffect
        advice = WorldLlmAdvisor.advise(
            state = state,
            civilization = civilization,
            economyState = economyState,
            briefing = briefing,
        )
    }

    val current = advice ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Локальний радник · Tellama",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
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
                    "Рекомендована дія: $action",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "${current.model} · ${String.format("%.1f", current.elapsedMs / 1000.0)} с · порада не виконується автоматично",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
