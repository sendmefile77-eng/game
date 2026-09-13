package com.sendmefile77.chronosphere.llm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.GameplayTurnReport
import com.sendmefile77.chronosphere.NeighborStanding
import com.sendmefile77.chronosphere.PanelCard
import com.sendmefile77.chronosphere.StatusPill
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal data class LlmTurnNarrative(
    val summaryUk: String,
    val outlookUk: String,
    val model: String,
    val elapsedMs: Long,
)

internal data class LlmDiplomaticVoice(
    val messageUk: String,
    val toneUk: String,
    val model: String,
    val elapsedMs: Long,
)

internal data class LlmCharacterVoice(
    val quoteUk: String,
    val noteUk: String,
    val model: String,
    val elapsedMs: Long,
)

internal object LlmNarrativeWriter {
    private val client = TellamaRuntime.client
    private val turnCache = ConcurrentHashMap<String, LlmTurnNarrative>()
    private val diplomacyCache = ConcurrentHashMap<String, LlmDiplomaticVoice>()
    private val characterCache = ConcurrentHashMap<String, LlmCharacterVoice>()

    suspend fun narrateTurn(report: GameplayTurnReport): LlmTurnNarrative? {
        val key = listOf(
            report.civilizationId,
            report.yearsAdvanced,
            report.populationDelta,
            "%.4f".format(report.stabilityDelta),
            "%.4f".format(report.technologyDelta),
            "%.2f".format(report.treasuryDelta),
            "%.2f".format(report.foodDelta),
            report.warsBefore,
            report.warsAfter,
            report.highlights.joinToString("|"),
        ).joinToString("#")
        turnCache[key]?.let { return it }
        val completion = complete(
            system = """
                Ти літописець гри «Хроносфера». Пиши українською живо, стисло і причинно.
                Не вигадуй фактів, осіб, причин, чисел чи подій. Використовуй лише надані зміни.
                Бажаний формат JSON: {"summary":"2-4 зв'язні речення про те, як минув хід","outlook":"1 речення: що тепер найбільш важливо"}.
                Якщо JSON не виходить, поверни просто зв'язний текст без пояснення формату.
            """.trimIndent(),
            user = buildString {
                appendLine("Держава=${report.civilizationName}; років=${report.yearsAdvanced}; вижила=${report.survived}")
                appendLine("населення=${report.populationDelta}; стабільність=${report.stabilityDelta}; розвиток=${report.technologyDelta}")
                appendLine("казна=${report.treasuryDelta}; їжа=${report.foodDelta}; війни=${report.warsBefore}->${report.warsAfter}")
                appendLine("події=${report.highlights.take(5).joinToString("; ").ifBlank { "немає" }}")
            },
            maxTokens = 300,
        ) ?: return null
        val json = parseJson(completion.content)
        val summary = flexibleField(completion.content, "summary", json)
            ?.takeIf { it.length in 10..700 }
            ?: plainReply(completion.content).takeIf { it.length in 10..700 }
            ?: return null
        val outlook = flexibleField(completion.content, "outlook", json)
            ?.takeIf { it.length in 5..280 }
            .orEmpty()
        return LlmTurnNarrative(summary, outlook, completion.model, completion.elapsedMs).also { turnCache[key] = it }
    }

    suspend fun diplomacy(
        tick: Long,
        ownName: String,
        target: NeighborStanding,
    ): LlmDiplomaticVoice? {
        val key = "$tick|$ownName|${target.civilizationId}|${"%.3f".format(target.relation)}|${target.status}"
        diplomacyCache[key]?.let { return it }
        val completion = complete(
            system = """
                Ти пишеш коротку дипломатичну репліку держави в грі «Хроносфера» українською.
                Не вигадуй правителів, договорів, воєн або мотивів. Врахуй лише статус і числові відносини.
                Не приймай рішень за гравця. Бажаний JSON: {"message":"1-2 речення від імені іншої держави","tone":"2-5 слів про тон"}.
                Якщо JSON не виходить, поверни лише саму дипломатичну репліку.
            """.trimIndent(),
            user = "Наша держава=$ownName; інша держава=${target.name}; відносини=${"%.2f".format(target.relation)}; статус=${target.status}; війна=${target.atWar}; союз=${target.allied}",
            maxTokens = 220,
        ) ?: return null
        val json = parseJson(completion.content)
        val message = flexibleField(completion.content, "message", json)
            ?.takeIf { it.length in 8..420 }
            ?: plainReply(completion.content).takeIf { it.length in 8..420 }
            ?: return null
        val tone = flexibleField(completion.content, "tone", json)
            ?.takeIf { it.length in 2..80 }
            .orEmpty()
        return LlmDiplomaticVoice(message, tone, completion.model, completion.elapsedMs).also { diplomacyCache[key] = it }
    }

