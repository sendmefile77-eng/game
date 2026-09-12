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
import androidx.compose.runtime.mutableIntStateOf
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

/** Visible read-only Qwen layer. The deterministic game remains authoritative. */
@Composable
internal fun LocalLlmWorldAdvisorCard(
    state: LivingPlanetState,
    civilization: Civilization,
    economyState: EconomyState,
    briefing: GameBriefing,
    enabled: Boolean,
) {
    val client = TellamaRuntime.client
    var advice by remember(state.tick, civilization.id, briefing.headline, briefing.objective.title) {
        mutableStateOf<LlmWorldAdvice?>(null)
    }
    var status by remember { mutableStateOf<TellamaStatus?>(null) }
    var checking by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(
        state.tick,
        civilization.id,
        briefing.headline,
        briefing.objective.title,
        enabled,
        refreshNonce,
    ) {
        advice = null
        if (!enabled) return@LaunchedEffect
        checking = true
        status = client.status(force = true)
        checking = false
        if (status?.available != true) return@LaunchedEffect
        working = true
        advice = WorldLlmAdvisor.advise(
            state = state,
            civilization = civilization,
            economyState = economyState,
            briefing = briefing,
        )
        working = false
    }

    val currentStatus = status
    val accent = if (currentStatus?.available == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.055f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
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
                    Text("Qwen · локальний радник", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            checking -> "Перевіряю Tellama…"
                            currentStatus?.available == true -> currentStatus?.model ?: "Tellama підключена"
                            currentStatus != null -> "Tellama недоступна"
                            client.hasApiKey() -> "Готова до перевірки"
                            else -> "Потрібен API-ключ Tellama"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = when {
                        checking -> "ПЕРЕВІРКА"
                        currentStatus?.available == true -> "АКТИВНА"
                        else -> "НЕДОСТУПНА"
                    },
                    color = accent,
                )
            }

            if (currentStatus?.available == false && !currentStatus.detail.isNullOrBlank()) {
                Text(
                    currentStatus.detail.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { refreshNonce += 1 }, enabled = enabled && !checking && !working) {
                    Text("Перевірити")
                }
                TextButton(onClick = { showSettings = !showSettings }, enabled = enabled) {
                    Text(if (showSettings) "Сховати ключ" else "Налаштувати")
                }
            }

            if (showSettings) {
                TellamaSettingsCard(enabled = enabled)
            }

            val current = advice
            when {
                working -> Text(
                    "Qwen читає стан світу й готує пораду…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                current != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(current.titleUk, fontWeight = FontWeight.SemiBold)
                        Text(current.adviceUk, style = MaterialTheme.typography.bodyMedium)
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
                currentStatus?.available == true -> Text(
                    "Qwen підключена. Порада з’явиться після аналізу поточного стану.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
