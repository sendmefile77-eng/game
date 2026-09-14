package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.textgen.ChronicleNarrative
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator

internal data class ChronicleStoryBeat(
    val eventId: String,
    val tick: Long,
    val title: String,
    val summary: String,
)

internal data class ChronicleStory(
    val title: String,
    val lead: String,
    val paragraphs: List<String>,
    val beats: List<ChronicleStoryBeat>,
)

/** User-facing prose layer. Internal event codes never leak into the chronicle. */
internal object ChroniclePresentation {
    fun narrative(
        event: SimulationEvent,
        textGenerator: ChronicleTextGenerator,
        era: TechnologyEra? = null,
    ): ChronicleNarrative {
        val civilization = event.facts["civilization"]?.takeIf { it.isNotBlank() }
        val settlement = event.facts["settlement"]?.takeIf { it.isNotBlank() }
        val person = event.facts["person"]?.takeIf { it.isNotBlank() }
        val participants = event.facts["participants"]?.takeIf { it.isNotBlank() }
        val eraName = era?.displayNameUk?.lowercase()
        return when (event.code) {
            "ADULT_SOCIAL_EVENT" -> adultNarrative(
                civilization,
                settlement,
                participants,
                event.facts["eventCode"],
                era,
                event.facts["scandal"],
                event.facts["significance"],
            )
            "SETTLEMENT_FOUNDED", "COLONY_FOUNDED" -> ChronicleNarrative(
                title = listOfNotNull(civilization, settlement).joinToString(" · ").ifBlank { "Нове осідлення" },
                hook = ChronicleEraVoice.foundingHook(era, civilization, settlement),
                body = ChronicleEraVoice.foundingBody(era, civilization, settlement),
                significance = ChronicleEraVoice.foundingWhy(era, settlement),
                changes = listOfNotNull(settlement?.let { "Осередок: $it" }, eraName?.let { "Епоха: $it" }),
            )
            "RULER_SUCCEEDED", "DYNASTY_FOUNDED" -> ChronicleNarrative(
                title = listOfNotNull(civilization, person).joinToString(": ").ifBlank { "Нова влада" },
                hook = ChronicleEraVoice.rulerHook(era, civilization, person),
                body = ChronicleEraVoice.rulerBody(era, civilization, person),
                significance = ChronicleEraVoice.rulerWhy(era, person),
                changes = listOfNotNull(person?.let { "На чолі: $it" }),
            )
            "ERA_ADVANCED" -> ChronicleNarrative(
                title = "${civilization ?: "Держава"} входить у ${eraName ?: "нову епоху"}",
                hook = ChronicleEraVoice.eraHook(era, civilization),
                body = ChronicleEraVoice.eraBody(era, civilization, settlement),
                significance = ChronicleEraVoice.eraWhy(era),
                changes = listOfNotNull(eraName?.let { "Новий поріг: $it" }),
            )
            else -> {
                val base = textGenerator.narrative(event)
                if (era == null) base else base.copy(
                    hook = "${base.hook} ${ChronicleEraVoice.texture(era)}",
                    body = "${base.body} ${ChronicleEraVoice.texture(era)}",
                )
            }
        }
    }

    private fun adultNarrative(
        civilization: String?,
        settlement: String?,
        participants: String?,
        code: String?,
        era: TechnologyEra?,
        scandal: String?,
        significance: String?,
    ): ChronicleNarrative {
        val practice = socialPracticeName(code, era)
        val where = settlement ?: civilization ?: "поселенні"
        val who = participants ?: "двоє повнолітніх"
        val kind = significance ?: if (scandal != null) "scandal" else "liaison"
        val title = when (kind) {
            "scandal" -> "$where · скандал"
            "union" -> "$where · союз"
            "dynastic" -> "$where · династичне ложе"
            "norm-shift" -> "$where · зміна звичаю"
            else -> "$where · $practice"
        }
        return ChronicleNarrative(
            title = title,
            hook = when (kind) {
                "scandal" -> ChronicleEraVoice.scandalHook(era, who, where)
                "union", "dynastic" -> ChronicleEraVoice.unionHook(era, who, where)
                else -> ChronicleEraVoice.eroticHook(era, who, where)
            },
            body = ChronicleEraVoice.eroticBody(era, who, where, practice),
            significance = when (kind) {
                "scandal" -> ChronicleEraVoice.scandalWhy(era, who)
                "union", "dynastic" -> ChronicleEraVoice.unionWhy(era, who)
                else -> ChronicleEraVoice.eroticWhy(era, who)
            },
            changes = listOf("Учасники: $who", "Звичай: $practice"),
        )
    }

