package com.sendmefile77.chronosphere.llm

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Tellama key is kept in app-private noBackupFilesDir: it is local to this installation,
 * excluded from Android Auto Backup and never mixed into world saves.
 */
internal object TellamaSettingsStore {
    private const val FILE_NAME = "tellama_api_key.txt"

    fun load(context: Context): String? = runCatching {
        File(context.noBackupFilesDir, FILE_NAME)
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun save(context: Context, value: String?) {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            if (file.exists()) file.delete()
        } else {
            file.parentFile?.mkdirs()
            file.writeText(normalized, Charsets.UTF_8)
        }
    }
}

@Composable
internal fun TellamaSettingsCard(enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialKey = remember { TellamaSettingsStore.load(context).orEmpty() }
    var apiKey by remember { mutableStateOf(initialKey) }
    var statusText by remember {
        mutableStateOf(if (initialKey.isBlank()) "API-ключ ще не збережено" else "API-ключ збережено локально")
    }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TellamaRuntime.configureApiKey(initialKey)
    }

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
                "Локальна LLM · Tellama",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tellama → Server → This phone only → New key → Start server. Вставте створений ключ нижче.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text("Tellama API key") },
                placeholder = { Text("tlm_…") },
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val normalized = apiKey.trim()
                        TellamaSettingsStore.save(context, normalized)
                        TellamaRuntime.configureApiKey(normalized)
                        statusText = if (normalized.isBlank()) "API-ключ видалено" else "API-ключ збережено локально"
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Зберегти")
                }
                OutlinedButton(
                    onClick = {
                        val normalized = apiKey.trim()
                        TellamaSettingsStore.save(context, normalized)
                        TellamaRuntime.configureApiKey(normalized)
                        checking = true
                        scope.launch {
                            val status = TellamaRuntime.client.status(force = true)
                            statusText = if (status.available) {
                                "Підключено · ${status.model ?: "модель готова"}"
                            } else {
                                status.detail ?: "Tellama не відповідає"
                            }
                            checking = false
                        }
                    },
                    enabled = enabled && !checking && apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (checking) "Перевірка…" else "Перевірити")
                }
            }
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (statusText.startsWith("Підключено")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                "Ключ зберігається тільки у приватному no-backup сховищі цієї інсталяції Хроносфери.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
