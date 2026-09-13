package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState

/**
 * Durable simulation-facing policies created by selected chronicle choices.
 * Stored as namespaced civilization tags so they branch and save with the world.
 */
object HistoricalCommitmentEngine {
    private const val PREFIX = "history_policy:"
    private const val ERA_CHOICE_PREFIX = "era-choice:"

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

    /**
     * Applies both classic chronicle commitments and the durable material consequences of the
     * player's era choices. Era-choice tags are part of Civilization itself, so these effects
     * naturally survive save/load and fork with the timeline instead of living in UI state.
     */
    fun applyRecurring(state: LivingPlanetState, months: Int): LivingPlanetState {
        if (months <= 0) return state
        val years = months / 12.0
        val activeByCivilization = state.civilizations.associate { civilization ->
            civilization.id to active(civilization)
        }
        val eraEffects = state.civilizations.associate { civilization ->
            civilization.id to eraChoiceEffect(civilization.cultureTags)
        }
        if (
            activeByCivilization.values.all { it.isEmpty() } &&
            eraEffects.values.all(EraChoiceRecurringEffect::isZero)
        ) return state

        val civilizations = state.civilizations.map { civilization ->
            val policies = activeByCivilization[civilization.id].orEmpty().map { it.definition }
            val eraEffect = eraEffects[civilization.id] ?: EraChoiceRecurringEffect()
            if (policies.isEmpty() && eraEffect.isZero()) return@map civilization
            civilization.copy(
                technology = (
                    civilization.technology +
                        (policies.sumOf { it.annualTechnologyDelta } + eraEffect.annualTechnologyDelta) * years
                    ).coerceIn(0.0, 1.0),
                stability = (
                    civilization.stability +
                        (policies.sumOf { it.annualStabilityDelta } + eraEffect.annualStabilityDelta) * years
                    ).coerceIn(0.12, 0.98),
                treasury = (
                    civilization.treasury +
                        (policies.sumOf { it.annualTreasuryDelta } + eraEffect.annualTreasuryDelta) * years
                    ).coerceAtLeast(0.0),
            )
        }
        val settlements = state.settlements.map { settlement ->
            val policyFood = activeByCivilization[settlement.civilizationId]
                .orEmpty()
                .sumOf { it.definition.annualFoodPerPersonDelta }
            val eraFood = eraEffects[settlement.civilizationId]?.annualFoodPerPersonDelta ?: 0.0
            val foodPerPerson = policyFood + eraFood
            if (foodPerPerson == 0.0) settlement
            else settlement.copy(
                foodStock = (settlement.foodStock + settlement.population * foodPerPerson * years).coerceAtLeast(0.0),
            )
        }
        val relations = state.relations.map { relation ->
            val drift = (
                (eraEffects[relation.civilizationA]?.annualRelationDelta ?: 0.0) +
                    (eraEffects[relation.civilizationB]?.annualRelationDelta ?: 0.0)
                ) * years
            if (drift == 0.0) relation else relation.copy(value = (relation.value + drift).coerceIn(-1.0, 1.0))
        }
        return state.copy(civilizations = civilizations, settlements = settlements, relations = relations)
    }

