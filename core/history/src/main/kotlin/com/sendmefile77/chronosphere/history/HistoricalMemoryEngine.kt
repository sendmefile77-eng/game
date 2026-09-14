package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent

/**
 * Compact causal memory above raw events. It never invents simulation facts.
 * It branches and saves with the timeline but never replaces authoritative simulation state.
 */
object HistoricalMemoryEngine {
    fun reconcile(
        previous: HistoricalMemoryState?,
        world: LivingPlanetState,
        people: PeopleState? = null,
        economy: EconomyState? = null,
    ): HistoricalMemoryState {
        val base = previous
            ?.takeIf { it.worldSeed == world.worldSeed && it.tick <= world.tick }
            ?: HistoricalMemoryState(worldSeed = world.worldSeed, tick = world.tick)

        val foundations = reconcileFoundations(base.foundations, world, people, economy)
        val commitments = reconcileCommitments(base.commitments, world)
        val newEvents = world.recentEvents
            .asSequence()
            .filter { event ->
                event.tick > base.lastProcessedTick ||
                    (event.tick == base.lastProcessedTick && event.id !in base.processedEventIdsAtLastTick)
            }
            .sortedWith(compareBy<SimulationEvent> { it.tick }.thenBy { it.id })
            .toList()

        var processes = base.processes
        var consequences = base.consequences
        var causalLinks = base.causalLinks
        var legacies = base.legacies
        newEvents.forEach { event ->
            val civilizationIds = civilizationIds(event, world, people)
            if (civilizationIds.isEmpty()) return@forEach
            causalLinks = HistoricalProcessReducer.applyEventToCausalLinks(
                existing = causalLinks,
                event = event,
                civilizationIds = civilizationIds,
                recentEvents = world.recentEvents,
            )
            processes = HistoricalProcessReducer.applyEventToProcesses(processes, event, civilizationIds, world)
            consequences = HistoricalProcessReducer.applyEventToConsequences(consequences, event, civilizationIds)
            legacies = HistoricalProcessReducer.applyEventToLegacies(legacies, event, civilizationIds)
        }

        processes = HistoricalProcessReducer.ageProcesses(processes, world, economy)
        consequences = HistoricalProcessReducer.resolveConsequences(consequences, world, economy, processes)
        val newestTick = maxOf(base.lastProcessedTick, newEvents.maxOfOrNull { it.tick } ?: base.lastProcessedTick)
        val newestIds = when {
            newestTick < 0L -> emptySet()
            newestTick == base.lastProcessedTick -> base.processedEventIdsAtLastTick + newEvents.filter { it.tick == newestTick }.map { it.id }
            else -> newEvents.filter { it.tick == newestTick }.mapTo(linkedSetOf()) { it.id }
        }

        return HistoricalMemoryState(
            worldSeed = world.worldSeed,
            tick = world.tick,
            foundations = foundations,
            processes = HistoricalProcessReducer.trimProcesses(processes),
            consequences = consequences.sortedBy { it.originTick }.takeLast(96),
            commitments = commitments.sortedBy { it.originTick }.takeLast(96),
            lastProcessedTick = newestTick,
            processedEventIdsAtLastTick = newestIds,
            causalLinks = causalLinks.sortedBy { it.effectTick }.takeLast(192),
            legacies = legacies.sortedBy { it.lastReinforcedTick }.takeLast(96),
        )
    }

    private fun reconcileFoundations(
        previous: List<CivilizationFoundation>,
        world: LivingPlanetState,
        people: PeopleState?,
        economy: EconomyState?,
    ): List<CivilizationFoundation> {
        val previousById = previous.associateBy { it.id }
        val currentCivilizationIds = world.civilizations.mapTo(hashSetOf()) { it.id }
        return buildList {
            world.civilizations.forEach { civilization ->
                val candidates = foundationCandidates(civilization, world, people, economy)
                candidates.forEach { candidate ->
                    val old = previousById[candidate.id]
                    add(
                        if (old != null && old.titleUk == candidate.titleUk) {
                            candidate.copy(originTick = old.originTick)
                        } else candidate
                    )
                }
            }
        }.filter { it.civilizationId in currentCivilizationIds }
    }

