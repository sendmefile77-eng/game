package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.history.ActiveHistoricalContextRegistry
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent

internal const val CHRONICLE_EVENT_CACHE_SCHEMA = "horde-chronicle-event-v10-war-era-subject"

/** Wide chronicle frame: era city life with the material consequences of player choices visible. */
object HordeChronicleEventPromptFactory {
    private val preferredModels = listOf(
        "WAI-NSFW-illustrious-SDXL",
        "AlbedoBase XL (SDXL)",
        "CyberRealistic Pony",
        "AbsoluteReality",
    )

    private val significantCodes = setOf(
        "SETTLEMENT_FOUNDED", "COLONY_FOUNDED", "WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED",
        "PEACE_TREATY", "ALLIANCE_FORMED", "ERA_ADVANCED", "RULER_SUCCEEDED", "DYNASTY_FOUNDED",
        "ADULT_SOCIAL_EVENT", "BIOLOGICAL_DIVERGENCE", "STRUCTURAL_MUTATION", "HYBRID_LINEAGE_FORMED",
        "PLAYER_EVOLUTION_DIVERGENCE", "PLAYER_STRUCTURAL_MUTATION", "PLAYER_HYBRIDIZATION",
        "INTERVENTION_HARVEST_AID", "INTERVENTION_DROUGHT", "INTERVENTION_TECH_BOOST",
        "INTERVENTION_STABILITY_SUPPORT", "INTERVENTION_FESTIVAL", "INTERVENTION_EMBASSY",
        "INTERVENTION_WAR_RAID",
    )

    private val warCodes = setOf(
        "WAR_STARTED", "WAR_CASUALTIES", "CITY_CAPTURED", "INTERVENTION_WAR_RAID",
    )

    fun latestSignificant(events: List<SimulationEvent>): SimulationEvent? =
        events.asReversed().firstOrNull { it.code in significantCodes }

