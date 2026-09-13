package com.sendmefile77.chronosphere.llm

import com.sendmefile77.chronosphere.GameBriefing
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal data class LlmWorldAdvice(
    val titleUk: String,
    val adviceUk: String,
    val whyUk: String,
    val actionUk: String?,
    val model: String,
    val elapsedMs: Long,
)

/**
 * A read-only strategic narrator for the World screen.
 * GameSituation remains authoritative; the LLM receives its calculated briefing and may only
 * explain it and point at an action that already exists in the UI. It cannot execute anything.
 */
internal object WorldLlmAdvisor {
    private val client = TellamaRuntime.client
    private val cache = ConcurrentHashMap<String, LlmWorldAdvice>()

    suspend fun advise(
        state: LivingPlanetState,
        civilization: Civilization,
        economyState: EconomyState,
        briefing: GameBriefing,
    ): LlmWorldAdvice? {
        val historicalLegacy = historicalLegacy(civilization.cultureTags)
        val cacheKey = listOf(
            state.tick.toString(),
            civilization.id,
            civilization.population.toString(),
            "%.3f".format(civilization.stability),
            "%.3f".format(civilization.technology),
            historicalLegacy.joinToString("|").hashCode().toString(),
            briefing.headline,
            briefing.objective.title,
            briefing.objective.meter,
        ).joinToString("|")
        cache[cacheKey]?.let { return it }

        val completion = try {
            client.completeJson(
                systemPrompt = SYSTEM_PROMPT,
                userPrompt = buildPrompt(state, civilization, economyState, briefing),
                maxTokens = 260,
                temperature = 0.50,
                timeoutMillis = 60_000,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return null

        val parsed = parse(completion.content)
        if (parsed.adviceUk.isBlank()) return null
        return LlmWorldAdvice(
            titleUk = parsed.titleUk,
            adviceUk = parsed.adviceUk,
            whyUk = parsed.whyUk,
            actionUk = parsed.actionUk,
            model = completion.model,
            elapsedMs = completion.elapsedMs,
        ).also { cache[cacheKey] = it }
    }

    internal fun buildPrompt(
        state: LivingPlanetState,
        civilization: Civilization,
        economyState: EconomyState,
        briefing: GameBriefing,
    ): String {
        val economy = economyState.economy(civilization.id)
        val neighbors = briefing.neighbors.take(5).joinToString("; ") { neighbor ->
            "${neighbor.name}: ${neighbor.status}, relation=${"%.2f".format(neighbor.relation)}"
        }.ifBlank { "немає" }
        val latest = briefing.latestEvent ?: "немає"
        val historicalLegacy = historicalLegacy(civilization.cultureTags)
        return buildString {
            appendLine("Це вже порахований стан гри. Не додавай нових фактів:")
            appendLine("tick=${state.tick}")
            appendLine("держава=${civilization.name}")
            appendLine("населення=${civilization.population}")
            appendLine("стабільність=${"%.3f".format(civilization.stability)}")
            appendLine("розвиток=${"%.3f".format(civilization.technology)}")
            appendLine("казна=${"%.1f".format(civilization.treasury)}")
            if (economy != null) {
                appendLine("епоха=${economy.era.displayNameUk}; дефіцит=${"%.3f".format(economy.shortageIndex)}; торгівля=${"%.1f".format(economy.tradeBalance)}")
            }
            if (historicalLegacy.isNotEmpty()) {
                appendLine("довготривала спадщина виборів=${historicalLegacy.joinToString(", ")}")
                appendLine("правило спадщини=вважай ці риси реальною частиною господарства, культури й повсякденної поведінки, а не декоративними назвами")
            }
            appendLine("ситуація=${briefing.headline}")
            appendLine("тиск=${briefing.pressure}")
            appendLine("детермінована підказка=${briefing.hint}")
            appendLine("завдання=${briefing.objective.title}; ${briefing.objective.detail}; ${briefing.objective.meter}")
            appendLine("війни=${briefing.wars.joinToString(", ").ifBlank { "немає" }}")
            appendLine("союзники=${briefing.allies.joinToString(", ").ifBlank { "немає" }}")
            appendLine("сусіди=$neighbors")
            appendLine("остання подія=$latest")
            appendLine("правило ходу=гравець обирає напрями епохи й одразу запускає 100 років симуляції")
            appendLine("дипломатія=Посольство покращує відносини; Союз потребує добрих відносин; Мир і Набіг доступні під час війни")
            appendLine()
            appendLine("Дозволені назви дій: ${ALLOWED_ACTIONS.joinToString(", ")}")
            appendLine("Бажаний JSON:")
            appendLine("{\"title\":\"коротка оцінка\",\"advice\":\"1-2 речення\",\"why\":\"1 коротке речення\",\"action\":\"одна дозволена назва або порожній рядок\"}")
            appendLine("Якщо JSON не виходить — поверни просто коротку пораду звичайним текстом.")
        }.take(5_500)
    }

    internal fun parse(raw: String): ParsedWorldAdvice {
        val clean = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val json = runCatching {
            JSONObject(clean)
        }.getOrNull() ?: run {
            val start = clean.indexOf('{')
            val end = clean.lastIndexOf('}')
            if (start >= 0 && end > start) runCatching { JSONObject(clean.substring(start, end + 1)) }.getOrNull() else null
        }

        val title = LlmNarrativeWriter.flexibleField(clean, "title", json)
            ?.takeIf { it.length in 3..100 }
            ?: "Оцінка ситуації"
        val advice = LlmNarrativeWriter.flexibleField(clean, "advice", json)
            ?.takeIf { it.length in 5..420 }
            ?: LlmNarrativeWriter.plainReply(clean).takeIf { it.length in 5..420 }
            .orEmpty()
        val why = LlmNarrativeWriter.flexibleField(clean, "why", json)
            ?.takeIf { it.length in 3..260 }
            .orEmpty()
        val requestedAction = LlmNarrativeWriter.flexibleField(clean, "action", json).orEmpty()
        val action = ALLOWED_ACTIONS.firstOrNull { it.equals(requestedAction, ignoreCase = true) }
        return ParsedWorldAdvice(title, advice, why, action)
    }

    internal data class ParsedWorldAdvice(
        val titleUk: String,
        val adviceUk: String,
        val whyUk: String,
        val actionUk: String?,
    )

    internal val ALLOWED_ACTIONS = listOf(
        "Резерви",
        "Дослідження",
        "Порядок",
        "Свято",
        "Посольство",
        "Союз",
        "Війна",
        "Мир",
        "Набіг",
        "Відкрити хроніку",
        "Хід · 100 років",
    )

    private fun historicalLegacy(tags: Set<String>): List<String> = tags.asSequence()
        .filter { tag -> HISTORY_PREFIXES.any(tag::startsWith) }
        .map { tag ->
            val parts = tag.split(':')
            when {
                tag.startsWith("era-choice:") && parts.size >= 3 -> "${humanize(parts[1])}: ${humanize(parts.drop(2).joinToString(" "))}"
                else -> humanize(tag.substringAfter(':', tag))
            }
        }
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .take(12)
        .toList()

    private fun humanize(value: String): String = value.replace('_', ' ').replace('-', ' ')

    private val HISTORY_PREFIXES = listOf("era-choice:", "foundation:", "policy:", "hist:", "history_policy:")

    private val SYSTEM_PROMPT = """
        Ти локальний радник у грі «Хроносфера». Відповідай українською.
        Спирайся ТІЛЬКИ на наданий порахований стан. Не вигадуй людей, держави, війни, ресурси або числа.
        Не змінюй правила і не виконуй дії. Ти лише коротко пояснюєш гравцеві ситуацію.
        Якщо рекомендуєш дію, використовуй точну назву лише з наданого списку. Якщо жодна не підходить — action порожній.
        JSON бажаний, але коректна коротка текстова відповідь теж прийнятна.
    """.trimIndent()
}
