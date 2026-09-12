package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.textgen.ChronicleNarrative
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator

internal data class ChronicleStoryBeat(
    val eventId: String,
    val tick: Long,
    val title: String,
    val summary: String,
)

internal data class ChronicleStory(
    val title: String,
    val lead: String,
    val paragraphs: List<String>,
    val beats: List<ChronicleStoryBeat>,
)

/** User-facing prose layer. Internal event codes never leak into the chronicle. */
internal object ChroniclePresentation {
    fun narrative(event: SimulationEvent, textGenerator: ChronicleTextGenerator): ChronicleNarrative {
        val base = textGenerator.narrative(event)
        if (event.code != "ADULT_SOCIAL_EVENT") return base

        val civilization = event.facts["civilization"]?.takeIf { it.isNotBlank() } ?: "невідомій державі"
        val practice = socialPracticeName(event.facts["eventCode"])
        val participants = event.facts["participants"]?.takeIf { it.isNotBlank() }
        return ChronicleNarrative(
            title = "$civilization · приватні звичаї стають суспільною традицією",
            hook = "Повсякденна поведінка дорослих показує, як культурні норми працюють поза законами та офіційними інститутами.",
            body = buildString {
                append("У державі $civilization зафіксовано $practice серед повнолітніх учасників.")
                participants?.let { append(" У центрі цієї історії — $it.") }
                append(" Для хроніки важлива не назва практики сама по собі, а те, що вона повторює і закріплює прийняті в суспільстві моделі близькості, довіри та статусу.")
            },
            significance = "Такі епізоди поступово формують репутацію покоління і показують, чи залишаються проголошені культурні норми абстракцією, чи справді стають способом життя.",
            changes = participants?.let { listOf("Учасники: $it", "Соціальна практика: $practice") }
                ?: listOf("Соціальна практика: $practice"),
        )
    }

    private fun socialPracticeName(code: String?): String = when (code?.lowercase()) {
        "bondage_rite", "bondage rite" -> "обряд довіри та символічного зв’язування"
        "union" -> "формування приватного союзу"
        "orgy" -> "груповий обряд близькості"
        "anal_union", "anal union" -> "інтимний союз двох дорослих"
        "cum_rite", "cum rite" -> "тілесний ритуал близькості"
        "fertility_rite", "fertility rite" -> "обряд родючості"
        "public_union", "public union" -> "публічне підтвердження союзу"
        "dominance_rite", "dominance rite" -> "ритуал статусу та підпорядкування"
        null, "" -> "приватний соціальний обряд"
        else -> "локальний обряд близькості"
    }
}

/**
 * Builds a deterministic story arc from real simulation events. It never invents people, places,
 * numbers or outcomes: the prose only connects facts already emitted by the simulation.
 */
internal object ChronicleStoryComposer {
    fun compose(
        events: List<SimulationEvent>,
        civilizationNames: Map<String, String>,
        textGenerator: ChronicleTextGenerator,
    ): ChronicleStory? {
        if (events.isEmpty()) return null

        val window = events.takeLast(24)
        val focusId = window.asReversed()
            .flatMap { event -> event.actorIds.asReversed() }
            .filter { it in civilizationNames }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { entry ->
                window.indexOfLast { entry.key in it.actorIds }
            })
            ?.key
        val focusName = focusId?.let(civilizationNames::get)

        val focused = if (focusId == null) {
            window
        } else {
            window.filter { focusId in it.actorIds }
                .ifEmpty { window }
        }

        val deduped = focused.fold(mutableListOf<SimulationEvent>()) { acc, event ->
            val previous = acc.lastOrNull()
            val sameBurst = previous != null &&
                previous.code == event.code &&
                (previous.tick == event.tick ||
                    event.code == "ADULT_SOCIAL_EVENT" && event.tick - previous.tick <= 12L)
            if (sameBurst) {
                acc[acc.lastIndex] = event
            } else {
                acc += event
            }
            acc
        }

        val selected = selectTurningPoints(deduped, 7)
        if (selected.isEmpty()) return null
        val narratives = selected.map { event -> event to ChroniclePresentation.narrative(event, textGenerator) }
        val first = narratives.first()
        val last = narratives.last()
        val span = (last.first.tick - first.first.tick).coerceAtLeast(0L)

        val title = when {
            focusName != null && narratives.size >= 3 -> "$focusName · історія змін"
            focusName != null -> "$focusName · початок власної історії"
            else -> "Світ набуває власної історії"
        }
        val lead = buildString {
            if (focusName != null) append("За останній відрізок саме $focusName опинилися в центрі змін. ")
            append("Хроніка з’єднує ${narratives.size} поворотних подій")
            if (span > 0L) append(" упродовж $span місяців модельованого часу")
            append(" — не як список, а як одну послідовність причин і наслідків.")
        }

        val paragraphs = buildList {
            add(
                "Спочатку ${lowerFirst(first.second.body)} " +
                    "Це стало вихідною точкою: ${lowerFirst(first.second.significance)}",
            )
            if (narratives.size > 2) {
                val middle = narratives.drop(1).dropLast(1)
                val sentences = middle.mapIndexed { index, (_, narrative) ->
                    val connector = when (index % 4) {
                        0 -> "Згодом"
                        1 -> "На цьому тлі"
                        2 -> "Після цього"
                        else -> "Тим часом"
                    }
                    "$connector ${lowerFirst(narrative.body)}"
                }
                add(sentences.joinToString(" "))
            }
            if (narratives.size > 1) {
                add(
                    "До теперішнього моменту ${lowerFirst(last.second.body)} " +
                        "Саме тому зараз важливо наступне: ${lowerFirst(last.second.significance)}",
                )
            }
        }.filter { it.isNotBlank() }

        val beats = narratives.map { (event, narrative) ->
            ChronicleStoryBeat(
                eventId = event.id,
                tick = event.tick,
                title = narrative.title,
                summary = narrative.hook,
            )
        }
        return ChronicleStory(title, lead, paragraphs, beats)
    }

    private fun selectTurningPoints(events: List<SimulationEvent>, limit: Int): List<SimulationEvent> {
        if (events.size <= limit) return events
        val priorityCodes = setOf(
            "WAR_STARTED", "PEACE_TREATY", "CITY_CAPTURED", "ERA_ADVANCED",
            "RULER_SUCCEEDED", "DYNASTY_FOUNDED", "BIOLOGICAL_DIVERGENCE",
            "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED", "PLAYER_STRUCTURAL_MUTATION",
            "PLAYER_HYBRIDIZATION", "ALLIANCE_FORMED", "ALLIANCE_ENDED",
        )
        val chosen = linkedSetOf<SimulationEvent>()
        chosen += events.first()
        events.filter { it.code in priorityCodes }.forEach(chosen::add)
        events.asReversed().forEach { event ->
            if (chosen.size < limit) chosen += event
        }
        chosen += events.last()
        return chosen.sortedBy { it.tick }.takeLast(limit)
    }

    private fun lowerFirst(value: String): String =
        value.trim().replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() }
}