    fun create(event: SimulationEvent, people: PeopleState, economy: EconomyState? = null): HordeImageRequest {
        val participants = event.actorIds.mapNotNull { id -> people.persons.firstOrNull { it.id == id } }
        val hasMinor = participants.any { it.ageYearsAt(event.tick) < 18 }
        val era = resolveEra(event, people, economy)
        val erotic = !hasMinor
        val settlement = event.facts["settlement"]?.takeIf { it.isNotBlank() }
        val choiceVisual = HordeHistoricalVisualPrompt.eraChoiceFragment(event.facts["choiceId"])
        val choiceLabel = event.facts["choiceLabel"]?.takeIf { it.isNotBlank() }
        val persistentTags = persistentHistoricalTags(event, people, economy)
        val persistentVisualRaw = HordeHistoricalVisualPrompt.fragment(persistentTags, era)
        val persistentVisual = if (erotic) HordeAdultSubjectGuard.sanitize(persistentVisualRaw) else persistentVisualRaw
        val persistentSignature = HordeHistoricalVisualPrompt.signature(persistentTags)
        val warFrame = event.code in warCodes
        val eraReward = event.code == "ERA_ADVANCED"

        val positiveRaw = buildList {
            add("masterpiece, best quality, anime illustration, cinematic wide establishing shot")
            add(HordeEraVisual.materialCulture(era))
            add(HordeEraVisual.distinctiveMarker(era))
            add(sceneWork(event, era, settlement))
            if (persistentVisual.isNotBlank()) {
                add("persistent historical way of life must remain materially visible in clothing, tools, work and surroundings: $persistentVisual")
                add("these are established daily-life consequences, not temporary decorations or symbolic icons")
            }
            if (choiceVisual.isNotBlank()) {
                add("the newly chosen way of life must be unmistakably visible in the main action: $choiceVisual")
                add("show the practical material consequence, not a symbolic icon or caption")
                if (erotic) {
                    val adultChoice = HordeAdultDecisionVisualCue.forChoiceId(event.facts["choiceId"])
                    if (adultChoice.isNotBlank()) {
                        add("the same choice also shapes the adult custom in this frame: $adultChoice")
                    }
                }
            }
            if (erotic) {
                add(HordeAdultSubjectGuard.PERSON_LOCK)
                if (warFrame) {
                    add(warErotica(era))
                } else if (eraReward) {
                    add(eraRewardErotica(era))
                } else {
                    add(HordeEraVisual.cityErotica(era))
                    val adultOverlay = HordeAdultVisualEnrichment.fragment(
                        persistentTags,
                        era,
                        HordeAdultVisualEnrichment.Kind.CHRONICLE,
                    )
                    if (adultOverlay.isNotBlank()) {
                        add("adult custom must stay inside this settlement's way of life: $adultOverlay")
                    }
                    add("explicit consensual adult sex in the same frame as ordinary city work")
                    add("nude adult woman and nude adult man, visible breasts, nipples, penis, vagina, wet skin")
                    add("other clothed workers continue their jobs around them, no one shocked")
                }
            } else {
                add("everyday public work, fully clothed adults, no sexual content")
            }
            add("readable human or humanoid bodies as the subject, environmental storytelling, complete connected bodies")
            add("natural era-correct light, detailed materials, no text, no UI, no modern bathroom")
        }.joinToString(", ")
        val positive = if (erotic) HordeAdultSubjectGuard.sanitize(positiveRaw) else positiveRaw

        val negative = buildList {
            add("worst quality, low quality, blurry, bad anatomy, extra limbs, duplicate people")
            add("disconnected limbs, floating head, cropped face, text, watermark, collage, split screen")
            add("oversaturated, overcooked, burnt colors, neon cyberpunk")
            addAll(HordeEraVisual.negatives(era).take(10))
            addAll(HordeAdultSubjectGuard.animalSubjectNegatives(chimeric = false).take(12))
            if (hasMinor) add("nudity, explicit sex, sexualized minor")
            add("child, loli, shota, underage")
        }.joinToString(", ")

        val eraSignature = era?.name ?: "UNSPECIFIED"
        val adultMinAge = participants.minOfOrNull { it.ageYearsAt(event.tick) }?.coerceAtLeast(0) ?: 21
        return HordeImageRequest(
            cacheKey = listOf(
                CHRONICLE_EVENT_CACHE_SCHEMA,
                event.id,
                event.tick.toString(),
                event.code,
                eraSignature,
                event.facts["choiceId"].orEmpty(),
                choiceLabel.orEmpty(),
                persistentSignature,
                if (erotic) HordeAdultVisualEnrichment.signature(persistentTags) else "sfw",
                event.actorIds.sorted().joinToString(","),
                event.locationId.orEmpty(),
                if (erotic) "nsfw" else "safe",
            ).joinToString("|"),
            positivePrompt = positive,
            negativePrompt = negative,
            nsfw = erotic,
            ageYears = if (erotic) adultMinAge.coerceAtLeast(18) else adultMinAge,
            width = 1024,
            height = 576,
            steps = 22,
            cfgScale = 5.5,
            seed = "chronosphere:chronicle-choice:$eraSignature:${event.id}:${event.facts["choiceId"].orEmpty()}:$persistentSignature",
            preferredModels = preferredModels,
            qualityPriority = false,
            referenceCacheKey = null,
            saveResultAsReference = false,
        )
    }

    private fun persistentHistoricalTags(
        event: SimulationEvent,
        people: PeopleState,
        economy: EconomyState?,
    ): Set<String> {
        val active = ActiveHistoricalContextRegistry.snapshot(people.worldSeed) ?: return emptySet()
        val civilizationIds = economy?.civilizations?.mapTo(hashSetOf()) { it.civilizationId }.orEmpty()
        val actorCivilizations = event.actorIds.mapNotNull { actorId ->
            when {
                actorId in civilizationIds -> actorId
                else -> people.persons.firstOrNull { it.id == actorId }?.civilizationId
            }
        }.distinct()
        val fallback = if (actorCivilizations.isEmpty() && active.cultureTagsByCivilization.size == 1) {
            active.cultureTagsByCivilization.keys.toList()
        } else {
            emptyList()
        }
        return (actorCivilizations + fallback)
            .asSequence()
            .flatMap { civilizationId -> active.cultureTagsByCivilization[civilizationId].orEmpty().asSequence() }
            .filter(::isHistoricalVisualTag)
            .toSortedSet()
    }