    private fun foundationCandidates(
        civilization: Civilization,
        world: LivingPlanetState,
        people: PeopleState?,
        economy: EconomyState?,
    ): List<CivilizationFoundation> {
        val settlements = world.settlements.filter { it.civilizationId == civilization.id }
        val totalPopulation = settlements.sumOf { it.population }.coerceAtLeast(1L)
        val largestShare = settlements.maxOfOrNull { it.population }?.toDouble()?.div(totalPopulation) ?: 1.0
        val settlementTitle = when {
            settlements.size <= 1 -> "Один головний осередок"
            largestShare >= 0.62 -> "Централізована мережа поселень"
            settlements.size >= 5 -> "Розгалужена мережа поселень"
            else -> "Кілька взаємозалежних осередків"
        }
        val settlementStrength = (0.35 + settlements.size.coerceAtMost(8) * 0.06 + largestShare * 0.18).coerceIn(0.0, 1.0)
        val settlement = CivilizationFoundation(
            id = "${civilization.id}:settlement",
            civilizationId = civilization.id,
            kind = FoundationKind.SETTLEMENT,
            titleUk = settlementTitle,
            benefitUk = if (largestShare >= 0.62) "Ресурси й влада легко концентруються в одному центрі." else "Кілька центрів дають стійкість і простір для міграції.",
            costUk = if (largestShare >= 0.62) "Втрата головного центру болісно б'є по всій системі." else "Розпорошені осередки важче координувати й захищати.",
            strength = settlementStrength,
            originTick = world.tick,
            lastChangedTick = world.tick,
        )

        val civEconomy = economy?.economy(civilization.id)
        val dominant = civEconomy?.production?.maxByOrNull { it.value }?.key
        val production = CivilizationFoundation(
            id = "${civilization.id}:production",
            civilizationId = civilization.id,
            kind = FoundationKind.PRODUCTION,
            titleUk = productionTitle(dominant),
            benefitUk = productionBenefit(dominant),
            costUk = productionCost(dominant),
            strength = productionStrength(civEconomy?.production?.values.orEmpty()),
            originTick = world.tick,
            lastChangedTick = world.tick,
        )

        val routes = economy?.routes.orEmpty().filter { it.exporterId == civilization.id || it.importerId == civilization.id }
        val third = if (routes.isNotEmpty()) {
            CivilizationFoundation(
                id = "${civilization.id}:exchange",
                civilizationId = civilization.id,
                kind = FoundationKind.EXCHANGE,
                titleUk = if (routes.size >= 4) "Торгова мережа" else "Зовнішній обмін",
                benefitUk = "Зовнішні маршрути розширюють доступ до дефіцитних ресурсів.",
                costUk = "Війна або розрив відносин може швидко вдарити по постачанню.",
                strength = (0.32 + routes.size.coerceAtMost(8) * 0.07).coerceIn(0.0, 1.0),
                originTick = world.tick,
                lastChangedTick = world.tick,
            )
        } else {
            val dynastyCount = people?.dynasties?.count { it.civilizationId == civilization.id } ?: 0
            CivilizationFoundation(
                id = "${civilization.id}:authority",
                civilizationId = civilization.id,
                kind = FoundationKind.AUTHORITY,
                titleUk = if (dynastyCount > 0 || "dynastic" in civilization.cultureTags) "Династична тяглість" else "Локальна політична тяглість",
                benefitUk = "Влада має впізнаваний центр прийняття рішень.",
                costUk = "Криза спадкоємності або низька стабільність швидко б'є по координації.",
                strength = civilization.stability.coerceIn(0.0, 1.0),
                originTick = world.tick,
                lastChangedTick = world.tick,
            )
        }
        return listOf(settlement, production, third)
    }

    private fun productionTitle(good: EconomicGood?): String = when (good) {
        EconomicGood.FOOD -> "Продовольча база"
        EconomicGood.TIMBER -> "Лісова спеціалізація"
        EconomicGood.STONE -> "Кам'яна спеціалізація"
        EconomicGood.METAL -> "Металургійна база"
        EconomicGood.FUEL -> "Паливна база"
        EconomicGood.CRAFTS -> "Реміснича спеціалізація"
        null -> "Локальне самозабезпечення"
    }