    internal fun socialPracticeName(code: String?, era: TechnologyEra?): String = when (code?.lowercase()) {
        "bondage_rite", "bondage rite" -> when (era) {
            TechnologyEra.METALLURGIC -> "зв'язування ременем біля горна"
            TechnologyEra.TRIBAL -> "обряд шкіряних стрічок біля вогню"
            else -> "обряд зв'язування і довіри"
        }
        "union" -> "шлюб на видиму усього осередку"
        "orgy" -> when (era) {
            TechnologyEra.METALLURGIC -> "нічне сплетіння біля печей"
            else -> "спільний нічний обряд"
        }
        "anal_union", "anal union" -> "тілесний союз двох дорослих"
        "cum_rite", "cum rite" -> when (era) {
            TechnologyEra.METALLURGIC -> "ритуал сім'я на розпеченому камені"
            TechnologyEra.TRIBAL -> "ритуал сім'я біля вогнища"
            TechnologyEra.AGRARIAN -> "ритуал сім'я на гумні току"
            else -> "тілесний ритуал сім'я"
        }
        "fertility_rite", "fertility rite" -> "обряд плодючості"
        "public_union", "public union" -> "публічний шлюб на майдані"
        "dominance_rite", "dominance rite" -> "ритуал влади і піддання"
        null, "" -> "відкрита близькість на вулиці"
        else -> "локальний обряд близькості"
    }
}

internal object ChronicleEraVoice {
    fun texture(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL -> "Повітря пахне димом вогнища, шкурою і сирою землею."
        TechnologyEra.AGRARIAN -> "Повітря пахне током, гноєм і свіжим деревом стін."
        TechnologyEra.URBAN -> "Повітря живе тіснотою вулиць, глиняним посудом і голосами торгу."
        TechnologyEra.METALLURGIC -> "Повітря пахне гарячою міддю, димом горнів і ударами молота."
        TechnologyEra.MEDIEVAL -> "Повітря пахне воском, воском свічок і сирою кам'яною."
        TechnologyEra.EARLY_INDUSTRIAL -> "Повітря пахне сажею і чути стукіт паром."
        TechnologyEra.INDUSTRIAL -> "Повітря живе чадом фабрик і брудом рейк."
        TechnologyEra.ELECTRIC -> "Повітря горить проводами і лампами над вулицею."
        TechnologyEra.INFORMATION -> "Повітря живе екранами і склом високих вікон."
        TechnologyEra.SPACEFARING -> "Повітря йде під гулом ілюмінатора, а не під небом."
        null -> ""
    }

    fun foundingHook(era: TechnologyEra?, civ: String?, settlement: String?): String {
        val who = civ ?: "Нарід"
        val where = settlement ?: "нове місце"
        return when (era) {
            TechnologyEra.TRIBAL -> "$who ставить $where як стійкий табір: шкури, вогонь, м'ясо на жердинах."
            TechnologyEra.AGRARIAN -> "$who оре першу межу навколо $where — це вже не кочівля, а двір із гумном під ногами."
            TechnologyEra.METALLURGIC -> "$who закладає $where біля руди: перший горн горить ще до того, як стіни стають цілими."
            else -> "$who засновує $where — це вже не тимчасовий табір, а місце, де люди зимують і родять дітей."
        }
    }

    fun foundingBody(era: TechnologyEra?, civ: String?, settlement: String?): String {
        val who = civ ?: "Нарід"
        val where = settlement ?: "осередок"
        val extra = texture(era)
        return "$who перестає кочувати й осідає $where. Тут з'являються склади, спільне вогнище і двори, за якими видно, хто з ким спить. $extra"
    }

    fun foundingWhy(era: TechnologyEra?, settlement: String?): String =
        "Відтепер ${settlement ?: "це місце"} може копити зброю, тримати любовників і стати причиною війни. ${texture(era)}"

    fun rulerHook(era: TechnologyEra?, civ: String?, person: String?): String {
        val who = person ?: "новий правитель"
        val land = civ ?: "держава"
        return when (era) {
            TechnologyEra.METALLURGIC -> "$who бере $land не з вінця, а з-під молота: хто тримає горн, той тримає людей."
            TechnologyEra.TRIBAL -> "$who сідає біля вогню $land — влада тут ще пахне димом, а не печаткою."
            else -> "$who очолює $land. Ім'я на чолі змінює, хто спить у головному будинку і хто ріже хліб."
        }
    }

