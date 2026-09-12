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
        val cacheKey = listOf(
            state.tick.toString(),
            civilization.id,
            civilization.population.toString(),
            "%.3f".format(civilization.stability),
            "%.3f".format(civilization.technology),
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
                timeoutMillis = 30_000,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return null

        val parsed = runCatching { parse(completion.content) }.getOrNull() ?: return null
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
            appendLine("ситуація=${briefing.headline}")
            appendLine("тиск=${briefing.pressure}")
            appendLine("детермінована підказка=${briefing.hint}")
            appendLine("завдання=${briefing.objective.title}; ${briefing.objective.detail}; ${briefing.objective.meter}")
            appendLine("війни=${briefing.wars.joinToString(", ").ifBlank { "немає" }}")
            appendLine("союзники=${briefing.allies.joinToString(", ").ifBlank { "немає" }}")
            appendLine("сусіди=$neighbors")
            appendLine("остання подія=$latest")
            appendLine()
            appendLine("Дозволені назви дій: ${ALLOWED_ACTIONS.joinToString(", ")}")
            appendLine("Поверни лише JSON:")
            appendLine("{\"title\":\"коротка оцінка\",\"advice\":\"1-2 речення\",\"why\":\"1 коротке речення\",\"action\":\"одна дозволена назва або порожній рядок\"}")
        }.take(5_500)
    }

    internal fun parse(raw: String): ParsedWorldAdvice {
        val clean = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val json = JSONObject(clean)
        val title = json.optString("title").trim().takeIf { it.length in 3..100 }
            ?: return ParsedWorldAdvice("Оцінка ситуації", "", "", null)
        val advice = json.optString("advice").trim().takeIf { it.length in 5..420 }.orEmpty()
        val why = json.optString("why").trim().takeIf { it.length in 3..260 }.orEmpty()
        if (advice.isBlank()) return ParsedWorldAdvice(title, "", why, null)
        val requestedAction = json.optString("action").trim()
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
        "Врожай",
        "Посуха",
        "Прорив",
        "Порядок",
        "Набіг",
        "Свято",
        "Відкрити хроніку",
        "+1 рік",
    )

    private val SYSTEM_PROMPT = """
        Ти локальний радник у грі «Хроносфера». Відповідай українською.
        Спирайся ТІЛЬКИ на наданий порахований стан. Не вигадуй людей, держави, війни, ресурси або числа.
        Не змінюй правила і не виконуй дії. Ти лише коротко пояснюєш гравцеві ситуацію.
        Якщо рекомендуєш дію, використовуй точну назву лише з наданого списку. Якщо жодна не підходить — action порожній.
        Відповідь — один валідний JSON без markdown.
    """.trimIndent()
}
