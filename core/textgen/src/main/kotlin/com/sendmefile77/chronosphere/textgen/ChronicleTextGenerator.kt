package com.sendmefile77.chronosphere.textgen

import com.sendmefile77.chronosphere.simulation.SimulationEvent
import kotlin.math.abs

/** Deterministic, local narrative derived only from facts already present in a simulation event. */
data class ChronicleNarrative(
    val title: String,
    val hook: String,
    val body: String,
    val significance: String,
    val changes: List<String> = emptyList(),
)

class ChronicleTextGenerator {
    fun describe(event: SimulationEvent): String = narrative(event).body

    fun narrative(event: SimulationEvent): ChronicleNarrative = when (event.code) {
        "SETTLEMENT_FOUNDED" -> {
            val settlement = fact(event, "settlement", "Нове поселення")
            val civilization = fact(event, "civilization", "невідомий народ")
            ChronicleNarrative(
                title = "$civilization закріплюється на мапі",
                hook = "$settlement стає постійним осередком, навколо якого тепер може вирости окрема історія.",
                body = "Народ $civilization заснував $settlement. Це вже не тимчасовий табір: поселення отримало власне населення, господарство й місце у політичній географії світу.",
                significance = "Новий центр може накопичувати ресурси, породжувати нові поселення, вступати в торгівлю або стати причиною майбутнього конфлікту.",
                changes = changes(event, "settlement" to "Осередок", "civilization" to "Держава"),
            )
        }
        "STATE_FOUNDED" -> {
            val civilization = fact(event, "civilization", "Нова держава")
            val parent = fact(event, "parent", "стара держава")
            val settlement = fact(event, "settlement", "прикордонний центр")
            ChronicleNarrative(
                title = "$civilization постає як окрема держава",
                hook = "$settlement більше не підкоряється державі $parent — на мапі з’явився новий політичний центр.",
                body = "Мешканці центру $settlement відокремилися від держави $parent і проголосили власну державу — $civilization. Вони успадкували частину населення, ресурсів, технологій і культури, але тепер мають окремі інтереси.",
                significance = "Поява нового центру сили відкриває дипломатію, торгівлю, союзи й конфлікти навіть у світі, що починався лише з одного племені.",
                changes = changes(event, "civilization" to "Нова держава", "parent" to "Материнська держава", "settlement" to "Столичний центр"),
            )
        }
        "SETTLEMENT_GROWTH" -> {
            val settlement = fact(event, "settlement", "Поселення")
            val population = event.numbers["population"]?.toLong()
            ChronicleNarrative(
                title = "$settlement переживає стрибок зростання",
                hook = population?.let { "Населення наблизилося до ${compact(it)} — старий масштаб поселення більше не відповідає реальності." }
                    ?: "Поселення входить у новий етап розвитку й починає тиснути на власні ресурси.",
                body = if (population != null) "$settlement виросло приблизно до ${compact(population)} мешканців. Зростання означає більше робочих рук і впливу, але також більшу потребу в їжі, просторі та порядку." else "$settlement увійшло в новий період зростання: його роль у житті держави помітно посилюється.",
                significance = "Чим більшим стає поселення, тим сильніше воно впливає на економіку, міграцію та здатність держави переживати кризи.",
                changes = numericChanges(event),
            )
        }
        "COLONY_FOUNDED" -> {
            val settlement = fact(event, "settlement", "Нова колонія")
            val parent = fact(event, "parent", "старішого поселення")
            ChronicleNarrative(
                title = "$settlement відділяється від старого центру",
                hook = "Переселенці з $parent створили нову точку присутності — карта держави стала складнішою.",
                body = "$settlement заснували вихідці з $parent. Нова громада переносить із собою людей, звичаї та господарські зв’язки, але з часом може набути власних інтересів.",
                significance = "Віддалені колонії розширюють контроль над територією, проте збільшують відстані, витрати й ризик політичного відокремлення.",
                changes = changes(event, "settlement" to "Нове поселення", "parent" to "Материнський центр"),
            )
        }
        "FOOD_SHORTAGE", "ECONOMIC_SHORTAGE" -> {
            val place = event.facts["settlement"] ?: event.facts["civilization"] ?: "Невідомий регіон"
            val shortage = event.numbers["shortage"]?.let { percent(it) }
            ChronicleNarrative(
                title = "Дефіцит стискає $place",
                hook = "Запас міцності скорочується: нестача ресурсів уже впливає на повсякденне життя.",
                body = if (shortage != null) "У $place сформувався дефіцит на рівні близько $shortage. Якщо він затягнеться, зростання зміниться міграцією, нестабільністю або боротьбою за доступні ресурси." else "$place зіткнувся з відчутною нестачею продовольства й ресурсів. Система ще працює, але запасу безпеки стає менше.",
                significance = "Тривалий дефіцит може змінити демографію, політику та навіть напрям експансії держави.",
                changes = numericChanges(event),
            )
        }
        "MIGRATION" -> {
            val from = fact(event, "from", "старого поселення")
            val to = fact(event, "to", "нового регіону")
            val people = event.numbers["people"]?.toLong() ?: 0L
            ChronicleNarrative(
                title = "Люди залишають $from",
                hook = "Потік населення зміщує вагу світу від $from до $to.",
                body = "Близько ${compact(people)} людей переселилися з $from до $to. Разом із ними переміщуються робочі руки, родинні зв’язки та культурні звички.",
                significance = "Міграція може посилити новий центр і одночасно послабити старий, змінюючи баланс між громадами без жодної війни.",
                changes = listOf("Переселено: ${compact(people)}"),
            )
        }
        "WAR_STARTED" -> {
            val a = fact(event, "a", "Одна держава")
            val b = fact(event, "b", "інша держава")
            ChronicleNarrative(
                title = "$a і $b переходять до війни",
                hook = "Дипломатичний конфлікт завершився: тепер кордони вирішуватимуться силою.",
                body = "$a та $b вступили у відкриту війну. Від цього моменту людські й господарські ресурси обох сторін починають працювати не лише на розвиток, а й на виживання у конфлікті.",
                significance = "Війна здатна змінити кордони, правителів, чисельність населення та довгострокові відносини навіть після укладення миру.",
                changes = changes(event, "a" to "Сторона", "b" to "Противник"),
            )
        }
        "WAR_CASUALTIES" -> {
            val casualties = event.numbers["casualties"]?.toLong() ?: 0L
            ChronicleNarrative(
                title = "Війна забирає ${compact(casualties)} життів",
                hook = "Конфлікт перестав бути лише лінією на мапі — його ціна вже вимірюється населенням.",
                body = "Бої біля ${fact(event, "settlementA", "одного фронту")} та ${fact(event, "settlementB", "сусіднього фронту")} призвели приблизно до ${compact(casualties)} втрат.",
                significance = "Великі втрати послаблюють економіку й армію, змінюють демографію та можуть наблизити мир або, навпаки, нову хвилю помсти.",
                changes = listOf("Втрати: ${compact(casualties)}"),
            )
        }
        "CITY_CAPTURED" -> {
            val settlement = fact(event, "settlement", "Прикордонне поселення")
            ChronicleNarrative(
                title = "$settlement змінює господаря",
                hook = "Лінія контролю посунулася: те, що вчора було тилом, сьогодні вже належить іншій державі.",
                body = "$settlement перейшло під контроль іншої держави після успішного наступу. Населення й ресурси поселення тепер включені в нову систему влади.",
                significance = "Захоплення поселення впливає не лише на карту: воно змінює логістику, багатство сторін і шанс наступних військових операцій.",
                changes = changes(event, "settlement" to "Захоплений центр"),
            )
        }
        "PEACE_TREATY" -> {
            val a = fact(event, "a", "Одна держава")
            val b = fact(event, "b", "інша держава")
            val winner = fact(event, "winner", "без однозначного переможця")
            val captures = event.numbers["captures"]?.toInt() ?: 0
            ChronicleNarrative(
                title = "$a і $b зупиняють війну",
                hook = "Зброя замовкає, але підсумок конфлікту вже вбудований у новий баланс сил.",
                body = "$a та $b уклали мир. Підсумок війни: $winner. За час бойових дій змінили власника $captures поселень.",
                significance = "Мир повертає ресурси до розвитку, однак нові кордони та пам’ять про втрати визначатимуть наступні рішення обох сторін.",
                changes = listOf("Захоплень за війну: $captures", "Підсумок: $winner"),
            )
        }
        "ALLIANCE_FORMED" -> allianceNarrative(event, formed = true)
        "ALLIANCE_ENDED" -> allianceNarrative(event, formed = false)
        "TRADE_FLOW" -> {
            val exporter = fact(event, "exporter", "Одна держава")
            val importer = fact(event, "importer", "сусід")
            val good = goodName(event.facts["good"])
            ChronicleNarrative(
                title = "$exporter нарощує торгівлю",
                hook = "$good починає системно рухатися до держави $importer.",
                body = "$exporter розширила постачання $good до держави $importer. Торговий маршрут пов’язує економіки сильніше, ніж формальний кордон їх розділяє.",
                significance = "Стабільна торгівля згладжує локальні дефіцити, створює взаємозалежність і робить розрив відносин дорожчим.",
                changes = changes(event, "good" to "Товар", "exporter" to "Експортер", "importer" to "Імпортер"),
            )
        }
        "ERA_ADVANCED" -> {
            val civilization = fact(event, "civilization", "Невідома держава")
            val era = fact(event, "era", "новий технологічний уклад")
            ChronicleNarrative(
                title = "$civilization входить у нову епоху",
                hook = "Технологічний поріг пройдено: $era тепер визначає можливості суспільства.",
                body = "$civilization увійшла в нову епоху — $era. Зміна проявиться не однією винахідницькою подією, а поступовою перебудовою праці, війни, транспорту й повсякденного життя.",
                significance = "Нова епоха відкриває інші межі розвитку й може швидко збільшити розрив із сусідами, які залишилися позаду.",
                changes = listOf("Нова епоха: $era"),
            )
        }
        "PERSON_DIED" -> {
            val person = fact(event, "person", "Історична постать")
            val age = event.numbers["age"]?.toInt()
            ChronicleNarrative(
                title = "Померла постать: $person",
                hook = age?.let { "$person завершив життя у віці $it років; тепер його місце в системі зв’язків залишилося вакантним." }
                    ?: "В історії світу закрилася одна особиста лінія.",
                body = age?.let { "Історична постать $person померла у віці $it років." } ?: "Історична постать $person померла.",
                significance = "Смерть впливової особи може змінити династичні зв’язки, престиж і подальший розподіл влади.",
                changes = age?.let { listOf("Вік: $it") } ?: emptyList(),
            )
        }
        "RULER_SUCCEEDED" -> {
            val person = fact(event, "person", "Новий правитель")
            val civilization = fact(event, "civilization", "невідома держава")
            ChronicleNarrative(
                title = "$person перебирає владу",
                hook = "$civilization отримує нового правителя — і нову точку, навколо якої будуватиметься політика.",
                body = "$person очолив державу $civilization. Формальна зміна імені на чолі держави може змінити династичну рівновагу й подальший курс еліт.",
                significance = "Передача влади — момент ризику: вона може пройти спокійно або відкрити старі суперечності всередині держави.",
                changes = changes(event, "person" to "Правитель", "civilization" to "Держава"),
            )
        }
        "DYNASTY_FOUNDED" -> {
            val person = fact(event, "person", "Нова постать")
            val civilization = fact(event, "civilization", "невідома держава")
            ChronicleNarrative(
                title = "У $civilization з’являється нова династія",
                hook = "$person перетворює особисту владу на родинний проєкт, розрахований на покоління.",
                body = "$person започаткував нову правлячу династію в державі $civilization. Відтепер шлюб, спадкування та родинні зв’язки стають частиною політичної архітектури.",
                significance = "Стійка династія може зменшити хаос передачі влади, але водночас концентрує статус і конфлікти навколо одного роду.",
                changes = changes(event, "person" to "Засновник", "civilization" to "Держава"),
            )
        }
        "RULER_PARTNERSHIP_FORMED" -> generic(
            event,
            "Влада закріплюється союзом",
            "Особисті стосунки правителя стають політичним зв’язком.",
            "Правитель ${fact(event, "ruler", "невідома особа")} утворив династичний союз із ${fact(event, "partner", "новим партнером")}.",
            "Такі союзи змінюють мережу спадкування, престижу й майбутніх претензій на владу.",
        )
        "DYNASTIC_BIRTH" -> generic(
            event,
            "У правлячому домі з’являється спадкоємець",
            "Народження ${fact(event, "person", "дитини")} додає нову гілку до майбутнього держави.",
            "У правлячому домі держави ${fact(event, "civilization", "невідомого народу")} народився новий династ — ${fact(event, "person", "дитина")}.",
            "Новий член династії змінює чергу спадкування та майбутні родинні союзи.",
        )
        "ADULT_SOCIAL_EVENT" -> {
            val civilization = fact(event, "civilization", "невідома держава")
            val name = event.facts["eventCode"]?.replace('_', ' ')?.lowercase() ?: "приватна соціальна подія"
            ChronicleNarrative(
                title = "Приватне життя $civilization виходить у хроніку",
                hook = "Соціальні норми проявилися не в законі, а в поведінці конкретних дорослих людей.",
                body = buildString {
                    append("У державі $civilization відбулася подія: $name.")
                    event.facts["participants"]?.takeIf { it.isNotBlank() }?.let { append(" Учасники: $it.") }
                },
                significance = "Такі події показують, як культурні норми реально працюють у побуті та відносинах, а не лише існують як абстрактні теги.",
                changes = emptyList(),
            )
        }
        "LINEAGE_FOUNDED", "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION" -> evolutionNarrative(event)
        "CULTURAL_ASSIMILATION" -> generic(
            event,
            "Культура більшості змінюється",
            "У ${fact(event, "settlement", "поселенні")} звички й ідентичність зсуваються швидше, ніж біологічне походження.",
            "У ${fact(event, "settlement", "поселенні")} культурна ідентичність більшості змістилася до панівної культури, не змінюючи біологічного походження населення.",
            "Культурна асиміляція здатна зменшити або, навпаки, загострити відмінність між походженням населення та його політичною ідентичністю.",
        )
        "INTERVENTION_HARVEST_AID", "INTERVENTION_DROUGHT", "INTERVENTION_TECH_BOOST", "INTERVENTION_STABILITY_SUPPORT" -> interventionNarrative(event)
        else -> generic(
            event,
            humanCode(event.code),
            "У світі відбулася подія, яка потрапила до історичного журналу.",
            humanCode(event.code) + ".",
            "Її довгострокове значення стане зрозумілим у наступних змінах світу.",
        )
    }

