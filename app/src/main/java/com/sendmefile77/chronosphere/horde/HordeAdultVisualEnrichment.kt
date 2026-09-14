package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra

/**
 * Adult enrichment layer.
 *
 * Contract: consume the same prepared historical tags the non-adult visual
 * pipeline already emitted (`era-choice:`, `foundation:`, `policy:`, `hist:`,
 * cloth/body/publicness, role/status). Do not rewrite those tags or replace
 * [HordeHistoricalVisualPrompt] / [HordeDecisionVisualCue].
 *
 * Pipeline: base historical visual context → this overlay → final adult prompt.
 */
internal object HordeAdultVisualEnrichment {
    fun fragment(
        tags: Set<String>,
        technologyEra: TechnologyEra? = null,
        kind: Kind = Kind.SCENE,
    ): String {
        val parts = linkedSetOf<String>()
        parts += eraBodyCulture(technologyEra, kind)

        selectedSlugs(tags, technologyEra).forEach { slug ->
            parts += HordeAdultDecisionVisualCue.forSlug(slug)
        }

        fun addPrefixed(prefix: String, limit: Int) {
            tags.asSequence()
                .filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix) }
                .filter { raw ->
                    val origin = HordeVisualEraChoiceIndex.eraForSlug(HordeHistoricalTagEra.canonical(raw))
                    origin == null || technologyEra == null ||
                        origin.ordinal >= (technologyEra.ordinal - 1).coerceAtLeast(0)
                }
                .sortedByDescending { raw ->
                    HordeVisualEraChoiceIndex.eraForSlug(HordeHistoricalTagEra.canonical(raw))?.ordinal ?: -1
                }
                .filterNot { HordeHistoricalTagEra.canonical(it) in selectedSlugSet(tags, technologyEra) }
                .take(limit)
                .forEach { parts += HordeAdultDecisionVisualCue.forHistoricalTag(prefix, it) }
        }

        addPrefixed("policy:", 1)
        addPrefixed("foundation:", 1)
        addPrefixed("hist:", 1)

        presentation(tags, technologyEra)?.let { parts += it }
        roleStatus(tags, technologyEra)?.let { parts += it }

        return parts.filter(String::isNotBlank).take(MAX_PARTS).joinToString(", ")
    }

    fun signature(tags: Set<String>): String = buildString {
        append(SIGNATURE_SCHEMA).append('|')
        append(
            tags.asSequence()
                .filter { tag -> PREFIXES.any(tag::startsWith) }
                .sorted()
                .joinToString(";"),
        )
    }

    enum class Kind { PORTRAIT, SCENE, CHRONICLE, SOCIAL }

    private fun eraBodyCulture(era: TechnologyEra?, kind: Kind): String = when (era) {
        TechnologyEra.TRIBAL -> when (kind) {
            Kind.PORTRAIT ->
                "tribal adult body culture: ochre traces, hide straps if any remain, " +
                    "survival musculature, unashamed bare skin"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "tribal public intimacy: sex belongs in the same camp circle as work and fire"
            Kind.SCENE ->
                "tribal intimate culture: closeness to survival and the body, hides, ritual marks, shared heat"
        }
        TechnologyEra.AGRARIAN -> when (kind) {
            Kind.PORTRAIT ->
                "agrarian adult body: work-bare arms, household fertility status, undyed cloth if worn"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "agrarian intimacy in yard, granary or field edge, family and clan status visible"
            Kind.SCENE ->
                "agrarian intimate culture: household bed, fertility, clan rank, dirt-yard privacy"
        }
        TechnologyEra.URBAN -> when (kind) {
            Kind.PORTRAIT ->
                "early-city adult presentation: dyed cloth remnants, kohl or dust, street-aware body"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "urban intimacy using market, workshop and rented rooms, public and private mixed"
            Kind.SCENE ->
                "urban intimate culture: craft-quarter rooms, market cover, status bought with space"
        }
        TechnologyEra.METALLURGIC -> when (kind) {
            Kind.PORTRAIT ->
                "metal-age adult body: soot or rank metal, warrior or smith musculature, status jewellery"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "metal-age public or yard sex beside forges and retainers, hierarchy on the body"
            Kind.SCENE ->
                "metal-age intimate culture: workshop heat, warrior rank, ritualised dominance"
        }
        TechnologyEra.MEDIEVAL -> when (kind) {
            Kind.PORTRAIT ->
                "estate-society adult presentation: rank-gated cloth or deliberate undress, status metal"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "medieval intimacy behind halls, alleys and guild rooms, rank deciding who may watch"
            Kind.SCENE ->
                "medieval intimate culture: solar or rope-bed chamber, estate hierarchy, ritual cover"
        }
        TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> when (kind) {
            Kind.PORTRAIT ->
                "industrial adult body: shift-tired, mill or factory class readable on skin and hands"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "industrial intimacy in alleys, tenements and after-whistle yards, crowded privacy"
            Kind.SCENE ->
                "industrial intimate culture: class, labour, rented rooms, overhearing neighbours"
        }
        TechnologyEra.ELECTRIC -> when (kind) {
            Kind.PORTRAIT ->
                "electrified-era adult body: indoor lighting on skin, tailored remnants, broadcast-age glamour"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "early-modern street and apartment intimacy under wired lamps"
            Kind.SCENE ->
                "electric-era intimate culture: wired rooms, radio in the corner, new indoor privacy"
        }
        TechnologyEra.INFORMATION -> when (kind) {
            Kind.PORTRAIT ->
                "information-age adult body: media-aware presentation, devices present but not the subject"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "networked-city intimacy, private rooms that still know the public feed"
            Kind.SCENE ->
                "information-age intimate culture: screens, surveillance, remote work beds, networked norms"
        }
        TechnologyEra.SPACEFARING -> when (kind) {
            Kind.PORTRAIT ->
                "habitat adult body: cropped or bound hair, sterile sheen, isolation in the face"
            Kind.CHRONICLE, Kind.SOCIAL ->
                "module and concourse intimacy, thin bulkheads, new off-world body manners"
            Kind.SCENE ->
                "spacefaring intimate culture: sealed cabins, artificial gravity, recycled air, isolation"
        }
        null -> "intimate custom kept inside the civilization's current material world"
    }

    private fun presentation(tags: Set<String>, era: TechnologyEra?): String? {
        val body = value(tags, "body-norm:")
        val publicness = value(tags, "publicness:")
        val cloth = value(tags, "cloth:")
        val cosmetic = value(tags, "cosmetic:")
        if (body == null && publicness == null && cloth == null && cosmetic == null) return null
        val bits = mutableListOf<String>()
        when (body) {
            "painted-skin", "painted-nude" -> bits += "ochre or earth paint across breasts, belly and genitals"
            "work-bare-arms", "field-undress" -> bits += "work-undress, cloth pushed aside rather than costume lingerie"
            "open-shoulder", "open-body", "court-decollete" -> bits += "status undress that still belongs to this era's cut of cloth"
            "estate-covered", "ritually-covered" -> bits += "cloth opened only as far as rank and rite allow"
            "corset-street", "soldier-undress" -> bits += "period understructure or kit dropped in the same room"
            "media-body" -> bits += "body presented as if it knows a camera or screen exists"
            "habitat-bare" -> bits += "habitat-bare skin, no earth-era jewellery language"
            null -> Unit
            else -> bits += "body presentation ${humanize(body)} kept historically specific"
        }
        when (publicness) {
            "communal" -> bits += "no locked door, camp-mates may remain in the same circle"
            "household" -> bits += "household-yard privacy, family space not a hotel room"
            "street-visible", "crowd-close" -> bits += "public or crowd-adjacent sex treated as ordinary custom"
            "rank-gated" -> bits += "only status-equals present; attendants stay beyond the threshold"
            "cloistered" -> bits += "closed rite space, witnesses if any are initiated adults"
            "mediated" -> bits += "a device or broadcast object remains in the room"
            "module-private" -> bits += "sealed module privacy, neighbours one hatch away"
            "private" -> bits += "small claimed room inside the settlement, not a generic studio"
            null -> Unit
            else -> bits += "social visibility ${humanize(publicness)}"
        }
        cloth?.let { bits += "any remaining cloth is ${humanize(it)}, never modern lingerie unless the era is contemporary" }
        cosmetic?.let { bits += "cosmetic trace ${humanize(it)} on adult skin" }
        era ?: Unit
        return bits.take(3).joinToString(", ").takeIf { it.isNotBlank() }
    }

    private fun roleStatus(tags: Set<String>, era: TechnologyEra?): String? {
        val role = value(tags, "person_role:") ?: value(tags, "role:")
        val status = value(tags, "person_status:") ?: value(tags, "status:")
        val roleLine = when (role) {
            "ruler" -> "ruler-status intimacy: heir-making or display coupling, the best bed the era can build"
            "commander" -> "commander intimacy: war-kit piled, dominance readable, a military shelter not a boudoir"
            "cleric" -> "cleric intimacy: rite, altar or cloister language, sex framed as sacred or forbidden"
            "merchant" -> "merchant intimacy: paid time, warehouse or upstairs salon, goods in the same room"
            "courtesan" -> "courtesan presentation: professional erotic display using this era's luxury, not modern porn sets"
            "scholar" -> "scholar intimacy: desk, manuscripts or screens nearby, a side chamber off the workplace"
            "artisan" -> "artisan intimacy: workshop tools still out, craft-stained hands on skin"
            else -> null
        }
        val statusLine = status?.takeIf { it.isNotBlank() }?.let { raw ->
            "adult social standing ${humanize(raw)} visible in who may touch whom"
        }
        val eraHint = when (era) {
            TechnologyEra.TRIBAL -> "rank is camp-seniority, not a throne room"
            TechnologyEra.MEDIEVAL -> "rank is estate and hall"
            TechnologyEra.INDUSTRIAL, TechnologyEra.EARLY_INDUSTRIAL -> "rank is class and shift"
            TechnologyEra.INFORMATION -> "rank is access, audience and network"
            TechnologyEra.SPACEFARING -> "rank is module clearance and crew role"
            else -> null
        }
        return listOfNotNull(roleLine, statusLine, eraHint).take(2).joinToString(", ").takeIf { it.isNotBlank() }
    }

    private data class EraChoice(val family: String, val slug: String)

    private fun eraChoices(tags: Set<String>): List<EraChoice> = tags.asSequence()
        .filter { it.startsWith("era-choice:") }
        .mapNotNull { tag ->
            val parts = tag.removePrefix("era-choice:").split(':', limit = 2)
            if (parts.size == 2 && parts.all(String::isNotBlank)) EraChoice(parts[0], parts[1]) else null
        }
        .toList()

    private fun selectedSlugs(tags: Set<String>, era: TechnologyEra?): List<String> {
        val lifestyle = eraChoices(tags)
            .filterNot { it.family == "breakthrough" }
            .sortedBy { it.family }
            .map { it.slug }
            .take(2)
        val breakthroughs = eraChoices(tags)
            .filter { it.family == "breakthrough" }
            .filter { HordeVisualEraChoiceIndex.shouldEmphasizeBreakthrough(it.slug, era) }
            .sortedByDescending { HordeVisualEraChoiceIndex.eraForSlug(it.slug)?.ordinal ?: -1 }
            .map { it.slug }
            .take(1)
        return (lifestyle + breakthroughs).distinct()
    }

    private fun selectedSlugSet(tags: Set<String>, era: TechnologyEra?): Set<String> =
        selectedSlugs(tags, era).toSet()

    private fun value(tags: Set<String>, prefix: String): String? = tags.asSequence()
        .filter { it.startsWith(prefix) && it.length > prefix.length }
        .map { it.removePrefix(prefix) }
        .sorted()
        .firstOrNull()

    private fun humanize(value: String): String =
        value.replace('_', ' ').replace('-', ' ').replace('+', ' ')

    private const val SIGNATURE_SCHEMA = "adult-enrich-v1"
    private const val MAX_PARTS = 7

    private val PREFIXES = listOf(
        "era-choice:", "foundation:", "policy:", "hist:", "era:",
        "cloth:", "body-norm:", "publicness:", "cosmetic:",
        "person_role:", "person_status:", "role:", "status:",
    )
}
