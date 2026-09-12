package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState

/**
 * Durable simulation-facing policies created by selected chronicle choices.
 * Stored as namespaced civilization tags so they branch and save with the world.
 */
object HistoricalCommitmentEngine {
    private const val PREFIX = "history_policy:"

    private val definitions = listOf(
        StructuralCommitmentDefinition(
            id = "feed-growth",
            family = "founding",
            titleUk = "Спільні резерви",
            benefitUk = "Поселення постійно накопичують додатковий продовольчий запас.",
            recurringCostUk = "Утримання резервів відтягує частину державного ресурсу.",
            annualTreasuryDelta = -0.035,
            annualFoodPerPersonDelta = 0.0015,
        ),
        StructuralCommitmentDefinition(
            id = "secure-order",
            family = "founding",
            titleUk = "Ставка на порядок",
            benefitUk = "Влада повільно нарощує стійкість держави.",
            recurringCostUk = "Підтримка адміністративного порядку постійно коштує казні.",
            annualStabilityDelta = 0.00025,
            annualTreasuryDelta = -0.035,
        ),
        StructuralCommitmentDefinition(
            id = "fund-craft",
            family = "founding",
            titleUk = "Ремісничий курс",
            benefitUk = "Ремісничі осередки дають довгий технологічний приріст.",
            recurringCostUk = "Майстерні потребують постійного фінансування.",
            annualTechnologyDelta = 0.00012,
            annualTreasuryDelta = -0.050,
        ),
        StructuralCommitmentDefinition(
            id = "peace-stability",
            family = "peace",
            titleUk = "Закріплення миру",
            benefitUk = "Післявоєнний порядок повільно підсилює стабільність.",
            recurringCostUk = "Мирні гарантії та відновлення забирають частину казни.",
            annualStabilityDelta = 0.00018,
            annualTreasuryDelta = -0.025,
        ),
        StructuralCommitmentDefinition(
            id = "peace-reserves",
            family = "peace",
            titleUk = "Післявоєнні резерви",
            benefitUk = "Держава системно поповнює запаси поселень.",
            recurringCostUk = "Резервна система потребує постійних витрат.",
            annualTreasuryDelta = -0.030,
            annualFoodPerPersonDelta = 0.0010,
        ),
        StructuralCommitmentDefinition(
            id = "era-push",
            family = "era",
            titleUk = "Безперервний технологічний ривок",
            benefitUk = "Технологічний розвиток має постійний додатковий імпульс.",
            recurringCostUk = "Швидкі зміни трохи підточують суспільну стійкість.",
            annualTechnologyDelta = 0.00018,
            annualStabilityDelta = -0.00008,
        ),
        StructuralCommitmentDefinition(
            id = "era-consolidate",
            family = "era",
            titleUk = "Консолідація епохи",
            benefitUk = "Суспільство краще засвоює зміни й повільно стабілізується.",
            recurringCostUk = "Підтримка переходу вимагає регулярних витрат.",
            annualStabilityDelta = 0.00022,
            annualTreasuryDelta = -0.030,
        ),
        StructuralCommitmentDefinition(
            id = "rule-legitimacy",
            family = "rule",
            titleUk = "Легітимізація династії",
            benefitUk = "Влада отримує довгу стабілізаційну перевагу.",
            recurringCostUk = "Двір і система лояльності постійно коштують казні.",
            annualStabilityDelta = 0.00018,
            annualTreasuryDelta = -0.035,
        ),
        StructuralCommitmentDefinition(
            id = "rule-reform",
            family = "rule",
            titleUk = "Реформаторський курс",
            benefitUk = "Реформи повільно прискорюють технологічний розвиток.",
            recurringCostUk = "Постійна перебудова трохи знижує стабільність.",
            annualTechnologyDelta = 0.00010,
            annualStabilityDelta = -0.00005,
        ),
        StructuralCommitmentDefinition(
            id = "evo-integrate",
            family = "evolution",
            titleUk = "Інтеграція нової лінії",
            benefitUk = "Держава повільно гасить напругу навколо біологічних змін.",
            recurringCostUk = "Інтеграційні механізми потребують постійного ресурсу.",
            annualStabilityDelta = 0.00010,
            annualTreasuryDelta = -0.020,
        ),
        StructuralCommitmentDefinition(
            id = "evo-study",
            family = "evolution",
            titleUk = "Дослідження нової лінії",
            benefitUk = "Дослідження дають довгий технологічний бонус.",
            recurringCostUk = "Пріоритет досліджень створює невеликий соціальний тиск.",
            annualTechnologyDelta = 0.00008,
            annualStabilityDelta = -0.00003,
        ),
    )

    private val byId = definitions.associateBy { it.id }

    fun definition(choiceId: String): StructuralCommitmentDefinition? = byId[choiceId]

    fun activate(state: LivingPlanetState, civilizationId: String, choiceId: String, originTick: Long): LivingPlanetState {
        val definition = byId[choiceId] ?: return state
        val familyPrefix = "$PREFIX${definition.family}:"
        val tag = "$PREFIX${definition.family}:${definition.id}:$originTick"
        return state.copy(
            civilizations = state.civilizations.map { civilization ->
                if (civilization.id != civilizationId) return@map civilization
                civilization.copy(
                    cultureTags = civilization.cultureTags
                        .filterNot { it.startsWith(familyPrefix) }
                        .toSet() + tag,
                )
            },
        )
    }

    fun active(civilization: Civilization): List<ActiveStructuralCommitment> =
        civilization.cultureTags.mapNotNull(::parseTag)

    fun applyRecurring(state: LivingPlanetState, months: Int): LivingPlanetState {
        if (months <= 0) return state
        val years = months / 12.0
        val activeByCivilization = state.civilizations.associate { civilization ->
            civilization.id to active(civilization)
        }
        if (activeByCivilization.values.all { it.isEmpty() }) return state

        val civilizations = state.civilizations.map { civilization ->
            val policies = activeByCivilization[civilization.id].orEmpty().map { it.definition }
            if (policies.isEmpty()) return@map civilization
            civilization.copy(
                technology = (civilization.technology + policies.sumOf { it.annualTechnologyDelta } * years).coerceIn(0.0, 1.0),
                stability = (civilization.stability + policies.sumOf { it.annualStabilityDelta } * years).coerceIn(0.12, 0.98),
                treasury = (civilization.treasury + policies.sumOf { it.annualTreasuryDelta } * years).coerceAtLeast(0.0),
            )
        }
        val settlements = state.settlements.map { settlement ->
            val foodPerPerson = activeByCivilization[settlement.civilizationId]
                .orEmpty()
                .sumOf { it.definition.annualFoodPerPersonDelta }
            if (foodPerPerson == 0.0) settlement
            else settlement.copy(
                foodStock = (settlement.foodStock + settlement.population * foodPerPerson * years).coerceAtLeast(0.0),
            )
        }
        return state.copy(civilizations = civilizations, settlements = settlements)
    }

    private fun parseTag(tag: String): ActiveStructuralCommitment? {
        if (!tag.startsWith(PREFIX)) return null
        val parts = tag.removePrefix(PREFIX).split(':')
        if (parts.size != 3) return null
        val family = parts[0]
        val definition = byId[parts[1]] ?: return null
        if (definition.family != family) return null
        val originTick = parts[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        return ActiveStructuralCommitment(definition, originTick)
    }
}