    private fun allianceNarrative(event: SimulationEvent, formed: Boolean): ChronicleNarrative {
        val a = fact(event, "a", "Одна держава")
        val b = fact(event, "b", "інша держава")
        return if (formed) {
            ChronicleNarrative(
                "$a і $b утворюють союз",
                "Дві держави вирішили, що співпраця зараз вигідніша за суперництво.",
                "$a та $b уклали союз. Відтепер їхні зовнішні рішення сильніше впливатимуть одна на одну.",
                "Союз змінює дипломатичний баланс: сусіди мають рахуватися вже не з двома окремими центрами сили.",
                changes(event, "a" to "Союзник", "b" to "Союзник"),
            )
        } else {
            ChronicleNarrative(
                "Союз $a і $b розпався",
                "Колишня домовленість більше не стримує суперечності між партнерами.",
                "Союз між $a та $b припинив існування.",
                "Розрив відкриває простір для нових союзів, торгових втрат або прямого конфлікту.",
                changes(event, "a" to "Колишній союзник", "b" to "Колишній союзник"),
            )
        }
    }

    private fun evolutionNarrative(event: SimulationEvent): ChronicleNarrative {
        val settlement = fact(event, "settlement", "невідомому регіоні")
        val lineage = fact(event, "lineage", "нова лінія")
        return when (event.code) {
            "LINEAGE_FOUNDED" -> ChronicleNarrative(
                "У $settlement формується окрема лінія",
                "$lineage отримує власну демографічну історію.",
                "У поселенні $settlement сформувалася окрема популяційна лінія — $lineage.",
                "Ізольована популяція може накопичувати власні ознаки й з часом віддалитися від початкового населення.",
                changes(event, "lineage" to "Лінія", "settlement" to "Осередок"),
            )
            "BIOLOGICAL_DIVERGENCE" -> ChronicleNarrative(
                "$lineage переходить нову межу еволюції",
                "Відмінності вже достатньо стійкі, щоб їх не можна було вважати просто випадковою варіацією.",
                "Лінія $lineage у $settlement досягла нового рівня біологічного розходження: ${fact(event, "rank", "морф")}.",
                "Подальша ізоляція або відбір можуть закріпити відмінності й перетворити їх на основу окремого підвиду чи виду.",
                changes(event, "rank" to "Рівень", "lineage" to "Лінія"),
            )
            "STRUCTURAL_MUTATION", "PLAYER_STRUCTURAL_MUTATION" -> ChronicleNarrative(
                "$lineage отримує новий план тіла",
                "Зміна торкнулася не лише зовнішності — перебудовується сама будова організму.",
                "У $settlement в лінії $lineage закріпилася структурна зміна: ${fact(event, "bodyPlan", "новий план тіла")}.",
                "Стійка структурна мутація може вплинути на спосіб життя, працю, бойові можливості та майбутню сумісність з іншими лініями.",
                changes(event, "bodyPlan" to "Нова будова", "lineage" to "Лінія"),
            )
            "HYBRID_LINEAGE_FORMED", "PLAYER_HYBRIDIZATION" -> ChronicleNarrative(
                "У $settlement народжується гібридна лінія",
                "$lineage поєднує спадковість двох раніше відмінних популяцій.",
                "У $settlement стабілізувалася гібридна лінія $lineage, що поєднує ${fact(event, "primary", "першу лінію")} та ${fact(event, "secondary", "другу лінію")}.",
                "Гібридна популяція може успадкувати сильні й слабкі сторони обох батьківських ліній та стати новим напрямом еволюції.",
                changes(event, "primary" to "Перша лінія", "secondary" to "Друга лінія"),
            )
            else -> ChronicleNarrative(
                "$lineage прискорено віддаляється від предків",
                "Втручання різко змінило темп природного розходження.",
                "За втручанням гравця в $settlement відокремилася прискорено змінена лінія $lineage.",
                "Тепер її подальша історія залежить від ізоляції, міграції та можливих контактів з іншими лініями.",
                changes(event, "lineage" to "Нова лінія", "parent" to "Предкова лінія"),
            )
        }
    }

