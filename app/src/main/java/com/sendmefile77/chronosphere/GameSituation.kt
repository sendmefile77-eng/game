package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.activeRebellionsFor
import com.sendmefile77.chronosphere.civilization.internalPressure
import com.sendmefile77.chronosphere.civilization.provincesFor
import com.sendmefile77.chronosphere.civilization.taxPolicyFor
import com.sendmefile77.chronosphere.economy.EconomyState

data class GameObjective(
    val title: String,
    val detail: String,
    val meter: String,
    val complete: Boolean,
)

data class NeighborStanding(
    val civilizationId: String,
    val name: String,
    val relation: Double,
    val atWar: Boolean,
    val allied: Boolean,
    val status: String,
)

data class GameBriefing(
    val headline: String,
    val pressure: String,
    val hint: String,
    val wars: List<String>,
    val allies: List<String>,
    val neighbors: List<NeighborStanding>,
    val latestEvent: String?,
    val objective: GameObjective,
)

object GameSituation {
    fun briefing(
        state: LivingPlanetState,
        civilization: Civilization,
        economy: EconomyState?,
        pendingDecisionTitle: String?,
    ): GameBriefing {
        val ownSettlements = state.settlements.filter { it.civilizationId == civilization.id }
        val food = ownSettlements.sumOf { it.foodStock }
        val people = civilization.population.coerceAtLeast(1L)
        val foodPer = food / people.toDouble()
        val hungry = foodPer < 0.45
        val fragile = civilization.stability < 0.38
        val internalPressure = state.internalPressure(civilization.id)
        val rebellions = state.activeRebellionsFor(civilization.id)
        val provinces = state.provincesFor(civilization.id)
        val worstProvince = provinces.maxByOrNull { it.unrest }
        val worstProvinceName = worstProvince?.let { province ->
            state.settlements.firstOrNull { it.id == province.settlementId }?.name
        }
        val taxPolicy = state.taxPolicyFor(civilization.id)
        val names = state.civilizations.associate { it.id to it.name }
        val wars = state.wars.mapNotNull { war ->
            when {
                war.civilizationA == civilization.id -> names[war.civilizationB]
                war.civilizationB == civilization.id -> names[war.civilizationA]
                else -> null
            }
        }
        val allies = state.alliances.mapNotNull { alliance ->
            when {
                alliance.civilizationA == civilization.id -> names[alliance.civilizationB]
                alliance.civilizationB == civilization.id -> names[alliance.civilizationA]
                else -> null
            }
        }
        val neighbors = neighborsOf(state, civilization.id)
        val rank = state.civilizations.sortedByDescending { it.population }.indexOfFirst { it.id == civilization.id } + 1
        val era = economy?.economy(civilization.id)?.era?.displayNameUk ?: "рання епоха"
        val latest = state.recentEvents.lastOrNull()?.let { event ->
            val code = eventLabel(event.code)
            val other = event.facts["b"] ?: event.facts["settlement"] ?: event.facts["civilization"]
            if (other != null) "$code · $other" else code
        }

        val headline = when {
            pendingDecisionTitle != null -> "Історія ${civilization.name} дійшла до розвилки"
            rebellions.isNotEmpty() -> "${civilization.name}: відкрите повстання${worstProvinceName?.let { " у $it" } ?: ""}"
            internalPressure >= 0.68 -> "${civilization.name}: провінції й еліти тиснуть на центр"
            wars.isNotEmpty() -> "${civilization.name} у війні з ${wars.joinToString(", ")}"
            hungry -> "${civilization.name} на межі голоду"
            fragile -> "${civilization.name} хитається: низька стабільність"
            else -> "${civilization.name} · $era · ${rank}-а за людністю"
        }
        val pressure = buildList {
            add("їжа ${foodBand(foodPer)}")
            add("порядок ${stabilityBand(civilization.stability)}")
            add("внутр. напруга ${pressureBand(internalPressure)}")
            taxPolicy?.let { add("податки ${String.format("%.0f%%", it.rate * 100.0)}") }
            add("розвиток ${techBand(civilization.technology)}")
            if (economy != null) add("ресурси ${shortageBandLocal(economy.economy(civilization.id)?.shortageIndex)}")
        }.joinToString(" · ")
        val worst = neighbors.minByOrNull { it.relation }
        val hint = when {
            pendingDecisionTitle != null -> "Час призупинено. Відкрий «Хроніку» і обери відповідь на подію."
            rebellions.isNotEmpty() -> "Відкрите повстання б'є по казні й стабільності. Підтримка порядку дає центру шанс повернути лояльність до того, як регіон відокремиться."
            internalPressure >= 0.68 -> "Внутрішня напруга небезпечна. Підтримай порядок або зменшуй інші кризи: нестача, війна й високі збори підсилюють провінційне невдоволення."
            hungry -> "Заплануй «Резерви» і запусти час. Потім перевір, чи зникла нестача."
            wars.isNotEmpty() -> "Обери противника: можна виснажити його набігом або спробувати завершити війну миром."
            worst != null && worst.relation < -0.35 -> "Відносини з ${worst.name} небезпечні: посольство знижує напругу, війна відкриває фронт."
            fragile -> "Заплануй «Порядок» або «Свято» і дай світові час відреагувати."
            else -> "На хід є одна команда. Можна діяти всередині держави, через дипломатію або просто пропустити час."
        }
        return GameBriefing(
            headline = headline,
            pressure = pressure,
            hint = hint,
            wars = wars,
            allies = allies,
            neighbors = neighbors,
            latestEvent = latest,
            objective = objective(
                state = state,
                civilization = civilization,
                foodPer = foodPer,
                wars = wars,
                neighbors = neighbors,
                pendingDecisionTitle = pendingDecisionTitle,
                rank = rank,
                internalPressure = internalPressure,
                rebellions = rebellions.size,
                worstProvinceName = worstProvinceName,
            ),
        )
    }