    private fun eraChoiceEffect(tags: Set<String>): EraChoiceRecurringEffect {
        val choices = tags.asSequence()
            .filter { it.startsWith(ERA_CHOICE_PREFIX) }
            .mapNotNull { tag ->
                val parts = tag.removePrefix(ERA_CHOICE_PREFIX).split(':', limit = 2)
                if (parts.size == 2 && parts.all(String::isNotBlank)) EraChoice(parts[0], parts[1]) else null
            }
            .toList()
        if (choices.isEmpty()) return EraChoiceRecurringEffect()

        var effect = EraChoiceRecurringEffect()
        val breakthroughs = choices.count { it.family == "breakthrough" }.coerceAtMost(6)
        if (breakthroughs > 0) {
            // Discoveries accumulate forever, but the generic recurring bonus is capped so an old
            // civilization cannot snowball merely by having a long list of remembered inventions.
            effect += EraChoiceRecurringEffect(
                annualTechnologyDelta = breakthroughs * 0.00003,
                annualTreasuryDelta = breakthroughs * -0.0015,
            )
        }
        if (choices.any { it.family == "society" }) {
            effect += EraChoiceRecurringEffect(annualStabilityDelta = 0.000025, annualTreasuryDelta = -0.003)
        }
        if (choices.any { it.family == "mobility" }) {
            effect += EraChoiceRecurringEffect(annualFoodPerPersonDelta = 0.000035)
        }

        choices.forEach { choice ->
            effect += when (choice.slug) {
                "fire" -> EraChoiceRecurringEffect(
                    annualStabilityDelta = 0.000025,
                    annualFoodPerPersonDelta = 0.000080,
                )
                "stone_tools" -> EraChoiceRecurringEffect(
                    annualTechnologyDelta = 0.000025,
                    annualFoodPerPersonDelta = 0.000070,
                )
                "predator_hunters" -> EraChoiceRecurringEffect(
                    annualStabilityDelta = -0.000020,
                    annualFoodPerPersonDelta = 0.000300,
                    annualRelationDelta = -0.000025,
                )
                "plant_foragers" -> EraChoiceRecurringEffect(
                    annualStabilityDelta = 0.000025,
                    annualFoodPerPersonDelta = 0.000235,
                )
                "river_fishers" -> EraChoiceRecurringEffect(annualFoodPerPersonDelta = 0.000275)
                "animal_taming" -> EraChoiceRecurringEffect(
                    annualStabilityDelta = 0.000020,
                    annualFoodPerPersonDelta = 0.000115,
                )
                "ritual_culture" -> EraChoiceRecurringEffect(
                    annualStabilityDelta = 0.000070,
                    annualTreasuryDelta = -0.004,
                )
                "nomadic_migration" -> EraChoiceRecurringEffect(
                    annualFoodPerPersonDelta = 0.000120,
                    annualTreasuryDelta = 0.003,
                    annualTechnologyDelta = -0.000006,
                )
                "permanent_camp" -> EraChoiceRecurringEffect(
                    annualStabilityDelta = 0.000025,
                    annualTechnologyDelta = 0.000012,
                )
                "plough", "iron_plough", "crop_rotation" -> EraChoiceRecurringEffect(
                    annualFoodPerPersonDelta = 0.000160,
                    annualTechnologyDelta = 0.000010,
                )
                "irrigation" -> EraChoiceRecurringEffect(
                    annualFoodPerPersonDelta = 0.000190,
                    annualTreasuryDelta = -0.004,
                )
                "grain_farming", "state_granaries" -> EraChoiceRecurringEffect(
                    annualFoodPerPersonDelta = 0.000210,
                    annualStabilityDelta = 0.000012,
                )
                "pastoralism" -> EraChoiceRecurringEffect(annualFoodPerPersonDelta = 0.000175)
                "seasonal_fairs", "urban_markets", "merchant_guilds", "coinage", "trade_caravans" ->
                    EraChoiceRecurringEffect(annualTreasuryDelta = 0.012, annualRelationDelta = 0.000018)
                "village_network", "paved_roads", "turnpikes", "railways", "motorization",
                "electric_transit", "autonomous_transport", "interplanetary_routes" ->
                    EraChoiceRecurringEffect(annualTreasuryDelta = 0.008, annualTechnologyDelta = 0.000010)
                "writing", "manuscript_schools", "mass_schooling", "radio", "computing" ->
                    EraChoiceRecurringEffect(annualTechnologyDelta = 0.000055, annualTreasuryDelta = -0.004)
                "iron_tools", "watermills", "steam_power", "mechanized_looms", "steel", "power_grid" ->
                    EraChoiceRecurringEffect(annualTechnologyDelta = 0.000070, annualTreasuryDelta = 0.004)
                "metal_weapons", "warrior_elite", "frontier_castles" ->
                    EraChoiceRecurringEffect(annualStabilityDelta = 0.000018, annualRelationDelta = -0.000030)
                "sewers", "sanitation", "public_clinics", "public_health" ->
                    EraChoiceRecurringEffect(annualStabilityDelta = 0.000045, annualTreasuryDelta = -0.006)
                "industrial_agriculture", "chemical_farming", "precision_farming" ->
                    EraChoiceRecurringEffect(annualFoodPerPersonDelta = 0.000260, annualTechnologyDelta = 0.000020)
                "processed_food", "cold_chain", "synthetic_food" ->
                    EraChoiceRecurringEffect(annualFoodPerPersonDelta = 0.000205, annualTreasuryDelta = 0.006)
                "biotech" -> EraChoiceRecurringEffect(
                    annualTechnologyDelta = 0.000075,
                    annualFoodPerPersonDelta = 0.000070,
                )
                "open_networks", "global_shipping" ->
                    EraChoiceRecurringEffect(annualTreasuryDelta = 0.014, annualRelationDelta = 0.000030)
                "algorithmic_governance", "ai_coordination" ->
                    EraChoiceRecurringEffect(annualTechnologyDelta = 0.000065, annualStabilityDelta = 0.000025)
                "fusion" -> EraChoiceRecurringEffect(annualTechnologyDelta = 0.000090, annualTreasuryDelta = 0.010)
                "asteroid_mining" -> EraChoiceRecurringEffect(annualTechnologyDelta = 0.000045, annualTreasuryDelta = 0.018)
                "closed_ecologies", "engineered_food" ->
                    EraChoiceRecurringEffect(annualFoodPerPersonDelta = 0.000320, annualTechnologyDelta = 0.000035)
                "orbital_habitats" -> EraChoiceRecurringEffect(
                    annualTechnologyDelta = 0.000050,
                    annualStabilityDelta = 0.000020,
                )
                else -> EraChoiceRecurringEffect()
            }
        }
        return effect
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

    private data class EraChoice(val family: String, val slug: String)

    private data class EraChoiceRecurringEffect(
        val annualTechnologyDelta: Double = 0.0,
        val annualStabilityDelta: Double = 0.0,
        val annualTreasuryDelta: Double = 0.0,
        val annualFoodPerPersonDelta: Double = 0.0,
        val annualRelationDelta: Double = 0.0,
    ) {
        fun isZero(): Boolean =
            annualTechnologyDelta == 0.0 && annualStabilityDelta == 0.0 && annualTreasuryDelta == 0.0 &&
                annualFoodPerPersonDelta == 0.0 && annualRelationDelta == 0.0

        operator fun plus(other: EraChoiceRecurringEffect): EraChoiceRecurringEffect = EraChoiceRecurringEffect(
            annualTechnologyDelta = annualTechnologyDelta + other.annualTechnologyDelta,
            annualStabilityDelta = annualStabilityDelta + other.annualStabilityDelta,
            annualTreasuryDelta = annualTreasuryDelta + other.annualTreasuryDelta,
            annualFoodPerPersonDelta = annualFoodPerPersonDelta + other.annualFoodPerPersonDelta,
            annualRelationDelta = annualRelationDelta + other.annualRelationDelta,
        )
    }
}
