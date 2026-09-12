package com.sendmefile77.chronosphere.llm

import com.sendmefile77.chronosphere.ChronicleDecision
import com.sendmefile77.chronosphere.ChronicleDecisionOption
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.textgen.ChronicleNarrative
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal data class LlmChronicleEnrichment(
    val narrative: ChronicleNarrative,
    val decision: ChronicleDecision?,
    val model: String,
    val elapsedMs: Long,
)

/**
 * Uses a local Tellama model only as a writer/editor.
 * Simulation facts, action ids, targets, strengths and InterventionKind values remain authoritative
 * Kotlin data and are never accepted back from the model. If Tellama is absent or returns malformed
 * JSON the deterministic ChronicleTextGenerator/ChronicleDecisionCatalog output stays on screen.
 */
internal object ChronicleLlmEnricher {
    private val client = TellamaClient()
    private val cache = ConcurrentHashMap<String, LlmChronicleEnrichment>()

    suspend fun enrich(
        event: SimulationEvent,
        recentEvents: List<SimulationEvent>,
        people: PeopleState,
        economy: EconomyState,
        baseNarrative: ChronicleNarrative,
        baseDecision: ChronicleDecision?,
    ): LlmChronicleEnrichment? {
        val cacheKey = buildString {
            append(event.id).append('|').append(event.tick).append('|').append(event.code)
            append('|').append(event.facts.hashCode()).append('|').append(event.numbers.hashCode())
            append('|').append(baseDecision?.eventId.orEmpty())
            append('|').append(baseDecision?.options?.joinToString(",") { it.id }.orEmpty())
        }
        cache[cacheKey]?.let { return it }

        val completion = try {
            client.completeJson(
                systemPrompt = SYSTEM_PROMPT,
                userPrompt = buildPrompt(event, recentEvents, people, economy, baseNarrative, baseDecision),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return null

        val parsed = runCatching {
            parse(completion.content, baseNarrative, baseDecision)
        }.getOrNull() ?: return null

        return LlmChronicleEnrichment(
            narrative = parsed.first,
            decision = parsed.second,
            model = completion.model,
            elapsedMs = completion.elapsedMs,
        ).also { cache[cacheKey] = it }
    }

    internal fun buildPrompt(
        event: SimulationEvent,
        recentEvents: List<SimulationEvent>,
        people: PeopleState,
        economy: EconomyState,
        baseNarrative: ChronicleNarrative,
        baseDecision: ChronicleDecision?,
    ): String {
        val civilizationIds = economy.civilizations.mapTo(hashSetOf()) { it.civilizationId }
        val civilizationId = event.actorIds.firstNotNullOfOrNull { actorId ->
            when {
                actorId in civilizationIds -> actorId
                else -> people.persons.firstOrNull { it.id == actorId }?.civilizationId
            }
        }
        val ruler = civilizationId?.let(people::ruler)
        val profile = civilizationId?.let(people::profile)
        val civEconomy = civilizationId?.let(economy::economy)
        val safeFacts = event.facts
            .filterKeys { key -> key !in HIDDEN_FACT_KEYS && !key.startsWith("pmorph:") }
            .entries
            .take(12)
            .joinToString("; ") { (key, value) -> "$key=${value.take(120)}" }
        val numbers = event.numbers.entries.take(10).joinToString("; ") { (key, value) -> "$key=${"%.3f".format(value)}" }
        val previous = recentEvents.asReversed()
            .filter { it.id != event.id }
            .take(4)
            .reversed()
            .joinToString("\n") { old -> "- ${old.code}: ${old.facts.values.take(2).joinToString(", ")}" }
            .ifBlank { "- немає" }

        return buildString {
            appendLine("ПОДІЯ (це єдине джерело фактів):")
            appendLine("code=${event.code}; tick=${event.tick}; facts={$safeFacts}; numbers={$numbers}")
            if (civilizationId != null) appendLine("civilizationId=$civilizationId")
            if (civEconomy != null) {
                appendLine("economy: era=${civEconomy.era.displayNameUk}; shortage=${"%.2f".format(civEconomy.shortageIndex)}; trade=${"%.1f".format(civEconomy.tradeBalance)}")
            }
            if (ruler != null) appendLine("ruler=${ruler.name}; age=${ruler.ageYearsAt(event.tick)}")
            if (profile != null) appendLine("culture=${profile.tags.sorted().take(8).joinToString(",")}; tension=${"%.2f".format(profile.socialTension)}")
            appendLine("Попередні події лише для контексту:")
            appendLine(previous)
            appendLine()
            appendLine("ДЕТЕРМІНОВАНИЙ ЧЕРНЕТКОВИЙ ТЕКСТ:")
            appendLine("title=${baseNarrative.title}")
            appendLine("hook=${baseNarrative.hook}")
            appendLine("body=${baseNarrative.body}")
            appendLine("significance=${baseNarrative.significance}")
            appendLine("changes=${baseNarrative.changes.joinToString(" | ")}")
            if (baseDecision != null) {
                appendLine()
                appendLine("РІШЕННЯ. Механіку НЕ змінювати, лише зробити формулювання живішими:")
                appendLine("decision_title=${baseDecision.titleUk}")
                appendLine("decision_prompt=${baseDecision.promptUk}")
                baseDecision.options.forEach { option ->
                    appendLine("option id=${option.id}; title=${option.titleUk}; effect=${option.effectUk}; risk=${option.riskUk}")
                }
            }
            appendLine()
            appendLine("Поверни РІВНО один JSON-об'єкт без markdown:")
            appendLine(JSON_SHAPE)
        }.take(MAX_PROMPT_CHARS)
    }

    private fun parse(
        raw: String,
        baseNarrative: ChronicleNarrative,
        baseDecision: ChronicleDecision?,
    ): Pair<ChronicleNarrative, ChronicleDecision?> {
        val clean = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val json = JSONObject(clean)

        fun text(key: String, fallback: String, max: Int): String =
            json.optString(key).trim().takeIf { it.length in 3..max } ?: fallback

        val changesJson = json.optJSONArray("changes")
        val changes = if (changesJson == null) baseNarrative.changes else {
            (0 until minOf(changesJson.length(), 5))
                .mapNotNull { index -> changesJson.optString(index).trim().takeIf { it.length in 2..160 } }
                .ifEmpty { baseNarrative.changes }
        }
        val narrative = ChronicleNarrative(
            title = text("title", baseNarrative.title, 110),
            hook = text("hook", baseNarrative.hook, 240),
            body = text("body", baseNarrative.body, 900),
            significance = text("significance", baseNarrative.significance, 520),
            changes = changes,
        )

        if (baseDecision == null) return narrative to null
        val decisionTitle = text("decision_title", baseDecision.titleUk, 130)
        val decisionPrompt = text("decision_prompt", baseDecision.promptUk, 360)
        val optionJson = json.optJSONArray("options")
        val rewrites = mutableMapOf<String, Triple<String, String, String>>()
        if (optionJson != null) {
            for (index in 0 until optionJson.length()) {
                val item = optionJson.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val base = baseDecision.options.firstOrNull { it.id == id } ?: continue
                rewrites[id] = Triple(
                    item.optString("title").trim().takeIf { it.length in 2..90 } ?: base.titleUk,
                    item.optString("effect").trim().takeIf { it.length in 2..220 } ?: base.effectUk,
                    item.optString("risk").trim().takeIf { it.length in 2..220 } ?: base.riskUk,
                )
            }
        }
        val rewrittenOptions = baseDecision.options.map { base ->
            val rewrite = rewrites[base.id] ?: return@map base
            // Only prose is copied from the LLM. Gameplay fields remain byte-for-byte authoritative.
            ChronicleDecisionOption(
                id = base.id,
                sourceEventId = base.sourceEventId,
                titleUk = rewrite.first,
                effectUk = rewrite.second,
                riskUk = rewrite.third,
                kind = base.kind,
                targetCivilizationId = base.targetCivilizationId,
                strength = base.strength,
            )
        }
        return narrative to baseDecision.copy(
            titleUk = decisionTitle,
            promptUk = decisionPrompt,
            options = rewrittenOptions,
        )
    }

    private const val MAX_PROMPT_CHARS = 7_500
    private val HIDDEN_FACT_KEYS = setOf("mediaKey", "mediaTags")

    private val SYSTEM_PROMPT = """
        Ти локальний сценарист гри «Хроносфера». Пиши природною українською, стисло й атмосферно.
        Ти НЕ керуєш симуляцією. Не вигадуй нові факти, числа, осіб, міста, війни чи наслідки.
        Можна лише переформулювати надані факти та пояснити їх значення.
        Якщо дано варіанти рішення: збережи КОЖЕН id, не додавай і не видаляй варіанти, не змінюй їхню механічну суть.
        Відповідай тільки валідним JSON без markdown і без тексту поза JSON.
    """.trimIndent()

    private val JSON_SHAPE = """
        {"title":"...","hook":"...","body":"...","significance":"...","changes":["..."],"decision_title":"...","decision_prompt":"...","options":[{"id":"існуючий-id","title":"...","effect":"...","risk":"..."}]}
    """.trimIndent()
}