    fun rulerBody(era: TechnologyEra?, civ: String?, person: String?): String {
        val who = person ?: "Новий вождь"
        val land = civ ?: "народ"
        return "$who стає особою, від якої залежить і війна, і те, з ким ділять ліжко наверху. У $land це ще не канцелярія — влада видна на майдані і в постелі. ${texture(era)}"
    }

    fun rulerWhy(era: TechnologyEra?, person: String?): String =
        "Династія ${person ?: "нового вождя"} тепер вирішуватиме, кого брати в союз і кого відсилати від вогню. ${texture(era)}"

    fun eraHook(era: TechnologyEra?, civ: String?): String {
        val who = civ ?: "Нарід"
        return when (era) {
            TechnologyEra.AGRARIAN -> "$who більше не живе з полювання: появляються поля, загони, запаси на зиму."
            TechnologyEra.METALLURGIC -> "$who вчиться топити руду. Мідь стає дорожче за камінну сокиру."
            TechnologyEra.URBAN -> "$who стискає стіни вулицями: з'являються крамниці, майстри, публічні двори."
            else -> "$who перетинає поріг епохи — інші знаряддя, інший ритм дня і інша близькість."
        }
    }

    fun eraBody(era: TechnologyEra?, civ: String?, settlement: String?): String {
        val who = civ ?: "Нарід"
        val where = settlement?.let { " у $it" } ?: ""
        return when (era) {
            TechnologyEra.METALLURGIC ->
                "$who$where ставить горни поруч із хатами. Увечері жар печі йде на вулицю: ковалі і жінки кузнів не ховають тіла, коли справа спрага. ${texture(era)}"
            TechnologyEra.AGRARIAN ->
                "$who$where живе від поля. Після зжин близькість стає частиною свята — не в храмі, а на гумні току. ${texture(era)}"
            else -> "$who$where змінює речі, якими робить, і речі, якими кохається. ${texture(era)}"
        }
    }

    fun eraWhy(era: TechnologyEra?): String = when (era) {
        TechnologyEra.METALLURGIC -> "Хто вміє топити метал, той вміє ламати зброю і вирішувати, кого брати в ліжко."
        else -> "Нова епоха міняє не лише знаряддя — вона міняє, як люди їдять, сваряться і тримають тіла одне одного."
    }

    fun eroticHook(era: TechnologyEra?, who: String, where: String): String = when (era) {
        TechnologyEra.METALLURGIC ->
            "$who не ховаються за дверима $where: біля горна видно, хто кого тримає за стегна і кому належить ніч."
        TechnologyEra.TRIBAL ->
            "$who лягають на шкурах біля вогню $where — це не сором, а те, як тут закріплюють союз."
        TechnologyEra.AGRARIAN ->
            "$who йдуть у стодол після зжин у $where. Земля і тіло тут одна мова."
        else ->
            "$who роблять близькість частиною дня $where, а не таємницею за стіною."
    }

    fun eroticBody(era: TechnologyEra?, who: String, where: String, practice: String): String = when (era) {
        TechnologyEra.METALLURGIC ->
            "У $where кузні працюють дотемна. $who знімаю фартухи біля розпеченого каменя: $practice. " +
                "Піт і сажа мішаються з димом горна. Це не метафора для хроніки — так тут справді закріплюють, хто кому належить."
        TechnologyEra.TRIBAL ->
            "$who у $where знімають шкури біля вогнища. $practice видно всьому табору: хто лягає ближче до жару, той ближче до влади."
        TechnologyEra.AGRARIAN ->
            "Після збору у $where $who йдуть у стодол і на ток. $practice тут читають як обіцянку врожаючості землі — без церкви і без сорому."
        else ->
            "У $where $who роблять $practice на очах. Хроніка це записує, бо так видно, як насправді устроєне їхнє суспільство — хто має право на чуже тіло, а хто лише дивиться."
    }

    fun eroticWhy(era: TechnologyEra?, who: String): String =
        "Імена $who залишаються в пам'яті не через указ, а через те, кого бачили голим у центрі осередку. ${texture(era)}"

    fun scandalHook(era: TechnologyEra?, who: String, where: String): String =
        "$who порушили пару в $where. Це вже не приватна ніч — від цього залежить напруга в домі і на майдані."

    fun scandalWhy(era: TechnologyEra?, who: String): String =
        "Скандал навколо $who змінює репутацію і ревнощі в оселі. ${texture(era)}"

    fun unionHook(era: TechnologyEra?, who: String, where: String): String =
        "$who закріплюють союз у $where. Ліжко тут читають як політичний факт, не лише як тіло."