    private fun interventionNarrative(event: SimulationEvent): ChronicleNarrative {
        val civilization = fact(event, "civilization", "невідома держава")
        return when (event.code) {
            "INTERVENTION_HARVEST_AID" -> generic(event, "Врожай $civilization отримує зовнішню підтримку", "Дефіцит відступає швидше, ніж це дозволили б природні умови.", "Зовнішнє втручання посилило врожайність у державі $civilization.", "Додаткові запаси можуть підтримати населення, зростання міст і політичну стабільність.")
            "INTERVENTION_DROUGHT" -> generic(event, "На $civilization насувається штучна посуха", "Вода й продовольство стають головним обмеженням для населення.", "Штучно спричинена посуха вдарила по державі $civilization, скоротивши запаси продовольства та населення.", "Тривала нестача здатна запустити міграцію, конфлікти та довгострокове ослаблення держави.")
            "INTERVENTION_TECH_BOOST" -> generic(event, "$civilization отримує технологічний поштовх", "Розвиток прискорився не природним шляхом — сусіди можуть не встигнути адаптуватися.", "Держава $civilization отримала різкий технологічний імпульс.", "Навіть невеликий технологічний розрив з часом перетворюється на перевагу в економіці, війні та експансії.")
            else -> generic(event, "Влада $civilization отримує опору", "Політична система тимчасово стає стійкішою до внутрішнього тиску.", "Політичну стабільність держави $civilization штучно посилили.", "Додаткова стабільність дає час на розвиток, але не усуває причин майбутніх криз.")
        }
    }

