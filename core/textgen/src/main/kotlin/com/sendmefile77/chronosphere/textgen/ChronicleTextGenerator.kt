package com.sendmefile77.chronosphere.textgen

import com.sendmefile77.chronosphere.simulation.SimulationEvent

class ChronicleTextGenerator {
    fun describe(event: SimulationEvent): String = when (event.code) {
        "SETTLEMENT_FOUNDED" -> "${event.facts["settlement"] ?: "Поселення"} заснувала цивілізація ${event.facts["civilization"] ?: "невідомого народу"}."
        "SETTLEMENT_GROWTH" -> {
            val settlement = event.facts["settlement"] ?: "Поселення"
            val population = event.numbers["population"]?.toLong()
            if (population != null) "$settlement виросло приблизно до $population мешканців." else "$settlement увійшло в новий період зростання."
        }
        "COLONY_FOUNDED" -> "${event.facts["settlement"] ?: "Нове поселення"} заснували переселенці з ${event.facts["parent"] ?: "старішого міста"}."
        "FOOD_SHORTAGE" -> "${event.facts["settlement"] ?: "Поселення"} зіткнулося з нестачею продовольства."
        "MIGRATION" -> "Близько ${event.numbers["people"]?.toLong() ?: 0L} людей переселилися з ${event.facts["from"] ?: "одного поселення"} до ${event.facts["to"] ?: "іншого"}."
        "WAR_STARTED" -> "${event.facts["a"] ?: "Одна держава"} та ${event.facts["b"] ?: "інша держава"} вступили у відкриту війну."
        "WAR_CASUALTIES" -> "Бої біля ${event.facts["settlementA"] ?: "кордону"} та ${event.facts["settlementB"] ?: "сусіднього фронту"} призвели приблизно до ${event.numbers["casualties"]?.toLong() ?: 0L} втрат."
        "CITY_CAPTURED" -> "${event.facts["settlement"] ?: "Прикордонне місто"} перейшло під контроль іншої держави після успішного наступу."
        "PEACE_TREATY" -> {
            val winner = event.facts["winner"] ?: "без однозначного переможця"
            "${event.facts["a"] ?: "Одна держава"} та ${event.facts["b"] ?: "інша"} уклали мир. Підсумок: $winner. За час війни змінили власника ${event.numbers["captures"]?.toInt() ?: 0} міст."
        }
        "ALLIANCE_FORMED" -> "${event.facts["a"] ?: "Одна держава"} та ${event.facts["b"] ?: "інша"} уклали союз."
        "ALLIANCE_ENDED" -> "Союз між ${event.facts["a"] ?: "двома державами"} та ${event.facts["b"] ?: "їхнім партнером"} припинив існування."
        "ECONOMIC_SHORTAGE" -> {
            val shortage = event.numbers["shortage"]?.let { String.format("%.0f%%", it * 100.0) } ?: "значний"
            "У державі ${event.facts["civilization"] ?: "невідомого народу"} сформувався системний дефіцит ресурсів ($shortage)."
        }
        "TRADE_FLOW" -> {
            val good = when (event.facts["good"]) {
                "FOOD" -> "продовольства"
                "TIMBER" -> "деревини"
                "STONE" -> "каменю"
                "METAL" -> "металу"
                "FUEL" -> "палива"
                "CRAFTS" -> "ремісничих виробів"
                else -> "товарів"
            }
            "${event.facts["exporter"] ?: "Одна держава"} розширила постачання $good до держави ${event.facts["importer"] ?: "сусіда"}."
        }
        "ERA_ADVANCED" -> "Держава ${event.facts["civilization"] ?: "невідомого народу"} увійшла в нову епоху — ${event.facts["era"] ?: "наступний технологічний уклад"}."
        "PERSON_DIED" -> {
            val age = event.numbers["age"]?.toInt()
            if (age != null) "Померла історична постать ${event.facts["person"] ?: "невідома особа"} у віці $age років."
            else "Померла історична постать ${event.facts["person"] ?: "невідома особа"}."
        }
        "RULER_SUCCEEDED" -> "${event.facts["person"] ?: "Новий правитель"} очолив державу ${event.facts["civilization"] ?: "невідомого народу"}."
        "DYNASTY_FOUNDED" -> "${event.facts["person"] ?: "Нова постать"} започаткував нову правлячу династію в державі ${event.facts["civilization"] ?: "невідомого народу"}."
        "RULER_PARTNERSHIP_FORMED" -> "Правитель ${event.facts["ruler"] ?: "невідома особа"} утворив династичний союз із ${event.facts["partner"] ?: "новим партнером"}."
        "DYNASTIC_BIRTH" -> "У правлячому домі держави ${event.facts["civilization"] ?: "невідомого народу"} народився новий династ — ${event.facts["person"] ?: "дитина"}."
        "ADULT_SOCIAL_EVENT" -> {
            val eventName = event.facts["eventCode"]?.replace('_', ' ')?.lowercase() ?: "приватна соціальна подія"
            val participants = event.facts["participants"]?.takeIf { it.isNotBlank() }
            buildString {
                append("У державі ${event.facts["civilization"] ?: "невідомого народу"} відбулася подія: $eventName")
                if (participants != null) append(". Учасники: $participants")
                append('.')
            }
        }
        "LINEAGE_FOUNDED" -> "У поселенні ${event.facts["settlement"] ?: "невідомому"} сформувалася окрема популяційна лінія — ${event.facts["lineage"] ?: "нова лінія"}."
        "BIOLOGICAL_DIVERGENCE" -> "Лінія ${event.facts["lineage"] ?: "населення"} у ${event.facts["settlement"] ?: "регіоні"} досягла нового рівня біологічного розходження: ${event.facts["rank"] ?: "морф"}."
        "STRUCTURAL_MUTATION" -> "У лінії ${event.facts["lineage"] ?: "населення"} закріпилася рідкісна структурна зміна тіла (${event.facts["bodyPlan"] ?: "новий план тіла"})."
        "HYBRID_LINEAGE_FORMED" -> "У ${event.facts["settlement"] ?: "поселенні"} стабілізувалася гібридна лінія ${event.facts["lineage"] ?: "населення"}, що поєднує походження ${event.facts["primary"] ?: "першої лінії"} та ${event.facts["secondary"] ?: "другої лінії"}."
        "CULTURAL_ASSIMILATION" -> "У ${event.facts["settlement"] ?: "поселенні"} культурна ідентичність більшості змістилася до панівної культури, не змінюючи біологічного походження населення."
        "INTERVENTION_HARVEST_AID" -> "Зовнішнє втручання посилило врожайність у державі ${event.facts["civilization"] ?: "невідомого народу"}."
        "INTERVENTION_DROUGHT" -> "Штучно спричинена посуха вдарила по державі ${event.facts["civilization"] ?: "невідомого народу"}, скоротивши запаси продовольства та населення."
        "INTERVENTION_TECH_BOOST" -> "Держава ${event.facts["civilization"] ?: "невідомого народу"} отримала різкий технологічний імпульс."
        "INTERVENTION_STABILITY_SUPPORT" -> "Політичну стабільність держави ${event.facts["civilization"] ?: "невідомого народу"} штучно посилили."
        else -> event.code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } + "."
    }
}