    private fun isHistoricalVisualTag(tag: String): Boolean = HISTORY_PREFIXES.any(tag::startsWith)

    private fun resolveEra(event: SimulationEvent, people: PeopleState, economy: EconomyState?): TechnologyEra? {
        if (economy == null) return null
        event.actorIds.firstNotNullOfOrNull { id -> economy.economy(id)?.era }?.let { return it }
        val civilizationId = event.actorIds.firstNotNullOfOrNull { id ->
            people.persons.firstOrNull { it.id == id }?.civilizationId
        }
        civilizationId?.let { economy.economy(it)?.era }?.let { return it }
        return economy.civilizations.maxByOrNull { it.grossOutput }?.era
    }

    private fun sceneWork(event: SimulationEvent, era: TechnologyEra?, settlement: String?): String {
        val place = settlement?.let { "in the settlement $it" } ?: "in the main settlement"
        val work = when (era) {
            TechnologyEra.TRIBAL -> "hide-working, butchering, hearth-tending $place"
            TechnologyEra.AGRARIAN -> "threshing, hauling grain, tending pens $place"
            TechnologyEra.URBAN -> "market stalls, potters, packed street $place"
            TechnologyEra.METALLURGIC -> "open-air forge yard, bellows, pouring bronze $place"
            TechnologyEra.MEDIEVAL -> "guild street, castle-town lane $place"
            TechnologyEra.EARLY_INDUSTRIAL -> "brick mill yard and chimneys $place"
            TechnologyEra.INDUSTRIAL -> "factory gates and rail sidings $place"
            TechnologyEra.ELECTRIC -> "wired boulevard and workshop fronts $place"
            TechnologyEra.INFORMATION -> "contemporary downtown sidewalk $place"
            TechnologyEra.SPACEFARING -> "habitat concourse $place"
            null -> "everyday civic work $place"
        }
        val choice = event.facts["choiceLabel"]?.takeIf { it.isNotBlank() }
        return when {
            choice != null -> "a century-defining change is being adopted in everyday life, $work"
            event.code == "SETTLEMENT_FOUNDED" || event.code == "COLONY_FOUNDED" -> "first permanent shelters rising, $work"
            event.code == "WAR_STARTED" -> warMuster(era, place)
            event.code == "WAR_CASUALTIES" -> warAftermath(era, place)
            event.code == "CITY_CAPTURED" -> warCapture(era, place)
            event.code == "INTERVENTION_WAR_RAID" -> warRaid(era, place)
            event.code == "PEACE_TREATY" -> "adult envoys meeting under era-correct standards $place, weapons grounded"
            event.code == "ERA_ADVANCED" -> eraRewardScene(era, place, work)
            event.code == "RULER_SUCCEEDED" || event.code == "DYNASTY_FOUNDED" -> "a new leader walking the same working street, $work"
            event.code == "ADULT_SOCIAL_EVENT" -> "public erotic custom happening in the work yard, $work"
            else -> work
        }
    }

    private fun warMuster(era: TechnologyEra?, place: String): String = when (era) {
        TechnologyEra.TRIBAL ->
            "war tableau: adult fighters mustering at the camp edge $place, spears and hide shields, people filling the frame"
        TechnologyEra.AGRARIAN, TechnologyEra.URBAN ->
            "war tableau: adult militia gathering on the packed street $place, clubs and slings, banners of raw cloth"
        TechnologyEra.METALLURGIC ->
            "war tableau: bronze weapons issued beside the forge $place, adult fighters in rank"
        TechnologyEra.MEDIEVAL ->
            "war tableau: levy and retainers at the castle-town gate $place, polearms and mail"
        TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL ->
            "war tableau: conscripts and factory smoke $place, rifles stacked, adult bodies in kit"
        TechnologyEra.ELECTRIC, TechnologyEra.INFORMATION ->
            "war tableau: uniformed adults deploying through the city $place"
        TechnologyEra.SPACEFARING ->
            "war tableau: armed crew in a habitat lock $place, vacuum-ready kit"
        null -> "war tableau: adult fighters gathering $place"
    }

    private fun warAftermath(era: TechnologyEra?, place: String): String =
        "aftermath of fighting $place: exhausted and wounded adult bodies in an era-correct camp, people remain the subject, ${era?.name ?: "early"} material world"

