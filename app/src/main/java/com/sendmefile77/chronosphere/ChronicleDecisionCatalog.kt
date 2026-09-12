package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent

internal data class ChronicleDecisionOption(
    val id: String,
    val sourceEventId: String,
    val titleUk: String,
    val effectUk: String,
    val riskUk: String,
    val kind: InterventionKind,
    val targetCivilizationId: String,
    val strength: Double,
    val counterpartCivilizationId: String? = null,
)

internal data class ChronicleDecision(
    val eventId: String,
    val titleUk: String,
    val promptUk: String,
    val options: List<ChronicleDecisionOption>,
)

internal data class PendingChronicleDecision(
    val commandId: String,
    val option: ChronicleDecisionOption,
)

/**
 * Process-level hand-off between the Chronicle UI and the next simulation slice.
 * A choice is applied at the beginning of the next time advance, so the causal loop is explicit:
 * event -> player choice -> time advances -> world reacts. Applied choices are also written to the
 * normal event log by InterventionEngine and therefore survive save/load without a new schema.
 */
internal object ChronicleDecisionMailbox {
    private val pending = linkedMapOf<String, PendingChronicleDecision>()

    @Synchronized
    fun enqueue(option: ChronicleDecisionOption) {
        pending[option.sourceEventId] = PendingChronicleDecision(
            commandId = "chronicle-${option.sourceEventId}-${option.id}",
            option = option,
        )
    }

    @Synchronized
    fun contains(sourceEventId: String): Boolean = sourceEventId in pending

    @Synchronized
    fun pendingFor(sourceEventId: String): PendingChronicleDecision? = pending[sourceEventId]

    @Synchronized
    fun remove(sourceEventId: String): PendingChronicleDecision? = pending.remove(sourceEventId)

    @Synchronized
    fun drain(): List<PendingChronicleDecision> = pending.values.toList().also { pending.clear() }

    @Synchronized
    fun restore(decisions: List<PendingChronicleDecision>) {
        decisions.forEach { pending[it.option.sourceEventId] = it }
    }
}

internal object ChronicleDecisionCatalog {
    fun latestUnresolved(
        events: List<SimulationEvent>,
        people: PeopleState,
        economy: EconomyState,
    ): ChronicleDecision? {
        val resolved = events.asSequence().mapNotNull { it.facts["sourceEventId"] }.toSet()
        return events.asReversed().firstNotNullOfOrNull { event ->
            if (event.id in resolved || ChronicleDecisionMailbox.contains(event.id)) null
            else forEvent(event, people, economy)
        }
    }