    fun neighborsOf(state: LivingPlanetState, civilizationId: String): List<NeighborStanding> {
        val names = state.civilizations.associate { it.id to it.name }
        return state.civilizations.filter { it.id != civilizationId }.map { other ->
            val relation = state.relations.firstOrNull { it.matches(civilizationId, other.id) }?.value ?: 0.0
            val atWar = state.wars.any { it.matches(civilizationId, other.id) }
            val allied = state.alliances.any { it.matches(civilizationId, other.id) }
            NeighborStanding(
                civilizationId = other.id,
                name = names[other.id] ?: other.name,
                relation = relation,
                atWar = atWar,
                allied = allied,
                status = when {
                    atWar -> "війна"
                    allied -> "союз"
                    relation < -0.45 -> "ворожість"
                    relation < -0.12 -> "холод"
                    relation > 0.55 -> "дружба"
                    else -> "нейтралітет"
                },
            )
        }.sortedWith(compareByDescending<NeighborStanding> { it.atWar }.thenBy { it.relation })
    }

    fun defaultCounterpartId(state: LivingPlanetState, civilizationId: String): String? {
        val neighbors = neighborsOf(state, civilizationId)
        return neighbors.firstOrNull { it.atWar }?.civilizationId
            ?: neighbors.minByOrNull { it.relation }?.civilizationId
    }