    private fun warCapture(era: TechnologyEra?, place: String): String =
        "captured gate and street $place, new banners over the same working settlement, adult victors and residents in one frame, ${era?.name ?: "early"} kit"

    private fun warRaid(era: TechnologyEra?, place: String): String =
        "raid in progress $place: adult raiders crossing the working edge of the settlement with era-correct weapons"

    private fun eraRewardScene(era: TechnologyEra?, place: String, work: String): String = when (era) {
        TechnologyEra.TRIBAL ->
            "epochal reward tableau $place: the tribal age arriving as a living camp, new hearths and hide architecture, adult bodies of this age standing in the new world"
        TechnologyEra.AGRARIAN ->
            "epochal reward tableau $place: fields and grain stores now define daily life, new tools in adult hands"
        TechnologyEra.URBAN ->
            "epochal reward tableau $place: the first dense street of this city age, workshops and packed houses"
        TechnologyEra.METALLURGIC ->
            "epochal reward tableau $place: forge-light and new metal tools appearing in ordinary adult hands"
        TechnologyEra.MEDIEVAL ->
            "epochal reward tableau $place: estate and guild street of the new age, timber halls risen"
        TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL ->
            "epochal reward tableau $place: mills, chimneys and brick yards of the industrial age now standing"
        TechnologyEra.ELECTRIC ->
            "epochal reward tableau $place: wired light reaching the street for the first time"
        TechnologyEra.INFORMATION ->
            "epochal reward tableau $place: screens and civic terminals of the information age in public use"
        TechnologyEra.SPACEFARING ->
            "epochal reward tableau $place: the habitat concourse of the off-world age, viewports and sealed air"
        null -> "epochal reward tableau: new tools appearing in ordinary hands, $work"
    }

    private fun warErotica(era: TechnologyEra?): String = buildString {
        append("adult war-camp intimacy: nude or half-stripped adult fighters and camp followers, ")
        append("visible breasts, nipples, penis, vagina, sex or displayed bodies after the muster, ")
        append("weapons and era kit still in the same frame, people first never animals, ")
        append(
            when (era) {
                TechnologyEra.TRIBAL -> "hide bedding and spears beside the sex"
                TechnologyEra.METALLURGIC -> "forge-heat and bronze beside the sex"
                TechnologyEra.MEDIEVAL -> "gate-yard and mail piled while bodies couple"
                TechnologyEra.INDUSTRIAL, TechnologyEra.EARLY_INDUSTRIAL -> "kit bags and factory brick behind the sex"
                TechnologyEra.SPACEFARING -> "bulkhead bunk intimacy after the lock alarm"
                else -> "war-camp sex inside this era's material world"
            },
        )
    }

    private fun eraRewardErotica(era: TechnologyEra?): String = buildString {
        append("epochal body reward: nude adult woman and nude adult man of the new age, ")
        append("visible breasts, nipples, penis, vagina, wet skin, explicit consensual sex, ")
        append("they use or stand among the new tools so the era change stays readable, ")
        append(
            when (era) {
                TechnologyEra.TRIBAL -> "ochre and hides on skin beside the new hearth"
                TechnologyEra.AGRARIAN -> "grain dust and yard dirt on bare skin"
                TechnologyEra.URBAN -> "market-lane sex as the city age begins"
                TechnologyEra.METALLURGIC -> "soot and new bronze against bare skin"
                TechnologyEra.MEDIEVAL -> "hall or guild-lane sex of the estate age"
                TechnologyEra.INDUSTRIAL, TechnologyEra.EARLY_INDUSTRIAL -> "after-whistle tenement sex of the machine age"
                TechnologyEra.INFORMATION -> "screen-lit apartment sex of the networked age"
                TechnologyEra.SPACEFARING -> "module-bunk sex of the off-world age"
                else -> "new-age bodies inside the new material world"
            },
        )
    }

    private val HISTORY_PREFIXES = listOf(
        "cloth:", "jewel:", "hair:", "body-norm:", "arch:", "set-bias:", "cosmetic:", "publicness:",
        "hist:", "foundation:", "policy:", "era-choice:", "era:",
    )
}