    fun forEvent(
        event: SimulationEvent,
        people: PeopleState,
        economy: EconomyState,
    ): ChronicleDecision? {
        val civilizationIds = economy.civilizations.mapTo(hashSetOf()) { it.civilizationId }
        val actorCivilizations = event.actorIds.mapNotNull { actorId ->
            when {
                actorId in civilizationIds -> actorId
                else -> people.persons.firstOrNull { it.id == actorId }?.civilizationId?.takeIf { it in civilizationIds }
            }
        }.distinct()
        val primary = actorCivilizations.firstOrNull() ?: return null
        val secondary = actorCivilizations.drop(1).firstOrNull()
        val primaryName = event.facts["civilization"] ?: event.facts["a"] ?: primary
        val secondaryName = event.facts["b"] ?: secondary ?: "супротивника"

        fun option(
            id: String,
            title: String,
            effect: String,
            risk: String,
            kind: InterventionKind,
            target: String = primary,
            strength: Double,
        ) = ChronicleDecisionOption(
            id = id,
            sourceEventId = event.id,
            titleUk = title,
            effectUk = effect,
            riskUk = risk,
            kind = kind,
            targetCivilizationId = target,
            strength = strength,
        )

        return when (event.code) {
            "SETTLEMENT_FOUNDED", "COLONY_FOUNDED" -> ChronicleDecision(
                event.id,
                "Новий центр потребує напрямку",
                "Перші роки визначать, чи стане поселення опорою $primaryName.",
                listOf(
                    option("feed-growth", "Підтримати запаси", "Поповнити продовольчий резерв.", "Швидке зростання підвищить майбутній попит на ресурси.", InterventionKind.HARVEST_AID, strength = 0.58),
                    option("secure-order", "Закріпити порядок", "Підняти стабільність держави.", "Матеріальне зростання не отримає прямої підтримки.", InterventionKind.STABILITY_SUPPORT, strength = 0.52),
                    option("fund-craft", "Ставка на ремесла", "Прискорити технологічний розвиток.", "Запаси їжі не збільшаться.", InterventionKind.TECHNOLOGY_BOOST, strength = 0.38),
                ),
            )

            "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> ChronicleDecision(
                event.id,
                "Дефіцит вимагає відповіді",
                "$primaryName входить у небезпечну фазу нестачі.",
                listOf(
                    option("emergency-food", "Аварійні запаси", "Негайно поповнити продовольство.", "Причина дефіциту сама не зникне.", InterventionKind.HARVEST_AID, strength = 0.72),
                    option("hold-society", "Утримати суспільство", "Підняти стабільність і знизити ризик зриву.", "Матеріальний дефіцит залишиться.", InterventionKind.STABILITY_SUPPORT, strength = 0.64),
                    option("solve-tech", "Технологічна відповідь", "Інвестувати у довгостроковий технологічний вихід.", "Не компенсує нестачу просто зараз.", InterventionKind.TECHNOLOGY_BOOST, strength = 0.46),
                ),
            )

            "WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED" -> {
                val choices = mutableListOf(
                    option("war-homefront", "Зміцнити тил", "Підвищити стабільність під тиском війни.", "Не дає прямого удару по противнику.", InterventionKind.STABILITY_SUPPORT, strength = 0.66),
                    option("war-technology", "Прискорити військові технології", "Дати державі технологічний імпульс.", "Перевага проявиться через подальшу симуляцію.", InterventionKind.TECHNOLOGY_BOOST, strength = 0.60),
                )
                if (secondary != null) {
                    choices += option(
                        "war-scorch-enemy",
                        "Виснажити $secondaryName",
                        "Ударити по продовольству та частині населення противника.",
                        "Жорстке втручання може радикально змінити баланс світу.",
                        InterventionKind.DROUGHT,
                        target = secondary,
                        strength = 0.62,
                    )
                }
                ChronicleDecision(
                    event.id,
                    "Війна відкрила вікно для втручання",
                    "Вибір визначить, через що $primaryName спробує переламати конфлікт.",
                    choices,
                )
            }

            "PEACE_TREATY", "ALLIANCE_FORMED" -> ChronicleDecision(
                event.id,
                "Мир можна перетворити на перевагу",
                "Дипломатичне вікно дає шанс закріпити порядок або накопичити ресурси.",
                listOf(
                    option("peace-stability", "Закріпити мир", "Помітно підвищити стабільність.", "Економічний ефект буде непрямим.", InterventionKind.STABILITY_SUPPORT, strength = 0.58),
                    option("peace-reserves", "Накопичити резерви", "Поповнити продовольчі запаси.", "Політичні суперечності не отримають прямої відповіді.", InterventionKind.HARVEST_AID, strength = 0.50),
                ),
            )

            "ERA_ADVANCED" -> ChronicleDecision(
                event.id,
                "Нова епоха — куди спрямувати імпульс?",
                "$primaryName перейшла технологічний рубіж.",
                listOf(
                    option("era-push", "Продовжити ривок", "Ще сильніше прискорити технології.", "Стабільність не отримає підтримки.", InterventionKind.TECHNOLOGY_BOOST, strength = 0.68),
                    option("era-consolidate", "Дати суспільству адаптуватися", "Підсилити стабільність після змін.", "Темп технологічного відриву буде нижчим.", InterventionKind.STABILITY_SUPPORT, strength = 0.60),
                ),
            )

            "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> ChronicleDecision(
                event.id,
                "Нова влада ще не закріпилася",
                "Перехід влади створює коротке вікно для визначення характеру правління.",
                listOf(
                    option("rule-legitimacy", "Підсилити легітимність", "Суттєво підняти стабільність режиму.", "Матеріальний розвиток не прискорюється.", InterventionKind.STABILITY_SUPPORT, strength = 0.70),
                    option("rule-reform", "Підштовхнути реформи", "Дати новій владі технологічний імпульс.", "Швидкі зміни йдуть без прямої стабілізації.", InterventionKind.TECHNOLOGY_BOOST, strength = 0.52),
                ),
            )

            "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
            "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> ChronicleDecision(
                event.id,
                "Нова лінія змінює суспільство",
                "Біологічна зміна вже стала фактом; тепер суспільству треба на неї відповісти.",
                listOf(
                    option("evo-integrate", "Захистити інтеграцію", "Підвищити стабільність у період змін.", "Не прискорює технологічний розвиток.", InterventionKind.STABILITY_SUPPORT, strength = 0.56),
                    option("evo-study", "Досліджувати нову лінію", "Перетворити зміни на технологічний імпульс.", "Соціальна напруга не отримає прямої компенсації.", InterventionKind.TECHNOLOGY_BOOST, strength = 0.46),
                ),
            )

            else -> null
        }
    }
}
