package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import kotlin.math.abs

internal const val DIRECT_ACTION_STRENGTH = 0.65
internal const val EVOLUTION_ACTION_COST = 28.0
internal const val TURN_YEARS = 100
internal const val TURN_MONTHS = TURN_YEARS * 12

internal data class GameplayActionGate(
    val enabled: Boolean,
    val reasonUk: String? = null,
    val treasuryCost: Double = 0.0,
)

internal data class GameplaySnapshot(
    val civilizationId: String,
    val civilizationName: String,
    val tick: Long,
    val population: Long,
    val stability: Double,
    val technology: Double,
    val treasury: Double,
    val food: Double,
    val wars: Int,
    val knownEventIds: Set<String>,
)

internal data class GameplayTurnReport(
    val civilizationId: String,
    val civilizationName: String,
    val yearsAdvanced: Int,
    val survived: Boolean,
    val populationDelta: Long,
    val stabilityDelta: Double,
    val technologyDelta: Double,
    val treasuryDelta: Double,
    val foodDelta: Double,
    val warsBefore: Int,
    val warsAfter: Int,
    val highlights: List<String>,
) {
    val headlineUk: String
        get() = when {
            !survived -> "$civilizationName зникла з політичної карти"
            stabilityDelta <= -0.12 -> "$civilizationName пережила важкий період"
            populationDelta < 0L && foodDelta < 0.0 -> "$civilizationName втратила людей і запаси"
            technologyDelta >= 0.08 -> "$civilizationName зробила помітний ривок"
            stabilityDelta >= 0.08 -> "$civilizationName стала стійкішою"
            populationDelta > 0L -> "$civilizationName продовжила зростати"
            else -> "$civilizationName пройшла ще один відрізок історії"
        }
}

internal object GameplayTurnReportStore {
    @Volatile private var reports: Map<String, GameplayTurnReport> = emptyMap()

    fun replace(value: Map<String, GameplayTurnReport>) {
        reports = value
    }

    fun latestFor(civilizationId: String): GameplayTurnReport? = reports[civilizationId]

    fun clear() {
        reports = emptyMap()
    }
}

internal object GameplayLoop {
    private val diplomaticKinds = setOf(
        InterventionKind.WAR_RAID,
        InterventionKind.DECLARE_WAR,
        InterventionKind.MAKE_PEACE,
        InterventionKind.FORM_ALLIANCE,
        InterventionKind.EMBASSY,
    )

    fun turnSourceId(tick: Long): String = "player-turn-$tick"

    /** One direct player command globally per current simulation tick. Switching states cannot reset it. */
    fun actionSpent(state: LivingPlanetState): Boolean = state.recentEvents.any { event ->
        event.tick == state.tick && event.id.startsWith("player-")
    }

    fun queuedAction(state: LivingPlanetState): PendingChronicleDecision? =
        ChronicleDecisionMailbox.pendingFor(turnSourceId(state.tick))

    fun cancelQueuedAction(state: LivingPlanetState): PendingChronicleDecision? =
        ChronicleDecisionMailbox.remove(turnSourceId(state.tick))

    fun queueAction(
        state: LivingPlanetState,
        civilizationId: String,
        kind: InterventionKind,
        titleUk: String,
        effectUk: String,
        riskUk: String,
        counterpartCivilizationId: String? = null,
    ) {
        ChronicleDecisionMailbox.enqueue(
            ChronicleDecisionOption(
                id = kind.name.lowercase(),
                sourceEventId = turnSourceId(state.tick),
                titleUk = titleUk,
                effectUk = effectUk,
                riskUk = riskUk,
                kind = kind,
                targetCivilizationId = civilizationId,
                strength = DIRECT_ACTION_STRENGTH,
                counterpartCivilizationId = counterpartCivilizationId,
            ),
        )
    }

    fun treasuryCost(kind: InterventionKind, strength: Double = DIRECT_ACTION_STRENGTH): Double = when (kind) {
        InterventionKind.HARVEST_AID -> 8.0 + strength * 12.0
        InterventionKind.TECHNOLOGY_BOOST -> 12.0 + strength * 18.0
        InterventionKind.STABILITY_SUPPORT -> 7.0 + strength * 10.0
        InterventionKind.FESTIVAL -> 6.0 + strength * 10.0
        InterventionKind.WAR_RAID -> 8.0 + strength * 14.0
        InterventionKind.DECLARE_WAR -> 10.0 + strength * 12.0
        InterventionKind.MAKE_PEACE -> 4.0
        InterventionKind.FORM_ALLIANCE -> 8.0 + strength * 8.0
        InterventionKind.EMBASSY -> 5.0 + strength * 7.0
        InterventionKind.DROUGHT -> 0.0
    }