    suspend fun character(
        person: NotablePerson,
        tick: Long,
        people: PeopleState,
        era: TechnologyEra?,
    ): LlmCharacterVoice? {
        val decade = tick / 120L
        val relationships = people.relationships.count { it.involves(person.id) }
        val key = "${person.id}|$decade|${person.role}|${person.traits.hashCode()}|$relationships|${era?.name}"
        characterCache[key]?.let { return it }
        val completion = complete(
            system = """
                Ти даєш голос реальній особі з симуляції «Хроносфера». Пиши українською від першої особи.
                Не вигадуй конкретних воєн, міст, родичів, посад або вчинків, яких немає у вхідних даних.
                Репліка має передавати роль, характер і епоху, але не змінювати канон.
                Бажаний формат — JSON: {"quote":"1-3 короткі речення від першої особи","note":"коротко, що в характері це підкреслює"}.
                Якщо не можеш дати JSON, поверни просто саму репліку без пояснень.
            """.trimIndent(),
            user = buildString {
                appendLine("ім'я=${person.name}; вік=${person.ageYearsAt(tick)}; роль=${person.role.name}")
                appendLine("риси=${person.traits.joinToString(",").ifBlank { "немає" }}")
                appendLine("вплив=${"%.2f".format(person.prestige)}; здібності=${"%.2f".format(person.aptitude)}")
                appendLine("епоха=${era?.displayNameUk ?: "невизначена"}; відомих зв'язків=$relationships")
            },
            maxTokens = 240,
        ) ?: return null
        val json = parseJson(completion.content)
        val quote = flexibleField(completion.content, "quote", json)
            ?.takeIf { it.length in 8..520 }
            ?: plainReply(completion.content).takeIf { it.length in 8..520 }
            ?: return null
        val note = flexibleField(completion.content, "note", json)
            ?.takeIf { it.length in 3..180 }
            .orEmpty()
        return LlmCharacterVoice(quote, note, completion.model, completion.elapsedMs).also { characterCache[key] = it }
    }