    private fun generic(event: SimulationEvent, title: String, hook: String, body: String, significance: String) =
        ChronicleNarrative(title, hook, body, significance, numericChanges(event))

    private fun fact(event: SimulationEvent, key: String, fallback: String): String =
        event.facts[key]?.takeIf { it.isNotBlank() } ?: fallback

    private fun changes(event: SimulationEvent, vararg factKeys: Pair<String, String>): List<String> = buildList {
        factKeys.forEach { (key, label) -> event.facts[key]?.takeIf { it.isNotBlank() }?.let { add("$label: $it") } }
        addAll(numericChanges(event))
    }.distinct().take(5)

    private fun numericChanges(event: SimulationEvent): List<String> = event.numbers.entries.take(5).map { (key, value) ->
        val label = when (key) {
            "population" -> "Населення"
            "people" -> "Людей"
            "casualties" -> "Втрати"
            "captures" -> "Захоплень"
            "shortage" -> "Дефіцит"
            "stability" -> "Стабільність"
            "technology" -> "Технології"
            "food" -> "Їжа"
            "wealth" -> "Добробут"
            else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        val rendered = when {
            key.contains("shortage", ignoreCase = true) || abs(value) <= 1.0 && key in setOf("stability", "technology") -> percent(value)
            abs(value) >= 1000.0 -> compact(value.toLong())
            value % 1.0 == 0.0 -> value.toLong().toString()
            else -> String.format("%.2f", value)
        }
        "$label: $rendered"
    }

    private fun goodName(code: String?): String = when (code) {
        "FOOD" -> "продовольство"
        "TIMBER" -> "деревину"
        "STONE" -> "камінь"
        "METAL" -> "метал"
        "FUEL" -> "паливо"
        "CRAFTS" -> "ремісничі вироби"
        else -> code?.lowercase() ?: "товари"
    }

    private fun percent(value: Double): String = String.format("%.0f%%", value * 100.0)

    private fun compact(value: Long): String = when {
        abs(value) >= 1_000_000 -> String.format("%.1f млн", value / 1_000_000.0)
        abs(value) >= 1_000 -> String.format("%.1f тис.", value / 1_000.0)
        else -> value.toString()
    }

    private fun humanCode(code: String): String =
        code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
