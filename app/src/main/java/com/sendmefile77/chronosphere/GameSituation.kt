package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState

data class GameObjective(
    val title: String,
    val detail: String,
    val meter: String,
    val complete: Boolean,
)

data class GameBriefing(
    val headline: String,
    val pressure: String,
    val hint: String,
    val wars: List<String>,
    val allies: List<String>,
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
        val rank = state.civilizations.sortedByDescending { it.population }.indexOfFirst { it.id == civilization.id } + 1
        val era = economy?.economy(civilization.id)?.era?.displayNameUk ?: "рання епоха"

        val headline = when {
            wars.isNotEmpty() -> "${civilization.name} у війні з ${wars.joinToString(", ")}"
            hungry -> "${civilization.name} на межі голоду"
            fragile -> "${civilization.name} хитається: низька стабільність"
            else -> "${civilization.name} · $era · ${rank}-а за людністю"
        }
        val pressure = buildList {
            add("Їжа ${foodBand(foodPer)}")
            add("порядок ${stabilityBand(civilization.stability)}")
            add("розвиток ${techBand(civilization.technology)}")
            if (economy != null) add("ресурси ${shortageBandLocal(economy.economy(civilization.id)?.shortageIndex)}")
        }.joinToString(" · ")
        val hint = when {
            pendingDecisionTitle != null -> "У вкладці «Хроніка» чекає рішення: $pendingDecisionTitle"
            hungry -> "Натисни «Врожай», потім «+1 рік», щоб побачити, чи відійшов голод."
            wars.isNotEmpty() -> "Можна бити суперника кнопкою «Набіг» або зміцнити тил «Святом» / «Порядком»."
            fragile -> "«Свято» або «Порядок» піднімають стабільність. Потім прокрути +1 рік."
            else -> "Тикаєш державу на карті, втручаєшся, тоді крутиш час. Світ змінюється сам."
        }
        return GameBriefing(
            headline = headline,
            pressure = pressure,
            hint = hint,
            wars = wars,
            allies = allies,
            objective = objective(state, civilization, foodPer, wars, pendingDecisionTitle, rank),
        )
    }

    private fun objective(
        state: LivingPlanetState,
        civilization: Civilization,
        foodPer: Double,
        wars: List<String>,
        pendingDecisionTitle: String?,
        rank: Int,
    ): GameObjective {
        val leader = state.civilizations.maxByOrNull { it.population }
        return when {
            foodPer < 0.45 -> GameObjective(
                title = "Відверни голод",
                detail = "Запаси не покривають населення. Дай врожай і перевір через рік.",
                meter = "їжа на особу ${String.format("%.2f", foodPer)} / 0.45",
                complete = false,
            )
            civilization.stability < 0.38 -> GameObjective(
                title = "Втримай державу",
                detail = "Низька стабільність веде до занепаду. Підніми порядок святом або підтримкою.",
                meter = "стабільність ${String.format("%.0f%%", civilization.stability * 100)} / 38%",
                complete = false,
            )
            pendingDecisionTitle != null -> GameObjective(
                title = "Прийми рішення хроніки",
                detail = pendingDecisionTitle,
                meter = "відкрий вкладку «Хроніка»",
                complete = false,
            )
            wars.isNotEmpty() -> GameObjective(
                title = "Переживи війну",
                detail = "Ворог: ${wars.joinToString(", ")}. Набіг б'є їхні запаси, порядок тримає твій тил.",
                meter = "${wars.size} активн. воєн",
                complete = false,
            )
            rank > 1 && leader != null -> GameObjective(
                title = "Стань найлюднішою державою",
                detail = "Зараз попереду ${leader.name}. Врожай і час збільшують міста.",
                meter = "місце $rank з ${state.civilizations.size}",
                complete = false,
            )
            civilization.technology < 0.45 -> GameObjective(
                title = "Підніми розвиток",
                detail = "«Прорив» дає технологію одразу. Час закріплює її в епосі.",
                meter = "розвиток ${String.format("%.0f%%", civilization.technology * 100)} / 45%",
                complete = false,
            )
            else -> GameObjective(
                title = "Спостерігай свою історію",
                detail = "Держава тримається. Збережи момент у «Часі» і спробуй іншу гілку.",
                meter = "немає кризи",
                complete = true,
            )
        }
    }

    private fun foodBand(foodPer: Double): String = when {
        foodPer < 0.30 -> "критично мало"
        foodPer < 0.45 -> "нестача"
        foodPer < 0.80 -> "достатньо"
        else -> "в надлишку"
    }

    private fun stabilityBand(value: Double): String = when {
        value < 0.30 -> "розвал"
        value < 0.45 -> "напруга"
        value < 0.70 -> "тримання"
        else -> "міцний"
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
