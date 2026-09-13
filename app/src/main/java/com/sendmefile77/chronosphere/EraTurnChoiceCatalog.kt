package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.history.InterventionKind

/** Concrete, era-specific choices offered before a century advances. */
internal object EraTurnChoiceCatalog {
    private const val SOURCE_PREFIX = "player-century-choice"
    private const val TAG_PREFIX = "era-choice:"

    private data class Spec(
        val family: String,
        val slug: String,
        val titleUk: String,
        val effectUk: String,
        val riskUk: String,
        val kind: InterventionKind,
        val strength: Double,
        val legacyTags: Set<String>,
    )

    fun decision(
        state: LivingPlanetState,
        economy: EconomyState,
        civilizationId: String,
    ): ChronicleDecision {
        val civilization = state.civilizations.firstOrNull { it.id == civilizationId } ?: state.civilizations.first()
        val era = economy.economy(civilization.id)?.era ?: TechnologyEra.TRIBAL
        val source = "$SOURCE_PREFIX-${state.tick}-${civilization.id}"
        val pool = specs.getValue(era)

        fun activeSlug(family: String): String? = civilization.cultureTags
            .firstOrNull { it.startsWith("$TAG_PREFIX$family:") }
            ?.substringAfterLast(':')

        val knownBreakthroughs = civilization.cultureTags.asSequence()
            .filter { it.startsWith("${TAG_PREFIX}breakthrough:") }
            .map { it.substringAfterLast(':') }
            .toSet()

        val selected = pool.groupBy { it.family }
            .toSortedMap()
            .mapNotNull { (familyName, familySpecs) ->
                val candidates = when (familyName) {
                    "breakthrough" -> familySpecs.filter { it.slug !in knownBreakthroughs }
                    else -> {
                        val current = activeSlug(familyName)
                        familySpecs.filter { it.slug != current }.ifEmpty { familySpecs }
                    }
                }
                candidates.minByOrNull { stableRank(state.worldSeed, state.tick, civilization.id, it.slug) }
            }
            .toMutableList()

        val remaining = pool.asSequence()
            .filter { it !in selected }
            .filterNot { it.family == "breakthrough" && it.slug in knownBreakthroughs }
            .filterNot { spec -> spec.family != "breakthrough" && spec.slug == activeSlug(spec.family) }
            .sortedBy { stableRank(state.worldSeed xor 0x5F3759DFL, state.tick + 17L, civilization.id, it.slug) }
            .toList()
        if (remaining.isNotEmpty()) selected += remaining.first()

        val options = selected.take(5).map { spec ->
            ChronicleDecisionOption(
                id = choiceId(era, spec),
                sourceEventId = "$source-${spec.family}",
                titleUk = spec.titleUk,
                effectUk = spec.effectUk,
                riskUk = spec.riskUk,
                kind = spec.kind,
                targetCivilizationId = civilization.id,
                strength = spec.strength,
            )
        }
        return ChronicleDecision(
            eventId = source,
            titleUk = "${era.displayNameUk} доба: що змінити цього століття?",
            promptUk = "Оберіть до трьох напрямів. Відкриття накопичуються назавжди, а спосіб життя, суспільний курс і мобільність можуть змінюватися. Після підтвердження світ одразу проживе наступні 100 років.",
            options = options,
        )
    }

    fun isEraTurn(decision: ChronicleDecision): Boolean = decision.eventId.startsWith(SOURCE_PREFIX)

    fun family(option: ChronicleDecisionOption): String? = specFor(option.id)?.family

    fun applyLegacy(
        state: LivingPlanetState,
        civilizationId: String,
        choiceId: String,
    ): LivingPlanetState {
        val spec = specFor(choiceId) ?: return state
        val familyPrefix = "$TAG_PREFIX${spec.family}:"
        val obsolete = if (spec.family == "breakthrough") emptySet() else {
            allSpecs.asSequence()
                .filter { it.family == spec.family }
                .flatMap { it.legacyTags.asSequence() }
                .toSet()
        }
        return state.copy(
            civilizations = state.civilizations.map { civilization ->
                if (civilization.id != civilizationId) return@map civilization
                val retained = civilization.cultureTags
                    .filterNot { it in obsolete }
                    .filterNot { spec.family != "breakthrough" && it.startsWith(familyPrefix) }
                    .toSet()
                civilization.copy(
                    cultureTags = retained + spec.legacyTags + "$familyPrefix${spec.slug}",
                )
            },
        )
    }

