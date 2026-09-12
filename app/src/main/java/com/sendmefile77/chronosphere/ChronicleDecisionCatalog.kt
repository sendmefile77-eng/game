package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.history.InterventionKind
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
)

internal data class ChronicleDecision(
    val eventId: String,
    val titleUk: String,
    val promptUk: String,
    val options: List<ChronicleDecisionOption>,
)

/**
 * Converts a small set of genuinely important simulation events into player decisions.
 *
 * The effects deliberately reuse InterventionEngine, so every button changes the same world state
 * that the simulation advances afterwards. A resolved sourceEventId is written into the resulting
 * intervention event, which makes decisions persistent across save/load without a new save schema.
 */
internal object ChronicleDecisionCatalog {
    fun latestUnresolved(
        events: List<SimulationEvent>,
        state: LivingPlanetState,
    ): ChronicleDecision? {
        val resolved = events.asSequence()
            .mapNotNull { it.facts["sourceEventId"] }
            .toSet()
        return events.asReversed().firstNotNullOfOrNull { event ->
            if (event.id in resolved) null else forEvent(event, state)
        }
    }

    fun forEvent(event: SimulationEvent, state: LivingPlanetState): ChronicleDecision? {
        val civilizationIds = state.civilizations.mapTo(hashSetOf()) { it.id }
        val actorCivilizations = event.actorIds.filter { it in civilizationIds }
        val primary = actorCivilizations.firstOrNull()
            ?: civilizationByName(state, event.facts["civilization"])
            ?: civilizationByName(state, event.facts["a"])
            ?: return null
        val secondary = actorCivilizations.drop(1).firstOrNull()
            ?: civilizationByName(state, event.facts["b"])

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

        val primaryName = state.civilizations.firstOrNull { it.id == primary }?.name ?: "держава"
        return when (event.code) {
            "SETTLEMENT_FOUNDED", "COLONY_FOUNDED" -> ChronicleDecision(
                eventId = event.id,
                titleUk = "Новий центр потребує напрямку",
                promptUk = "Перші роки визначать, чи стане нове поселення опорою $primaryName, чи залишиться слабким форпостом.",
                options = listOf(
                    option(
                        id = "feed-growth",
                        title = "Підтримати запаси",
                        effect = "Різко збільшити продовольчий резерв держави.",
                        risk = "Швидке зростання може пізніше посилити навантаження на ресурси.",
                        kind = InterventionKind.HARVEST_AID,
                        strength = 0.58,
                    ),
                    option(
                        id = "secure-order",
                        title = "Закріпити порядок",
                        effect = "Підняти стабільність і зробити новий центр політично надійнішим.",
                        risk = "Менше уваги отримає матеріальне зростання.",
                        kind = InterventionKind.STABILITY_SUPPORT,
                        strength = 0.52,
                    ),
                    option(
                        id = "fund-craft",
                        title = "Ставка на ремесла",
                        effect = "Прискорити технологічний розвиток держави.",
                        risk = "Продовольча база не отримає прямої підтримки.",
                        kind = InterventionKind.TECHNOLOGY_BOOST,
                        strength = 0.38,
                    ),
                ),
            )

            "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> ChronicleDecision(
                eventId = event.id,
                titleUk = "Дефіцит вимагає відповіді",
                promptUk = "$primaryName входить у небезпечну фазу нестачі. Втручання зараз змінить траєкторію найближчих років.",
                options = listOf(
                    option(
                        id = "emergency-food",
                        title = "Аварійні запаси",
                        effect = "Негайно поповнити продовольство поселень.",
                        risk = "Причина дефіциту сама по собі не зникне.",
                        kind = InterventionKind.HARVEST_AID,
                        strength = 0.72,
                    ),
                    option(
                        id = "hold-society",
                        title = "Утримати суспільство",
                        effect = "Підняти стабільність і знизити ризик політичного зриву.",
                        risk = "Матеріальний дефіцит залишиться гострим.",
                        kind = InterventionKind.STABILITY_SUPPORT,
                        strength = 0.64,
                    ),
                    option(
                        id = "solve-tech",
                        title = "Шукати технологічне рішення",
                        effect = "Прискорити технології, що можуть дати довгостроковий вихід.",
                        risk = "Ефект не компенсує нестачу їжі просто зараз.",
                        kind = InterventionKind.TECHNOLOGY_BOOST,
                        strength = 0.46,
                    ),
                ),
            )

            "WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED" -> {
                val options = mutableListOf(
                    option(
                        id = "war-homefront",
                        title = "Зміцнити тил",
                        effect = "Підвищити стабільність $primaryName під тиском війни.",
                        risk = "Не дає прямої військової переваги.",
                        kind = InterventionKind.STABILITY_SUPPORT,
                        strength = 0.66,
                    ),
                    option(
                        id = "war-technology",
                        title = "Прискорити військові технології",
                        effect = "Дати державі відчутний технологічний імпульс.",
                        risk = "Результат проявиться через подальшу симуляцію, а не миттєво на фронті.",
                        kind = InterventionKind.TECHNOLOGY_BOOST,
                        strength = 0.60,
                    ),
                )
                if (secondary != null) {
                    val enemyName = state.civilizations.firstOrNull { it.id == secondary }?.name ?: "супротивника"
                    options += option(
                        id = "war-scorch-enemy",
                        title = "Виснажити $enemyName",
                        effect = "Спричинити сильний удар по продовольству й частині населення супротивника.",
                        risk = "Це жорстке втручання, яке може радикально змінити баланс світу.",
                        kind = InterventionKind.DROUGHT,
                        target = secondary,
                        strength = 0.62,
                    )
                }
                ChronicleDecision(
                    eventId = event.id,
                    titleUk = "Війна відкрила вікно для втручання",
                    promptUk = "Вибір зараз визначить, чи переживе $primaryName конфлікт через стійкість, технологічну перевагу або виснаження ворога.",
                    options = options,
                )
            }

            "PEACE_TREATY", "ALLIANCE_FORMED" -> ChronicleDecision(
                eventId = event.id,
                titleUk = "Мир можна перетворити на перевагу",
                promptUk = "Дипломатичне вікно дає $primaryName шанс закріпити внутрішній порядок або швидко накопичити ресурси.",
                options = listOf(
                    option(
                        id = "peace-stability",
                        title = "Закріпити мир усередині",
                        effect = "Помітно підвищити стабільність держави.",
                        risk = "Економічний ефект буде непрямим.",
                        kind = InterventionKind.STABILITY_SUPPORT,
                        strength = 0.58,
                    ),
                    option(
                        id = "peace-reserves",
                        title = "Накопичити резерви",
                        effect = "Поповнити продовольчі запаси поселень.",
                        risk = "Політичні суперечності залишаться без прямої відповіді.",
                        kind = InterventionKind.HARVEST_AID,
                        strength = 0.50,
                    ),
                ),
            )

            "ERA_ADVANCED" -> ChronicleDecision(
                eventId = event.id,
                titleUk = "Нова епоха — куди спрямувати імпульс?",
                promptUk = "$primaryName перейшла технологічний рубіж. Перші пріоритети нової епохи вплинуть на її подальшу перевагу.",
                options = listOf(
                    option(
                        id = "era-push",
                        title = "Продовжити технологічний ривок",
                        effect = "Ще сильніше прискорити технологічний показник.",
                        risk = "Суспільна стабільність не отримає підтримки.",
                        kind = InterventionKind.TECHNOLOGY_BOOST,
                        strength = 0.68,
                    ),
                    option(
                        id = "era-consolidate",
                        title = "Дати суспільству адаптуватися",
                        effect = "Підсилити стабільність після швидких змін.",
                        risk = "Темп технологічного відриву буде нижчим.",
                        kind = InterventionKind.STABILITY_SUPPORT,
                        strength = 0.60,
                    ),
                ),
            )

            "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> ChronicleDecision(
                eventId = event.id,
                titleUk = "Нова влада ще не закріпилася",
                promptUk = "Перехід влади у $primaryName створює коротке вікно, коли можна визначити характер нового правління.",
                options = listOf(
                    option(
                        id = "rule-legitimacy",
                        title = "Підсилити легітимність",
                        effect = "Суттєво підняти стабільність режиму.",
                        risk = "Не прискорює матеріальний розвиток.",
                        kind = InterventionKind.STABILITY_SUPPORT,
                        strength = 0.70,
                    ),
                    option(
                        id = "rule-reform",
                        title = "Підштовхнути реформи",
                        effect = "Дати новій владі технологічний імпульс.",
                        risk = "Швидкі зміни проходять без прямої стабілізації.",
                        kind = InterventionKind.TECHNOLOGY_BOOST,
                        strength = 0.52,
                    ),
                ),
            )

            "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
            "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> ChronicleDecision(
                eventId = event.id,
                titleUk = "Нова лінія змінює суспільство",
                promptUk = "Біологічна зміна вже стала фактом. Тепер можна допомогти $primaryName адаптувати інституції або використати нові можливості для розвитку.",
                options = listOf(
                    option(
                        id = "evo-integrate",
                        title = "Захистити інтеграцію",
                        effect = "Підвищити стабільність у період біологічних змін.",
                        risk = "Не прискорює подальший розвиток технологій.",
                        kind = InterventionKind.STABILITY_SUPPORT,
                        strength = 0.56,
                    ),
                    option(
                        id = "evo-study",
                        title = "Досліджувати нову лінію",
                        effect = "Перетворити зміни на технологічний імпульс.",
                        risk = "Соціальна напруга не отримає прямої компенсації.",
                        kind = InterventionKind.TECHNOLOGY_BOOST,
                        strength = 0.46,
                    ),
                ),
            )

            else -> null
        }
    }

    private fun civilizationByName(state: LivingPlanetState, value: String?): String? {
        val name = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return state.civilizations.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
    }
}