    private fun objective(
        state: LivingPlanetState,
        civilization: Civilization,
        foodPer: Double,
        wars: List<String>,
        neighbors: List<NeighborStanding>,
        pendingDecisionTitle: String?,
        rank: Int,
        internalPressure: Double,
        rebellions: Int,
        worstProvinceName: String?,
    ): GameObjective {
        val leader = state.civilizations.maxByOrNull { it.population }
        val hostile = neighbors.firstOrNull { !it.atWar && it.relation < -0.45 }
        return when {
            pendingDecisionTitle != null -> GameObjective(
                title = "Виріши історичну розвилку",
                detail = pendingDecisionTitle,
                meter = "час чекає на ваш вибір",
                complete = false,
            )
            rebellions > 0 -> GameObjective(
                title = "Не дай державі розколотися",
                detail = "Повстання${worstProvinceName?.let { " у $it" } ?: ""} вже відкрите. Якщо центр не відновить контроль, провінція може створити окрему державу.",
                meter = "$rebellions активн. повстань · напруга ${String.format("%.0f%%", internalPressure * 100.0)}",
                complete = false,
            )
            internalPressure >= 0.68 -> GameObjective(
                title = "Заспокой провінції та еліти",
                detail = "Внутрішня напруга наближається до рівня відкритого заколоту. Війна, нестача й податковий тиск можуть прискорити кризу.",
                meter = "внутрішня напруга ${String.format("%.0f%%", internalPressure * 100.0)}",
                complete = false,
            )
            foodPer < 0.45 -> GameObjective(
                title = "Відверни голод",
                detail = "Запасів замало для населення. Поповни резерви або ризикуй втратити людей під час наступного ходу.",
                meter = "їжа на особу ${String.format("%.2f", foodPer)} / 0.45",
                complete = false,
            )
            civilization.stability < 0.38 -> GameObjective(
                title = "Втримай державу",
                detail = "Низька стабільність робить будь-яку кризу небезпечнішою. Підтримай порядок або проведи свято.",
                meter = "стабільність ${String.format("%.0f%%", civilization.stability * 100)} / 38%",
                complete = false,
            )
            wars.isNotEmpty() -> GameObjective(
                title = "Визнач стратегію війни",
                detail = "Противник: ${wars.joinToString(", ")}. Обери між виснаженням ворога, миром або внутрішньою підготовкою.",
                meter = "${wars.size} активн. воєн",
                complete = false,
            )
            hostile != null -> GameObjective(
                title = "Виріши проблему з ${hostile.name}",
                detail = "Посольство поступово поліпшує відносини; війна різко змінює правила гри.",
                meter = "відносини ${relationPercent(hostile.relation)} / +30 для союзу",
                complete = false,
            )
            rank > 1 && leader != null -> GameObjective(
                title = "Наздожени ${leader.name}",
                detail = "Зараз ви не перші за населенням. Резерви допомагають пережити зростання, а розвиток дає довгу перевагу.",
                meter = "місце $rank з ${state.civilizations.size}",
                complete = false,
            )
            civilization.technology < 0.45 -> GameObjective(
                title = "Підніми рівень розвитку",
                detail = "Інвестуй у дослідження або дай державі розвиватися природно, зберігаючи ресурси для криз.",
                meter = "розвиток ${String.format("%.0f%%", civilization.technology * 100)} / 45%",
                complete = false,
            )
            else -> GameObjective(
                title = "Сформуй власну довгу стратегію",
                detail = "Гострої кризи немає. Можеш будувати союзи, накопичувати казну, прискорювати розвиток або не втручатися.",
                meter = "стабільний період",
                complete = true,
            )
        }
    }

    private fun eventLabel(code: String): String = when (code) {
        "INTERVENTION_HARVEST_AID" -> "Резерви поповнено"
        "INTERVENTION_DROUGHT" -> "Посуха"
        "INTERVENTION_TECH_BOOST" -> "Інвестиція у розвиток"
        "INTERVENTION_STABILITY_SUPPORT" -> "Порядок зміцнено"
        "INTERVENTION_WAR_RAID" -> "Набіг"
        "INTERVENTION_FESTIVAL" -> "Свято"
        "INTERVENTION_EMBASSY" -> "Посольство"
        "WAR_STARTED" -> "Оголошено війну"
        "PEACE_TREATY" -> "Укладено мир"
        "ALLIANCE_FORMED" -> "Створено союз"
        "ALLIANCE_ENDED" -> "Союз розпався"
        "STATE_FOUNDED" -> "Постала нова держава"
        "SECESSION" -> "Провінція відокремилася"
        "TAXES_RAISED" -> "Податки підвищено"
        "TAXES_LOWERED" -> "Податки знижено"
        "PROVINCIAL_UNREST" -> "Провінційне невдоволення"
        "REBELLION_STARTED" -> "Почалося повстання"
        "REBELLION_SUPPRESSED" -> "Повстання придушено"
        "FOOD_SHORTAGE" -> "Нестача їжі"
        "SETTLEMENT_GROWTH" -> "Місто зросло"
        "CITY_CAPTURED" -> "Місто взято"
        else -> code.lowercase().replace('_', ' ')
    }

    private fun relationPercent(value: Double): String = String.format("%+.0f", value * 100)

    private fun foodBand(foodPer: Double): String = when {
        foodPer < 0.30 -> "критично мало"
        foodPer < 0.45 -> "нестача"
        foodPer < 0.80 -> "достатньо"
        else -> "в надлишку"
    }

    private fun stabilityBand(value: Double): String = when {
        value < 0.30 -> "розвал"
        value < 0.45 -> "напруга"
        value < 0.70 -> "тримається"
        else -> "міцний"
    }

    private fun pressureBand(value: Double): String = when {
        value >= 0.82 -> "розкол"
        value >= 0.68 -> "критична"
        value >= 0.48 -> "помітна"
        else -> "низька"
    }

    private fun techBand(value: Double): String = when {
        value < 0.20 -> "ранній"
        value < 0.45 -> "середній"
        value < 0.70 -> "високий"
        else -> "передовий"
    }

    private fun shortageBandLocal(value: Double?): String = when {
        value == null -> "невідомо"
        value > 0.55 -> "дефіцит"
        value > 0.30 -> "тісно"
        else -> "спокійно"
    }
}