    fun chargeDomesticCost(
        state: LivingPlanetState,
        civilizationId: String,
        kind: InterventionKind,
        strength: Double = DIRECT_ACTION_STRENGTH,
    ): LivingPlanetState {
        val cost = when (kind) {
            InterventionKind.HARVEST_AID,
            InterventionKind.TECHNOLOGY_BOOST,
            InterventionKind.STABILITY_SUPPORT -> treasuryCost(kind, strength)
            else -> 0.0
        }
        if (cost <= 0.0) return state
        return state.copy(
            civilizations = state.civilizations.map { civilization ->
                if (civilization.id == civilizationId) {
                    civilization.copy(treasury = (civilization.treasury - cost).coerceAtLeast(0.0))
                } else civilization
            },
        )
    }

    fun chargeEvolutionCost(state: LivingPlanetState, civilizationId: String): LivingPlanetState = state.copy(
        civilizations = state.civilizations.map { civilization ->
            if (civilization.id == civilizationId) {
                civilization.copy(treasury = (civilization.treasury - EVOLUTION_ACTION_COST).coerceAtLeast(0.0))
            } else civilization
        },
    )

    fun gate(
        state: LivingPlanetState,
        civilizationId: String,
        kind: InterventionKind,
        targetCivilizationId: String?,
        hasPendingDecision: Boolean,
    ): GameplayActionGate {
        val actor = state.civilizations.firstOrNull { it.id == civilizationId }
            ?: return GameplayActionGate(false, "Держава більше не існує")
        val cost = treasuryCost(kind)
        if (hasPendingDecision) return GameplayActionGate(false, "Спочатку прийміть рішення у Хроніці", cost)
        if (actionSpent(state)) return GameplayActionGate(false, "Команду цього ходу вже використано", cost)
        if (queuedAction(state) != null) return GameplayActionGate(false, "Команду вже заплановано — прокрутіть час або скасуйте її", cost)
        if (actor.treasury + 1e-9 < cost) {
            return GameplayActionGate(false, "Потрібно ${cost.toInt()} казни", cost)
        }
        if (kind !in diplomaticKinds) return GameplayActionGate(true, treasuryCost = cost)

        val targetId = targetCivilizationId
            ?: return GameplayActionGate(false, "Оберіть іншу державу", cost)
        if (targetId == civilizationId || state.civilizations.none { it.id == targetId }) {
            return GameplayActionGate(false, "Некоректна ціль", cost)
        }
        val atWar = state.wars.any { it.matches(civilizationId, targetId) }
        val allied = state.alliances.any { it.matches(civilizationId, targetId) }
        val relation = state.relations.firstOrNull { it.matches(civilizationId, targetId) }?.value ?: 0.0

        return when (kind) {
            InterventionKind.WAR_RAID -> if (atWar) GameplayActionGate(true, treasuryCost = cost)
                else GameplayActionGate(false, "Набіг доступний лише у війні", cost)
            InterventionKind.MAKE_PEACE -> if (atWar) GameplayActionGate(true, treasuryCost = cost)
                else GameplayActionGate(false, "Війни з цією державою немає", cost)
            InterventionKind.DECLARE_WAR -> if (!atWar) GameplayActionGate(true, treasuryCost = cost)
                else GameplayActionGate(false, "Війна вже триває", cost)
            InterventionKind.FORM_ALLIANCE -> when {
                atWar -> GameplayActionGate(false, "Спочатку укладіть мир", cost)
                allied -> GameplayActionGate(false, "Союз уже діє", cost)
                relation < 0.30 -> GameplayActionGate(false, "Для союзу потрібні відносини +30 або вище", cost)
                else -> GameplayActionGate(true, treasuryCost = cost)
            }
            InterventionKind.EMBASSY -> if (!atWar) GameplayActionGate(true, treasuryCost = cost)
                else GameplayActionGate(false, "Під час війни спочатку потрібен мир", cost)
            else -> GameplayActionGate(true, treasuryCost = cost)
        }
    }

    fun evolutionGate(
        state: LivingPlanetState,
        civilizationId: String,
        hasPendingDecision: Boolean,
    ): GameplayActionGate {
        val actor = state.civilizations.firstOrNull { it.id == civilizationId }
            ?: return GameplayActionGate(false, "Держава більше не існує", EVOLUTION_ACTION_COST)
        return when {
            hasPendingDecision -> GameplayActionGate(false, "Спочатку прийміть рішення у Хроніці", EVOLUTION_ACTION_COST)
            actionSpent(state) -> GameplayActionGate(false, "Команду цього ходу вже використано", EVOLUTION_ACTION_COST)
            queuedAction(state) != null -> GameplayActionGate(false, "Спочатку завершіть заплановану команду", EVOLUTION_ACTION_COST)
            actor.treasury < EVOLUTION_ACTION_COST -> GameplayActionGate(false, "Потрібно ${EVOLUTION_ACTION_COST.toInt()} казни", EVOLUTION_ACTION_COST)
            else -> GameplayActionGate(true, treasuryCost = EVOLUTION_ACTION_COST)
        }
    }

