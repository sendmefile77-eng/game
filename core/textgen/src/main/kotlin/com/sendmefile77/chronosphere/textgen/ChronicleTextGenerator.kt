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
        "INTERVENTION_HARVEST_AID" -> "Зовнішнє втручання посилило врожайність у державі ${event.facts["civilization"] ?: "невідомого народу"}."
        "INTERVENTION_DROUGHT" -> "Штучно спричинена посуха вдарила по державі ${event.facts["civilization"] ?: "невідомого народу"}, скоротивши запаси продовольства та населення."
        "INTERVENTION_TECH_BOOST" -> "Держава ${event.facts["civilization"] ?: "невідомого народу"} отримала різкий технологічний імпульс."
        "INTERVENTION_STABILITY_SUPPORT" -> "Політичну стабільність держави ${event.facts["civilization"] ?: "невідомого народу"} штучно посилили."
        else -> event.code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } + "."
    }
}
