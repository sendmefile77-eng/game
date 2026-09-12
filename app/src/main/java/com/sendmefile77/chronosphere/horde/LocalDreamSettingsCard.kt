package com.sendmefile77.chronosphere.horde

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LocalDreamSettingsCard(enabled: Boolean) {
    val context = LocalContext.current
    var selectedId by remember {
        mutableStateOf(LocalDreamModelPackStore.load(context).id)
    }
    val selected = LocalDreamModelPacks.byId(selectedId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "Local Dream · модель",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Перемикач міняє пакет гри: промпт, steps, CFG і sampler. Сам чекпоінт треба відкрити в Local Dream.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LocalDreamModelPacks.all.forEach { pack ->
                    FilterChip(
                        selected = pack.id == selectedId,
                        onClick = {
                            selectedId = pack.id
                            LocalDreamModelPackStore.save(context, pack)
                        },
                        enabled = enabled,
                        label = { Text(pack.titleUk) },
                    )
                }
            }
            Text(selected.subtitleUk, fontWeight = FontWeight.SemiBold)
            Text(
                "Steps ${selected.steps} · CFG ${trimCfg(selected.cfgScale)} · ${selected.samplerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                selected.hintUk,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun trimCfg(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