    fun snapshot(state: LivingPlanetState, civilizationId: String): GameplaySnapshot? {
        val civilization = state.civilizations.firstOrNull { it.id == civilizationId } ?: return null
        val settlements = state.settlements.filter { it.civilizationId == civilizationId }
        val wars = state.wars.count { it.civilizationA == civilizationId || it.civilizationB == civilizationId }
        return GameplaySnapshot(
            civilizationId = civilization.id,
            civilizationName = civilization.name,
            tick = state.tick,
            population = civilization.population,
            stability = civilization.stability,
            technology = civilization.technology,
            treasury = civilization.treasury,
            food = settlements.sumOf { it.foodStock },
            wars = wars,
            knownEventIds = state.recentEvents.mapTo(hashSetOf()) { it.id },
        )
    }

    fun report(before: GameplaySnapshot, after: LivingPlanetState, monthsAdvanced: Int): GameplayTurnReport {
        val civilization = after.civilizations.firstOrNull { it.id == before.civilizationId }
        val newEvents = after.recentEvents.filter { it.id !in before.knownEventIds }
            .filter { event -> event.actorIds.isEmpty() || before.civilizationId in event.actorIds }
            .takeLast(5)
            .map(::eventSummary)
            .distinct()
        if (civilization == null) {
            return GameplayTurnReport(
                civilizationId = before.civilizationId,
                civilizationName = before.civilizationName,
                yearsAdvanced = (monthsAdvanced / 12).coerceAtLeast(1),
                survived = false,
                populationDelta = -before.population,
                stabilityDelta = -before.stability,
                technologyDelta = 0.0,
                treasuryDelta = -before.treasury,
                foodDelta = -before.food,
                warsBefore = before.wars,
                warsAfter = 0,
                highlights = newEvents,
            )
        }
        val foodAfter = after.settlements.filter { it.civilizationId == before.civilizationId }.sumOf { it.foodStock }
        val warsAfter = after.wars.count { it.civilizationA == before.civilizationId || it.civilizationB == before.civilizationId }
        return GameplayTurnReport(
            civilizationId = before.civilizationId,
            civilizationName = civilization.name,
            yearsAdvanced = (monthsAdvanced / 12).coerceAtLeast(1),
            survived = true,
            populationDelta = civilization.population - before.population,
            stabilityDelta = civilization.stability - before.stability,
            technologyDelta = civilization.technology - before.technology,
            treasuryDelta = civilization.treasury - before.treasury,
            foodDelta = foodAfter - before.food,
            warsBefore = before.wars,
            warsAfter = warsAfter,
            highlights = newEvents,
        )
    }

    private fun eventSummary(event: SimulationEvent): String = when (event.code) {
        "WAR_STARTED" -> "Почалася війна${event.facts[\"b\"]?.let { \" з $it\" } ?: \"\"}"
        "PEACE_TREATY" -> "Укладено мир${event.facts[\"b\"]?.let { \" з $it\" } ?: \"\"}"
        "ALLIANCE_FORMED" -> "Створено союз${event.facts[\"b\"]?.let { \" з $it\" } ?: \"\"}"
        "CITY_CAPTURED" -> "Змінився контроль над містом ${event.facts[\"settlement\"] ?: \"\"}".trim()
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> "Загострився дефіцит ресурсів"
        "ERA_ADVANCED" -> "Держава перейшла до нової епохи"
        "RULER_SUCCEEDED" -> "До влади прийшов новий правитель"
        "SETTLEMENT_FOUNDED", "COLONY_FOUNDED" -> "Засновано нове поселення"
        "PLAYER_EVOLUTION_DIVERGENCE" -> "Відокремилася нова біологічна лінія"
        "PLAYER_STRUCTURAL_MUTATION" -> "Закріпилася структурна мутація"
        "PLAYER_HYBRIDIZATION" -> "Сформувалася гібридна лінія"
        else -> event.code.lowercase().replace('_', ' ')
    }

    fun signedLong(value: Long): String = when {
        value > 0 -> "+${compactNumber(value)}"
        value < 0 -> "−${compactNumber(abs(value))}"
        else -> "0"
    }

    fun signedDouble(value: Double, suffix: String = ""): String = when {
        value > 0.0005 -> "+${String.format(\"%.1f\", value)}$suffix"
        value < -0.0005 -> "−${String.format(\"%.1f\", abs(value))}$suffix"
        else -> "0$suffix"
    }
}