    private fun choiceId(era: TechnologyEra, spec: Spec): String =
        "era-${era.name.lowercase()}-${spec.family}-${spec.slug}"

    private fun specFor(choiceId: String): Spec? = allSpecs.firstOrNull { spec ->
        choiceId.endsWith("-${spec.family}-${spec.slug}")
    }

    private fun s(
        family: String,
        slug: String,
        title: String,
        effect: String,
        risk: String,
        kind: InterventionKind,
        strength: Double,
        vararg tags: String,
    ) = Spec(family, slug, title, effect, risk, kind, strength, tags.toSet())

    private val specs: Map<TechnologyEra, List<Spec>> = mapOf(
        TechnologyEra.TRIBAL to listOf(
            s("breakthrough", "fire", "Опанувати вогонь", "Їжа краще зберігається, ночівлі стають безпечнішими, з’являється постійне вогнище.", "Дим, опіки й залежність від палива стають частиною побуту.", InterventionKind.TECHNOLOGY_BOOST, 0.42, "foundation:fire_mastery", "hist:hearth_culture"),
            s("breakthrough", "stone_tools", "Винайти кам’яні знаряддя", "Рубила, ножі й скребки прискорюють полювання та обробку матеріалів.", "Виробництво знарядь забирає час і добрий камінь.", InterventionKind.TECHNOLOGY_BOOST, 0.46, "foundation:stone_tools", "hist:tool_bearers"),
            s("subsistence", "predator_hunters", "Жити полюванням як хижаки", "Плем’я робить ставку на велику здобич, м’ясо, шкури й агресивну мисливську культуру.", "Невдалий сезон полювання різко б’є по запасах; культура стає жорсткішою.", InterventionKind.HARVEST_AID, 0.58, "policy:predator_hunters", "hist:blood_hunt"),
            s("subsistence", "plant_foragers", "Жити з рослин і коріння", "Збиральництво дає ширший і стабільніший раціон без залежності від великої здобичі.", "Менше білкової їжі й слабша мисливська спеціалізація.", InterventionKind.HARVEST_AID, 0.48, "policy:plant_foragers", "hist:gatherer_culture"),
            s("subsistence", "river_fishers", "Освоїти рибальство", "Гарпуни, пастки й річкові стоянки дають нове джерело їжі.", "Плем’я сильніше залежить від води й сезонів.", InterventionKind.HARVEST_AID, 0.52, "policy:river_fishers", "hist:harpoon_fishing"),
            s("society", "animal_taming", "Приручити перших тварин", "Тварини допомагають охороняти табір, полювати й переносити вантажі.", "Хвороби й витрати на утримання переходять до людей.", InterventionKind.STABILITY_SUPPORT, 0.40, "foundation:animal_companions", "hist:tamed_animals"),
            s("society", "ritual_culture", "Створити великі ритуали", "Спільні обряди зміцнюють єдність племені й владу старійшин.", "Суспільство стає залежнішим від традиції.", InterventionKind.FESTIVAL, 0.44, "policy:ritual_culture", "hist:ritual_markings"),
            s("mobility", "nomadic_migration", "Стати кочівниками", "Плем’я рухається слідом за ресурсами й уникає виснажених земель.", "Постійні споруди й запаси накопичуються повільніше.", InterventionKind.STABILITY_SUPPORT, 0.34, "policy:nomadic_migration", "hist:portable_camp"),
            s("mobility", "permanent_camp", "Закріпитися на одному місці", "Постійне поселення дає сховища, майстерні й сильнішу територіальну пам’ять.", "Нестача ресурсів поблизу стає небезпечнішою.", InterventionKind.STABILITY_SUPPORT, 0.42, "foundation:permanent_camp", "hist:fixed_settlement"),
        ),
        TechnologyEra.AGRARIAN to listOf(
            s("breakthrough", "plough", "Створити плуг", "Обробіток землі прискорюється, врожаї ростуть.", "Потрібні тяглова сила й родючі землі.", InterventionKind.TECHNOLOGY_BOOST, 0.46, "foundation:plough", "hist:plough_fields"),
            s("breakthrough", "irrigation", "Прокласти зрошення", "Канали роблять врожаї стабільнішими й дозволяють годувати більше людей.", "Канали треба постійно чистити й охороняти.", InterventionKind.TECHNOLOGY_BOOST, 0.50, "foundation:irrigation", "hist:canal_fields"),
            s("subsistence", "grain_farming", "Зробити зерно основою життя", "Великі запаси зерна підтримують міста й армії.", "Неврожай однієї культури стає системною загрозою.", InterventionKind.HARVEST_AID, 0.56, "policy:grain_farming", "hist:grain_stores"),
            s("subsistence", "pastoralism", "Жити стадами", "М’ясо, молоко, шкіра й мобільність стають основою господарства.", "Посухи й епідемії худоби можуть бути руйнівними.", InterventionKind.HARVEST_AID, 0.50, "policy:pastoralism", "hist:herd_culture"),
            s("society", "land_tenure", "Закріпити землю за родами", "Стає зрозуміло, хто відповідає за поля й запаси.", "Майнова нерівність зростає.", InterventionKind.STABILITY_SUPPORT, 0.48, "policy:land_tenure", "hist:field_boundaries"),
            s("society", "seasonal_fairs", "Запровадити ярмарки", "Обмін між поселеннями прискорює спеціалізацію.", "Більше контактів означає більше конфліктів і хвороб.", InterventionKind.FESTIVAL, 0.46, "policy:seasonal_fairs", "hist:market_fair"),
            s("mobility", "village_network", "Зв’язати села дорогами", "Рух людей і вантажів стає швидшим.", "Будівництво доріг відтягує працю від полів.", InterventionKind.TECHNOLOGY_BOOST, 0.42, "foundation:road_network", "hist:dirt_roads"),
            s("mobility", "frontier_farms", "Освоювати нові землі", "Населення розсіюється по нових полях і пасовищах.", "Фронтир важче захищати й контролювати.", InterventionKind.HARVEST_AID, 0.44, "policy:frontier_farms", "hist:frontier_homesteads"),
        ),
        TechnologyEra.URBAN to listOf(
            s("breakthrough", "writing", "Запровадити письмо", "Податки, угоди й пам’ять держави перестають залежати лише від усної традиції.", "Писемність концентрує владу в руках освічених груп.", InterventionKind.TECHNOLOGY_BOOST, 0.55, "foundation:writing", "hist:scribes"),
            s("breakthrough", "sewers", "Побудувати міську каналізацію", "Великі міста стають чистішими й стійкішими до хвороб.", "Дорога інфраструктура потребує постійного догляду.", InterventionKind.TECHNOLOGY_BOOST, 0.48, "foundation:sewers", "hist:drains"),
            s("subsistence", "urban_markets", "Годувати міста через ринки", "Ремісники менше залежать від власних полів.", "Зрив торгівлі швидко породжує дефіцит.", InterventionKind.HARVEST_AID, 0.50, "policy:urban_markets", "hist:market_stalls"),
            s("subsistence", "state_granaries", "Створити державні комори", "Запаси згладжують неврожаї й облоги.", "Утримання запасів дороге й приваблює корупцію.", InterventionKind.HARVEST_AID, 0.58, "foundation:state_granaries", "hist:granary_rows"),
            s("society", "standing_guard", "Створити міську варту", "Порядок і контроль у містах посилюються.", "Влада стає жорсткішою й дорожчою.", InterventionKind.STABILITY_SUPPORT, 0.52, "policy:standing_guard", "hist:city_guard"),
            s("society", "merchant_guilds", "Дозволити купецькі гільдії", "Торгівля й спеціалізація прискорюються.", "Гільдії накопичують політичний вплив.", InterventionKind.FESTIVAL, 0.44, "policy:merchant_guilds", "hist:guild_markets"),
            s("mobility", "paved_roads", "Мостити великі дороги", "Армії, пошта й торгівля рухаються швидше.", "Дороги коштують дорого й допомагають ворогові рухатися так само швидко.", InterventionKind.TECHNOLOGY_BOOST, 0.46, "foundation:paved_roads", "hist:stone_roads"),
            s("mobility", "colonies", "Засновувати далекі колонії", "Держава отримує нові ресурси й опорні пункти.", "Віддалені землі важко утримувати.", InterventionKind.STABILITY_SUPPORT, 0.36, "policy:colonies", "hist:colonial_outposts"),
        ),
        TechnologyEra.METALLURGIC to listOf(
            s("breakthrough", "iron_tools", "Перейти на залізні знаряддя", "Міцні інструменти прискорюють працю й змінюють побут.", "Потрібні шахти, паливо й ковалі.", InterventionKind.TECHNOLOGY_BOOST, 0.58, "foundation:iron_tools", "hist:iron_tools"),
            s("breakthrough", "metal_weapons", "Масово кувати металеву зброю", "Військова сила різко зростає.", "Держава стає залежною від руди й майстрів.", InterventionKind.TECHNOLOGY_BOOST, 0.52, "foundation:metal_weapons", "hist:armed_militia"),
            s("subsistence", "iron_plough", "Озброїти поля залізом", "Міцні лемеші відкривають важкі ґрунти й збільшують врожаї.", "Ковальство відтягує метал від інших потреб.", InterventionKind.HARVEST_AID, 0.52, "policy:iron_plough", "hist:deep_fields"),
            s("subsistence", "mine_economy", "Жити навколо шахт", "Метал стає основою багатства й спеціалізації.", "Шахти небезпечні й виснажують довкілля.", InterventionKind.TECHNOLOGY_BOOST, 0.50, "policy:mine_economy", "hist:mining_camps"),
            s("society", "coinage", "Карбувати монету", "Податки й торгівля стають простішими.", "Контроль грошей посилює центральну владу.", InterventionKind.STABILITY_SUPPORT, 0.44, "foundation:coinage", "hist:coins_scales"),
            s("society", "warrior_elite", "Створити військову еліту", "Професійні воїни підсилюють державу.", "Еліта починає вимагати привілеїв.", InterventionKind.STABILITY_SUPPORT, 0.48, "policy:warrior_elite", "hist:armored_elite"),
            s("mobility", "cavalry", "Освоїти кінноту", "Зв’язок і війна стають значно швидшими.", "Потрібні коні, пасовища й нова підготовка.", InterventionKind.TECHNOLOGY_BOOST, 0.48, "foundation:cavalry", "hist:horse_gear"),
            s("mobility", "trade_caravans", "Прокласти караванні шляхи", "Метал, сіль і ремесла ходять між далекими містами.", "Каравани потребують охорони.", InterventionKind.FESTIVAL, 0.40, "policy:trade_caravans", "hist:caravan_routes"),
        ),
        TechnologyEra.MEDIEVAL to listOf(
            s("breakthrough", "watermills", "Будувати водяні млини", "Вода бере на себе частину важкої ручної праці.", "Млини прив’язують виробництво до річок і власників споруд.", InterventionKind.TECHNOLOGY_BOOST, 0.50, "foundation:watermills", "hist:watermills"),
            s("breakthrough", "manuscript_schools", "Відкрити школи переписувачів", "Знання й адміністрація накопичуються швидше.", "Освіта залишається дорогою й елітарною.", InterventionKind.TECHNOLOGY_BOOST, 0.46, "foundation:manuscript_schools", "hist:scriptoria"),
            s("subsistence", "crop_rotation", "Запровадити сівозміну", "Поля виснажуються повільніше, врожаї стають стабільнішими.", "Потрібна дисципліна всієї громади.", InterventionKind.HARVEST_AID, 0.56, "policy:crop_rotation", "hist:strip_fields"),
            s("subsistence", "lordly_estates", "Зосередити їжу в маєтках", "Великі господарства створюють резерви й спеціалізацію.", "Селяни втрачають частину самостійності.", InterventionKind.HARVEST_AID, 0.48, "policy:lordly_estates", "hist:estate_barns"),
            s("society", "fortifications", "Укріпити міста й замки", "Оборона й політичний контроль посилюються.", "Кам’яні укріплення дуже дорогі.", InterventionKind.STABILITY_SUPPORT, 0.54, "foundation:fortifications", "hist:stone_castles"),
            s("society", "guild_law", "Дати гільдіям права", "Ремісники самоорганізовуються й підвищують якість виробництва.", "Цехи обмежують конкуренцію.", InterventionKind.FESTIVAL, 0.42, "policy:guild_law", "hist:guild_halls"),
            s("mobility", "pilgrim_roads", "Розвинути великі дороги", "Торгівля, паломництва й пошта зв’язують регіони.", "Разом із людьми швидше рухаються хвороби.", InterventionKind.TECHNOLOGY_BOOST, 0.40, "foundation:medieval_roads", "hist:roadside_inns"),
            s("mobility", "frontier_castles", "Розширювати кордон замками", "Влада закріплюється на нових землях.", "Постійні гарнізони виснажують казну.", InterventionKind.STABILITY_SUPPORT, 0.42, "policy:frontier_castles", "hist:border_keeps"),
        ),
        TechnologyEra.EARLY_INDUSTRIAL to listOf(
            s("breakthrough", "steam_power", "Освоїти парову машину", "Механічна енергія відриває виробництво від м’язів, вітру й води.", "Потреба у вугіллі та аварії різко зростають.", InterventionKind.TECHNOLOGY_BOOST, 0.64, "foundation:steam_power", "hist:steam_engines"),
            s("breakthrough", "mechanized_looms", "Механізувати ткацтво", "Тканини стають масовим товаром.", "Ремісники втрачають старі професії.", InterventionKind.TECHNOLOGY_BOOST, 0.56, "foundation:mechanized_looms", "hist:mill_looms"),
            s("subsistence", "enclosures", "Укрупнити сільське господарство", "Продуктивність землі росте.", "Частина людей втрачає землю й іде до міст.", InterventionKind.HARVEST_AID, 0.52, "policy:enclosures", "hist:enclosed_fields"),
            s("subsistence", "urban_food_chain", "Створити міське постачання їжі", "Міста можуть рости далеко від власних полів.", "Збої транспорту стають небезпечнішими.", InterventionKind.HARVEST_AID, 0.50, "policy:urban_food_chain", "hist:food_warehouses"),
            s("society", "factory_discipline", "Запровадити фабричну дисципліну", "Виробництво стає передбачуванішим.", "Робота стає жорсткішою й монотоннішою.", InterventionKind.STABILITY_SUPPORT, 0.42, "policy:factory_discipline", "hist:factory_shifts"),
            s("society", "public_clinics", "Створити перші громадські клініки", "Міста краще переживають травми й епідемії.", "Медицина потребує податків і фахівців.", InterventionKind.STABILITY_SUPPORT, 0.50, "foundation:public_clinics", "hist:early_clinics"),
            s("mobility", "canals", "Прокласти промислові канали", "Вугілля й вантажі рухаються дешевше.", "Будівництво довге й дороге.", InterventionKind.TECHNOLOGY_BOOST, 0.48, "foundation:industrial_canals", "hist:canal_barges"),
            s("mobility", "turnpikes", "Побудувати мережу шосе", "Пошта й торгівля прискорюються.", "Дороги потребують зборів і постійного ремонту.", InterventionKind.TECHNOLOGY_BOOST, 0.42, "foundation:turnpikes", "hist:coach_roads"),
        ),
        TechnologyEra.INDUSTRIAL to listOf(
            s("breakthrough", "railways", "Побудувати залізниці", "Люди, сировина й армії рухаються в небачених масштабах.", "Залізниці дорогі й концентрують промисловість.", InterventionKind.TECHNOLOGY_BOOST, 0.66, "foundation:railways", "hist:steam_rail"),
            s("breakthrough", "steel", "Освоїти масову сталь", "Мости, машини й зброя стають міцнішими й дешевшими.", "Печі споживають величезні обсяги палива.", InterventionKind.TECHNOLOGY_BOOST, 0.62, "foundation:mass_steel", "hist:steelworks"),
            s("subsistence", "industrial_agriculture", "Механізувати поля", "Менше людей годують значно більші міста.", "Село втрачає населення, а система залежить від машин.", InterventionKind.HARVEST_AID, 0.58, "policy:industrial_agriculture", "hist:mechanized_farms"),
            s("subsistence", "processed_food", "Створити масове виробництво їжі", "Їжа довше зберігається й легше перевозиться.", "Раціон стає одноманітнішим і залежним від фабрик.", InterventionKind.HARVEST_AID, 0.52, "policy:processed_food", "hist:canneries"),
            s("society", "mass_schooling", "Запровадити масову освіту", "Грамотність і технічні навички поширюються на більшість населення.", "Держава отримує сильніший вплив на світогляд людей.", InterventionKind.STABILITY_SUPPORT, 0.48, "foundation:mass_schooling", "hist:public_schools"),
            s("society", "sanitation", "Провести санітарну реформу", "Вода, каналізація й медицина знижують міську смертність.", "Інфраструктура коштує дорого.", InterventionKind.STABILITY_SUPPORT, 0.54, "foundation:sanitation", "hist:sewer_streets"),
            s("mobility", "mass_migration", "Відкрити шлях масовій міграції", "Робітники швидко переміщуються до нових центрів.", "Старі громади руйнуються, міста перенаселяються.", InterventionKind.STABILITY_SUPPORT, 0.36, "policy:mass_migration", "hist:rail_migrants"),
            s("mobility", "global_shipping", "Розвинути океанські перевезення", "Далекі ринки стають частиною щоденної економіки.", "Залежність від зовнішніх маршрутів зростає.", InterventionKind.TECHNOLOGY_BOOST, 0.48, "foundation:global_shipping", "hist:steamships"),
        ),
        TechnologyEra.ELECTRIC to listOf(
            s("breakthrough", "power_grid", "Електрифікувати країну", "Світло, двигуни й зв’язок працюють цілодобово.", "Аварія мережі тепер паралізує безліч систем одразу.", InterventionKind.TECHNOLOGY_BOOST, 0.66, "foundation:power_grid", "hist:electric_streets"),
            s("breakthrough", "radio", "Освоїти радіозв’язок", "Новини й накази долають країну майже миттєво.", "Центральна влада отримує новий інструмент впливу.", InterventionKind.TECHNOLOGY_BOOST, 0.54, "foundation:radio", "hist:radio_towers"),
            s("subsistence", "chemical_farming", "Хімізувати землеробство", "Врожайність різко зростає.", "Ґрунти й вода отримують нове навантаження.", InterventionKind.HARVEST_AID, 0.58, "policy:chemical_farming", "hist:fertilizer_fields"),
            s("subsistence", "cold_chain", "Створити холодильний ланцюг", "Свіжі продукти їдуть далеко й довго зберігаються.", "Система залежить від електрики й транспорту.", InterventionKind.HARVEST_AID, 0.52, "foundation:cold_chain", "hist:cold_storage"),
            s("society", "broadcast_society", "Створити масове мовлення", "Спільні новини й культура об’єднують великі суспільства.", "Інформація стає централізованою.", InterventionKind.STABILITY_SUPPORT, 0.44, "policy:broadcast_society", "hist:radio_homes"),
            s("society", "public_health", "Розгорнути громадську медицину", "Смертність падає, міста стають стійкішими.", "Система потребує великого бюджету.", InterventionKind.STABILITY_SUPPORT, 0.52, "foundation:public_health", "hist:hospitals"),
            s("mobility", "motorization", "Моторизувати транспорт", "Автомобілі й вантажівки змінюють відстані та міста.", "Паливо стає стратегічною залежністю.", InterventionKind.TECHNOLOGY_BOOST, 0.52, "foundation:motorization", "hist:motor_roads"),
            s("mobility", "electric_transit", "Будувати електричний транспорт", "Міста ростуть уздовж трамваїв і приміських ліній.", "Мережі дорогі й негнучкі.", InterventionKind.STABILITY_SUPPORT, 0.44, "foundation:electric_transit", "hist:tram_lines"),
        ),
        TechnologyEra.INFORMATION to listOf(
            s("breakthrough", "computing", "Зробити комп’ютери масовими", "Розрахунки, управління й наука різко прискорюються.", "Суспільство стає залежним від цифрової інфраструктури.", InterventionKind.TECHNOLOGY_BOOST, 0.68, "foundation:computing", "hist:computerized_work"),
            s("breakthrough", "biotech", "Інвестувати в біотехнології", "Медицина й керування живими системами переходять на новий рівень.", "Помилки й нерівний доступ мають довгі наслідки.", InterventionKind.TECHNOLOGY_BOOST, 0.62, "foundation:biotech", "hist:biotech_labs"),
            s("subsistence", "precision_farming", "Перейти на точне землеробство", "Дані й автоматика зменшують втрати ресурсів.", "Система залежить від датчиків і мереж.", InterventionKind.HARVEST_AID, 0.56, "policy:precision_farming", "hist:sensor_fields"),
            s("subsistence", "synthetic_food", "Розвивати синтетичну їжу", "Частина харчування відривається від клімату й ґрунту.", "Культура харчування стає залежною від технологічних платформ.", InterventionKind.HARVEST_AID, 0.50, "policy:synthetic_food", "hist:food_bioreactors"),
            s("society", "open_networks", "Побудувати відкриті мережі", "Знання й кооперація поширюються швидше.", "Дезінформація й конфлікти також прискорюються.", InterventionKind.STABILITY_SUPPORT, 0.42, "policy:open_networks", "hist:network_society"),
            s("society", "algorithmic_governance", "Автоматизувати управління", "Держава швидше реагує на великі потоки даних.", "Помилки алгоритмів масштабуються на все суспільство.", InterventionKind.STABILITY_SUPPORT, 0.48, "policy:algorithmic_governance", "hist:data_governance"),
            s("mobility", "autonomous_transport", "Запустити автономний транспорт", "Перевезення стають безперервними й точнішими.", "Збій програмного шару зупиняє цілі мережі.", InterventionKind.TECHNOLOGY_BOOST, 0.50, "foundation:autonomous_transport", "hist:autonomous_roads"),
            s("mobility", "remote_life", "Перенести частину життя в мережу", "Робота й освіта менше залежать від місця.", "Фізичні громади слабшають.", InterventionKind.STABILITY_SUPPORT, 0.38, "policy:remote_life", "hist:remote_society"),
        ),
        TechnologyEra.SPACEFARING to listOf(
            s("breakthrough", "fusion", "Освоїти термоядерну енергію", "Енергетичні межі цивілізації різко відсуваються.", "Складність інфраструктури стає безпрецедентною.", InterventionKind.TECHNOLOGY_BOOST, 0.72, "foundation:fusion", "hist:fusion_reactors"),
            s("breakthrough", "asteroid_mining", "Розгорнути видобуток астероїдів", "Рідкісні матеріали перестають бути прив’язаними до однієї планети.", "Логістика й аварії в космосі мають величезну ціну.", InterventionKind.TECHNOLOGY_BOOST, 0.66, "foundation:asteroid_mining", "hist:orbital_mines"),
            s("subsistence", "closed_ecologies", "Створити замкнені екосистеми", "Поселення можуть жити далеко від біосфери планети.", "Одна помилка в циклі води чи їжі загрожує всій колонії.", InterventionKind.HARVEST_AID, 0.62, "foundation:closed_ecologies", "hist:habitat_farms"),
            s("subsistence", "engineered_food", "Проєктувати їжу під колонії", "Раціон адаптується до різних гравітацій і середовищ.", "Біологічна одноманітність стає стратегічним ризиком.", InterventionKind.HARVEST_AID, 0.54, "policy:engineered_food", "hist:engineered_crops"),
            s("society", "distributed_governance", "Розподілити владу між колоніями", "Далекі поселення швидше вирішують локальні проблеми.", "Єдність цивілізації слабшає.", InterventionKind.STABILITY_SUPPORT, 0.46, "policy:distributed_governance", "hist:colony_councils"),
            s("society", "ai_coordination", "Доручити координацію штучному інтелекту", "Складні системи працюють узгодженіше.", "Помилка центральної логіки стає системною.", InterventionKind.STABILITY_SUPPORT, 0.52, "policy:ai_coordination", "hist:ai_coordination"),
            s("mobility", "orbital_habitats", "Будувати орбітальні міста", "Населення й виробництво виходять за межі поверхні.", "Життя стає залежним від герметичної інфраструктури.", InterventionKind.TECHNOLOGY_BOOST, 0.60, "foundation:orbital_habitats", "hist:orbital_habitats"),
            s("mobility", "interplanetary_routes", "Відкрити міжпланетні маршрути", "Торгівля й міграція охоплюють кілька світів.", "Відстань робить будь-яку кризу повільною для реагування.", InterventionKind.TECHNOLOGY_BOOST, 0.58, "foundation:interplanetary_routes", "hist:space_routes"),
        ),
    )

    private val allSpecs = specs.values.flatten()

    private fun stableRank(worldSeed: Long, tick: Long, civilizationId: String, slug: String): Long {
        var hash = worldSeed xor tick xor 0xcbf29ce484222325UL.toLong()
        "$civilizationId|$slug".forEach { char ->
            hash = (hash xor char.code.toLong()) * 1099511628211L
        }
        return hash and Long.MAX_VALUE
    }
}