    private suspend fun complete(system: String, user: String, maxTokens: Int): TellamaCompletion? {
        if (!client.hasApiKey()) return null
        return try {
            client.completeJson(
                systemPrompt = system,
                userPrompt = user,
                maxTokens = maxTokens,
                temperature = 0.66,
                timeoutMillis = 60_000,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    internal fun parseJson(raw: String): JSONObject? {
        val cleaned = cleanReply(raw)
        runCatching { JSONObject(cleaned) }.getOrNull()?.let { return it }
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()?.let { return it }
        }
        return null
    }

    internal fun flexibleField(raw: String, name: String, parsed: JSONObject? = parseJson(raw)): String? {
        parsed?.optString(name)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val cleaned = cleanReply(raw)
        val quoted = Regex("(?is)[\\\"']?${Regex.escape(name)}[\\\"']?\\s*:\\s*\\\"([^\\\"]{2,900})\\\"")
            .find(cleaned)?.groupValues?.getOrNull(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted
        val singleQuoted = Regex("(?is)[\\\"']?${Regex.escape(name)}[\\\"']?\\s*:\\s*'([^']{2,900})'")
            .find(cleaned)?.groupValues?.getOrNull(1)?.trim()
        return singleQuoted?.takeIf { it.isNotBlank() }
    }

    internal fun plainReply(raw: String): String {
        var cleaned = cleanReply(raw)
        if (cleaned.startsWith('{') && cleaned.endsWith('}') && cleaned.length > 2) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        cleaned = cleaned
            .replace(
                Regex("(?i)[\\\"']?(quote|summary|message|advice|note|tone|outlook|title)[\\\"']?\\s*:\\s*"),
                "",
            )
            .replace(Regex("\\s*,\\s*[\\\"']?[a-zA-Z_]+[\\\"']?\\s*:\\s*"), " ")
            .trim()
        if (cleaned.startsWith('"') && cleaned.endsWith('"') && cleaned.length > 1) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        return cleaned
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(700)
    }

    private fun cleanReply(raw: String): String = raw.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}

@Composable
internal fun LocalLlmTurnNarrativeCard(report: GameplayTurnReport, enabled: Boolean) {
    var narrative by remember(report) { mutableStateOf<LlmTurnNarrative?>(null) }
    var working by remember(report) { mutableStateOf(false) }

    LaunchedEffect(report, enabled) {
        narrative = null
        if (!enabled || !TellamaRuntime.client.hasApiKey()) return@LaunchedEffect
        working = true
        narrative = LlmNarrativeWriter.narrateTurn(report)
        working = false
    }

    if (!working && narrative == null) return
    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Qwen · історія ходу", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusPill(if (working) "ПИШЕ" else "ГОТОВО", color = MaterialTheme.colorScheme.secondary)
            }
            if (working) {
                Text("Перетворюю сухі підсумки на зв’язний епізод…", style = MaterialTheme.typography.bodySmall)
            } else {
                narrative?.let { value ->
                    Text(value.summaryUk, style = MaterialTheme.typography.bodyMedium)
                    if (value.outlookUk.isNotBlank()) {
                        Text(value.outlookUk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${value.model} · ${String.format("%.1f", value.elapsedMs / 1000.0)} с", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun LocalLlmDiplomacyVoiceCard(
    tick: Long,
    ownName: String,
    target: NeighborStanding,
    enabled: Boolean,
) {
    var voice by remember(tick, ownName, target.civilizationId, target.relation, target.status) {
        mutableStateOf<LlmDiplomaticVoice?>(null)
    }
    var working by remember(target.civilizationId) { mutableStateOf(false) }

    LaunchedEffect(tick, ownName, target.civilizationId, target.relation, target.status, enabled) {
        voice = null
        if (!enabled || !TellamaRuntime.client.hasApiKey()) return@LaunchedEffect
        working = true
        voice = LlmNarrativeWriter.diplomacy(tick, ownName, target)
        working = false
    }

    if (!working && voice == null) return
    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Послання · ${target.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            if (working) {
                Text("Qwen формулює позицію…", style = MaterialTheme.typography.bodySmall)
            } else {
                voice?.let { value ->
                    Text("“${value.messageUk}”", style = MaterialTheme.typography.bodyMedium)
                    if (value.toneUk.isNotBlank()) Text("Тон · ${value.toneUk}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun LocalLlmCharacterVoiceCard(
    person: NotablePerson,
    tick: Long,
    people: PeopleState,
    technologyEra: TechnologyEra?,
    enabled: Boolean,
) {
    val scope = rememberCoroutineScope()
    var voice by remember(person.id, tick / 120L) { mutableStateOf<LlmCharacterVoice?>(null) }
    var working by remember(person.id) { mutableStateOf(false) }
    var failure by remember(person.id, tick / 120L) { mutableStateOf<String?>(null) }
    var model by remember(person.id) { mutableStateOf<String?>(null) }

    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Голос персонажа · Qwen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Локальна LLM говорить від імені ${person.name}, не змінюючи факти гри.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(
                    when {
                        working -> "ПИШЕ"
                        voice != null -> "ГОТОВО"
                        failure != null -> "ПОМИЛКА"
                        TellamaRuntime.client.hasApiKey() -> "ГОТОВА"
                        else -> "НЕ НАЛАШТОВАНО"
                    },
                    color = if (failure == null && TellamaRuntime.client.hasApiKey()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
            }

            when {
                working -> Text("Qwen формулює репліку… Це може тривати до хвилини на 1.5B-моделі.", style = MaterialTheme.typography.bodySmall)
                voice != null -> {
                    Text("“${voice!!.quoteUk}”", style = MaterialTheme.typography.bodyMedium)
                    if (voice!!.noteUk.isNotBlank()) Text(voice!!.noteUk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${model ?: voice!!.model} · локально", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    failure?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                working = true
                                failure = null
                                val status = TellamaRuntime.client.status(force = true)
                                model = status.model
                                if (!status.available) {
                                    failure = status.detail ?: "Tellama недоступна"
                                    working = false
                                    return@launch
                                }
                                voice = LlmNarrativeWriter.character(person, tick, people, technologyEra)
                                if (voice == null) {
                                    failure = TellamaRuntime.client.lastError()
                                        ?: "Qwen відповіла, але текст не вдалося розібрати. Спробуйте ще раз."
                                }
                                working = false
                            }
                        },
                        enabled = enabled && TellamaRuntime.client.hasApiKey(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (failure == null) "Дати слово" else "Спробувати ще раз")
                    }
                }
            }
        }
    }
}