    fun unionWhy(era: TechnologyEra?, who: String): String =
        "Союз $who тримає спадкоємців, союзників і право говорити від імені дому. ${texture(era)}"

    fun cityLead(era: TechnologyEra?, civ: String, settlement: String?): String {
        val where = settlement ?: civ
        return when (era) {
            TechnologyEra.METALLURGIC ->
                "$civ живе металургією. У $where горни горять цілу ніч, а біля них — не лише слуги, а й тіла. Це вже не табір і ще не місто з мурами: вулиця пахне сажею і сексом."
            TechnologyEra.TRIBAL ->
                "$civ тримаєть $where як вогнище посеред дикого краю. Шкури, кістки, сире м'ясо — і люди, які не ховають тіла від племені."
            TechnologyEra.AGRARIAN ->
                "$civ осіли $where між полів. День починається з рала, а кінчається тим, що пари йдуть у стодол прямо з грядки."
            else ->
                "$civ у $where живе своєю епохою навиворіт: праця, влада і близькість відбуваються на одній вулиці. ${texture(era)}"
        }
    }
}

internal object ChronicleStoryComposer {
    fun compose(
        events: List<SimulationEvent>,
        civilizationNames: Map<String, String>,
        textGenerator: ChronicleTextGenerator,
        era: TechnologyEra? = null,
    ): ChronicleStory? {
        if (events.isEmpty()) return null

        val window = events.takeLast(72)
        val focusId = window.asReversed()
            .flatMap { event -> event.actorIds.asReversed() }
            .filter { it in civilizationNames }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { entry ->
                window.indexOfLast { entry.key in it.actorIds }
            })
            ?.key
        val focusName = focusId?.let(civilizationNames::get)
        val focused = if (focusId == null) window else window.filter { focusId in it.actorIds }.ifEmpty { window }

        val deduped = focused.fold(mutableListOf<SimulationEvent>()) { acc, event ->
            val previous = acc.lastOrNull()
            val sameBurst = previous != null &&
                previous.code == event.code &&
                (previous.tick == event.tick ||
                    event.code == "ADULT_SOCIAL_EVENT" && event.tick - previous.tick <= 12L)
            if (sameBurst) acc[acc.lastIndex] = event else acc += event
            acc
        }

        val selected = selectTurningPoints(deduped, 8)
        if (selected.isEmpty()) return null
        val narratives = selected.map { event -> event to ChroniclePresentation.narrative(event, textGenerator, era) }
        val settlement = selected.asReversed().firstNotNullOfOrNull { it.facts["settlement"]?.takeIf(String::isNotBlank) }
        val eraLabel = era?.displayNameUk

        val title = when {
            focusName != null && eraLabel != null -> "$focusName · $eraLabel"
            focusName != null -> "$focusName · жива історія"
            else -> "Світ набуває власної історії"
        }
        val lead = if (focusName != null) {
            ChronicleEraVoice.cityLead(era, focusName, settlement)
        } else {
            "Хроніка збирає ${narratives.size} поворотів в одну історію буднів і тіл."
        }

        val paragraphs = narratives.map { (_, narrative) -> narrative.body }.filter { it.isNotBlank() }.distinct()

        val beats = narratives.map { (event, narrative) ->
            ChronicleStoryBeat(
                eventId = event.id,
                tick = event.tick,
                title = narrative.title,
                summary = narrative.hook,
            )
        }
        return ChronicleStory(title, lead, paragraphs, beats)
    }

    private fun selectTurningPoints(events: List<SimulationEvent>, limit: Int): List<SimulationEvent> {
        if (events.size <= limit) return events
        val priorityCodes = setOf(
            "WAR_STARTED", "PEACE_TREATY", "CITY_CAPTURED", "ERA_ADVANCED",
            "RULER_SUCCEEDED", "DYNASTY_FOUNDED", "BIOLOGICAL_DIVERGENCE",
            "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED", "PLAYER_STRUCTURAL_MUTATION",
            "PLAYER_HYBRIDIZATION", "ALLIANCE_FORMED", "ALLIANCE_ENDED",
            "SETTLEMENT_FOUNDED", "ADULT_SOCIAL_EVENT",
        )
        val chosen = linkedSetOf<SimulationEvent>()
        chosen += events.first()
        events.filter { it.code in priorityCodes }.forEach(chosen::add)
        events.asReversed().forEach { event -> if (chosen.size < limit) chosen += event }
        chosen += events.last()
        return chosen.sortedBy { it.tick }.takeLast(limit)
    }
}