    private fun productionBenefit(good: EconomicGood?): String = when (good) {
        EconomicGood.FOOD -> "Власне виробництво підтримує населення та резерви."
        EconomicGood.TIMBER -> "Деревина підтримує будівництво, паливо й обмін."
        EconomicGood.STONE -> "Камінь підтримує довговічне будівництво й інфраструктуру."
        EconomicGood.METAL -> "Метал підсилює ремесла, інструменти й військову спроможність."
        EconomicGood.FUEL -> "Паливо підтримує виробництво й складніші технології."
        EconomicGood.CRAFTS -> "Ремесла створюють додану вартість і технологічний досвід."
        null -> "Господарство спирається переважно на власні місцеві можливості."
    }

    private fun productionCost(good: EconomicGood?): String = when (good) {
        EconomicGood.FOOD -> "Неврожай одразу перетворюється на системний ризик."
        EconomicGood.TIMBER -> "Виснаження лісів збільшує транспортні й будівельні витрати."
        EconomicGood.STONE -> "Видобуток потребує важкої праці й транспортної мережі."
        EconomicGood.METAL -> "Руда й паливо роблять виробництво залежним від ланцюгів постачання."
        EconomicGood.FUEL -> "Паливна залежність робить економіку чутливою до виснаження родовищ."
        EconomicGood.CRAFTS -> "Ремесла потребують стабільних потоків сировини та платоспроможного попиту."
        null -> "Слабка спеціалізація обмежує надлишок і далеку торгівлю."
    }

    private fun productionStrength(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.30
        val total = values.sum().takeIf { it > 0.0 } ?: return 0.30
        return (0.35 + (values.maxOrNull() ?: 0.0) / total * 0.50).coerceIn(0.0, 1.0)
    }

    private fun reconcileCommitments(
        previous: List<HistoricalCommitment>,
        world: LivingPlanetState,
    ): List<HistoricalCommitment> {
        val activeNow = buildMap<String, Pair<String, ActiveStructuralCommitment>> {
            world.civilizations.forEach { civilization ->
                HistoricalCommitmentEngine.active(civilization).forEach { active ->
                    put("${civilization.id}:${active.definition.family}", civilization.id to active)
                }
            }
        }
        val retained = previous.map { commitment ->
            val key = "${commitment.civilizationId}:${commitment.family}"
            val now = activeNow[key]
            if (commitment.status == HistoricalCommitmentStatus.ACTIVE &&
                (now == null || now.second.definition.id != commitment.choiceId || now.second.originTick != commitment.originTick)
            ) {
                commitment.copy(status = HistoricalCommitmentStatus.SUPERSEDED, endedTick = world.tick)
            } else commitment
        }.toMutableList()
        val existingActiveKeys = retained
            .filter { it.status == HistoricalCommitmentStatus.ACTIVE }
            .mapTo(hashSetOf()) { "${it.civilizationId}:${it.family}:${it.choiceId}:${it.originTick}" }
        activeNow.values.forEach { (civilizationId, active) ->
            val definition = active.definition
            val key = "$civilizationId:${definition.family}:${definition.id}:${active.originTick}"
            if (key !in existingActiveKeys) {
                retained += HistoricalCommitment(
                    id = "commitment:$key",
                    civilizationId = civilizationId,
                    family = definition.family,
                    choiceId = definition.id,
                    titleUk = definition.titleUk,
                    benefitUk = definition.benefitUk,
                    recurringCostUk = definition.recurringCostUk,
                    originTick = active.originTick,
                )
            }
        }
        return retained
    }

    private fun civilizationIds(
        event: SimulationEvent,
        world: LivingPlanetState,
        people: PeopleState?,
    ): Set<String> {
        val known = world.civilizations.mapTo(hashSetOf()) { it.id }
        val personToCivilization = people?.persons?.associate { it.id to it.civilizationId }.orEmpty()
        val result = linkedSetOf<String>()
        event.actorIds.forEach { actorId ->
            when {
                actorId in known -> result += actorId
                personToCivilization[actorId] in known -> result += personToCivilization.getValue(actorId)
            }
        }
        event.facts["targetCivilizationId"]?.takeIf { it in known }?.let(result::add)
        if (result.isEmpty()) {
            val byName = world.civilizations.associateBy { it.name }
            listOf("civilization", "a", "b").forEach { key ->
                event.facts[key]?.let { name -> byName[name]?.id?.let(result::add) }
            }
        }
        return result
    }
}
