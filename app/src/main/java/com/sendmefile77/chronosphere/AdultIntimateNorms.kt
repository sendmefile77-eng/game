package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.people.RelationshipKind
import com.sendmefile77.chronosphere.people.SocialProfile

/**
 * Living sexual culture for one civilization at one moment.
 * Base era model is then bent by privacy, piety, openness, hierarchy and historical tags.
 */
data class AdultIntimateNorms(
    val era: TechnologyEra?,
    val partnership: PartnershipForm,
    val bodyPublicness: BodyPublicness,
    val setting: String,
    val mood: String,
    val tabooSummary: String,
    val allowedActs: List<AdultActionType>,
    val preferredBond: RelationshipKind,
    val affairChance: Double,
    val fertilityEmphasis: Double,
    val scandalSensitivity: Double,
    val courtship: String = "",
    val eliteVsCommon: String = "",
    val clothing: String = "",
    val conflict: String = "",
    val visualSignature: String = "",
) {
    enum class PartnershipForm { CAMP_SHARE, HOUSEHOLD, RANKED_HOUSEHOLD, MONOGAMOUS, SERIAL, NETWORKED }
    enum class BodyPublicness { SHARED_CIRCLE, YARD, RANK_GATED, PRIVATE_ROOM, MEDIATED, MODULE }

    fun allows(type: AdultActionType): Boolean = type in allowedActs

    companion object {
        fun resolve(
            era: TechnologyEra?,
            profile: SocialProfile?,
            tags: Set<String> = emptySet(),
            role: PersonRole? = null,
        ): AdultIntimateNorms {
            val base = eraBase(era)
            val piety = profile?.piety ?: 0.45
            val openness = profile?.bodyOpenness ?: 0.45
            val privacy = profile?.privacy ?: 0.45
            val pair = profile?.pairBonding ?: 0.55
            val jealousy = profile?.jealousy ?: 0.45
            val fertility = profile?.fertilityNorm ?: 0.5
            val hierarchy = profile?.statusHierarchy ?: 0.45
            val lowered = tags.map { it.lowercase() }.toSet()

            val acts = base.allowedActs.toMutableList()
            if (piety >= 0.72 || hasAny(lowered, "puritan", "cloister", "policy:ritual_culture")) {
                acts.removeAll(listOf(AdultActionType.BUKKAKE, AdultActionType.BDSM, AdultActionType.FUTANARI_ORGASM))
            }
            if (openness < 0.28 && privacy >= 0.62) {
                acts.remove(AdultActionType.BUKKAKE)
            }
            if (openness >= 0.72 || hasAny(lowered, "publicness:communal", "body-norm:painted-nude", "policy:seasonal_fairs")) {
                if (AdultActionType.VAGINAL !in acts) acts.add(0, AdultActionType.VAGINAL)
            }
            if (role == PersonRole.CLERGY && piety >= 0.55) {
                acts.removeAll(listOf(AdultActionType.BUKKAKE, AdultActionType.BDSM))
            }
            if (role == PersonRole.GENERAL || hasAny(lowered, "war", "martial", "policy:warrior_elite", "at_war")) {
                if (AdultActionType.BDSM !in acts) acts += AdultActionType.BDSM
            }
            if (hasAny(lowered, "policy:open_networks", "policy:broadcast_society") && AdultActionType.MASTURBATION !in acts) {
                acts += AdultActionType.MASTURBATION
            }
            if (acts.isEmpty()) acts += AdultActionType.VAGINAL

            val partnership = when {
                pair >= 0.72 && jealousy >= 0.6 -> PartnershipForm.MONOGAMOUS
                era == TechnologyEra.INFORMATION && privacy < 0.4 -> PartnershipForm.NETWORKED
                hierarchy >= 0.7 && era in setOf(TechnologyEra.MEDIEVAL, TechnologyEra.METALLURGIC, TechnologyEra.AGRARIAN) ->
                    PartnershipForm.RANKED_HOUSEHOLD
                era == TechnologyEra.TRIBAL -> PartnershipForm.CAMP_SHARE
                pair < 0.38 -> PartnershipForm.SERIAL
                else -> base.partnership
            }
            val publicness = when {
                privacy <= 0.22 || openness >= 0.78 -> when (era) {
                    TechnologyEra.TRIBAL -> BodyPublicness.SHARED_CIRCLE
                    TechnologyEra.SPACEFARING -> BodyPublicness.MODULE
                    TechnologyEra.INFORMATION -> BodyPublicness.MEDIATED
                    else -> BodyPublicness.YARD
                }
                privacy >= 0.72 -> when (era) {
                    TechnologyEra.SPACEFARING -> BodyPublicness.MODULE
                    else -> BodyPublicness.PRIVATE_ROOM
                }
                hierarchy >= 0.7 -> BodyPublicness.RANK_GATED
                else -> base.bodyPublicness
            }
            val affair = ((1.0 - pair) * 0.55 + (1.0 - jealousy) * 0.35 + openness * 0.15).coerceIn(0.04, 0.72)
            val scandal = (piety * 0.45 + jealousy * 0.35 + privacy * 0.2).coerceIn(0.08, 0.95)
            return AdultIntimateNorms(
                era = era,
                partnership = partnership,
                bodyPublicness = publicness,
                setting = settingLine(era, publicness, role),
                mood = moodLine(era, role, openness, piety),
                tabooSummary = tabooLine(era, piety, openness, lowered),
                allowedActs = acts.distinct(),
                preferredBond = if (pair >= 0.6) RelationshipKind.PARTNER else RelationshipKind.LOVER,
                affairChance = affair,
                fertilityEmphasis = fertility,
                scandalSensitivity = scandal,
                courtship = courtshipLine(era, pair, hierarchy),
                eliteVsCommon = eliteLine(era, hierarchy, role),
                clothing = clothingLine(era, publicness, openness),
                conflict = conflictLine(era, piety, jealousy, lowered),
                visualSignature = visualLine(era, publicness),
            )
        }

        fun defaultAct(norms: AdultIntimateNorms, personId: String, tick: Long, sequence: Int): AdultActionType {
            val pool = norms.allowedActs.ifEmpty { listOf(AdultActionType.VAGINAL) }
            val weighted = pool.flatMap { type -> List(weight(type, norms)) { type } }
            val index = (stableHash("$personId:$tick:$sequence:norm-act") % weighted.size.toLong()).toInt()
            return weighted[index]
        }

        private fun weight(type: AdultActionType, norms: AdultIntimateNorms): Int {
            val fertility = if (type == AdultActionType.VAGINAL) (1 + (norms.fertilityEmphasis * 3).toInt()) else 1
            val public = if (type == AdultActionType.BUKKAKE && norms.bodyPublicness == BodyPublicness.PRIVATE_ROOM) 0 else 1
            val modest = if (type == AdultActionType.MASTURBATION && norms.partnership == PartnershipForm.MONOGAMOUS) 1 else 2
            return (fertility * public * modest).coerceAtLeast(0).coerceAtLeast(if (public == 0) 0 else 1)
        }

        private fun eraBase(era: TechnologyEra?): AdultIntimateNorms {
            val acts = when (era) {
                TechnologyEra.TRIBAL -> listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.MASTURBATION)
                TechnologyEra.AGRARIAN -> listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.MASTURBATION)
                TechnologyEra.URBAN -> listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.ANAL, AdultActionType.MASTURBATION)
                TechnologyEra.METALLURGIC -> listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.BDSM, AdultActionType.ANAL)
                TechnologyEra.MEDIEVAL -> listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.MASTURBATION)
                TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL ->
                    listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.ANAL, AdultActionType.MASTURBATION)
                TechnologyEra.ELECTRIC -> listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.ANAL, AdultActionType.BDSM)
                TechnologyEra.INFORMATION ->
                    listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.ANAL, AdultActionType.BDSM, AdultActionType.MASTURBATION)
                TechnologyEra.SPACEFARING ->
                    listOf(AdultActionType.VAGINAL, AdultActionType.ORAL, AdultActionType.MASTURBATION, AdultActionType.ANAL)
                null -> AdultActionType.entries.toList()
            }
            val partnership = when (era) {
                TechnologyEra.TRIBAL -> PartnershipForm.CAMP_SHARE
                TechnologyEra.AGRARIAN -> PartnershipForm.HOUSEHOLD
                TechnologyEra.URBAN -> PartnershipForm.SERIAL
                TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> PartnershipForm.RANKED_HOUSEHOLD
                TechnologyEra.INFORMATION -> PartnershipForm.NETWORKED
                else -> PartnershipForm.MONOGAMOUS
            }
            val publicness = when (era) {
                TechnologyEra.TRIBAL -> BodyPublicness.SHARED_CIRCLE
                TechnologyEra.AGRARIAN -> BodyPublicness.YARD
                TechnologyEra.URBAN -> BodyPublicness.YARD
                TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> BodyPublicness.RANK_GATED
                TechnologyEra.INFORMATION -> BodyPublicness.MEDIATED
                TechnologyEra.SPACEFARING -> BodyPublicness.MODULE
                else -> BodyPublicness.PRIVATE_ROOM
            }
            return AdultIntimateNorms(
                era = era,
                partnership = partnership,
                bodyPublicness = publicness,
                setting = settingLine(era, publicness, null),
                mood = moodLine(era, null, 0.5, 0.5),
                tabooSummary = tabooLine(era, 0.5, 0.5, emptySet()),
                allowedActs = acts,
                preferredBond = RelationshipKind.PARTNER,
                affairChance = 0.2,
                fertilityEmphasis = 0.5,
                scandalSensitivity = 0.4,
                courtship = courtshipLine(era, 0.55, 0.45),
                eliteVsCommon = eliteLine(era, 0.45, null),
                clothing = clothingLine(era, publicness, 0.5),
                conflict = conflictLine(era, 0.5, 0.45, emptySet()),
                visualSignature = visualLine(era, publicness),
            )
        }

        private fun settingLine(era: TechnologyEra?, publicness: BodyPublicness, role: PersonRole?): String {
            val rank = if (role == PersonRole.RULER || role == PersonRole.HEIR) "elite chamber of this age, " else ""
            return rank + when (era) {
                TechnologyEra.TRIBAL -> when (publicness) {
                    BodyPublicness.SHARED_CIRCLE -> "hide tent and hearth circle, camp-mates may remain nearby"
                    else -> "inner hide sleeping shelter, packed earth, stacked furs"
                }
                TechnologyEra.AGRARIAN -> "household bed off the yard, grain baskets and field tools in reach"
                TechnologyEra.URBAN -> "craft-quarter room above the street, clay lamps, market cloth"
                TechnologyEra.METALLURGIC -> "workshop dwelling beside the forge, soot and rank metal"
                TechnologyEra.MEDIEVAL -> "timber solar or rope-bed chamber, estate rank at the door"
                TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> "rented tenement room, brick, after-shift quiet"
                TechnologyEra.ELECTRIC -> "wired apartment, ceiling bulb, radio in the corner"
                TechnologyEra.INFORMATION -> "ordinary current apartment, screens face-down, city window"
                TechnologyEra.SPACEFARING -> "sealed crew cabin, padded bulkheads, oval viewport"
                null -> "era-correct private shelter"
            }
        }

        private fun moodLine(era: TechnologyEra?, role: PersonRole?, openness: Double, piety: Double): String {
            val roleMood = when (role) {
                PersonRole.RULER -> "heir-making or court intimacy"
                PersonRole.GENERAL -> "camp or kit-side urgency"
                PersonRole.CLERGY -> if (piety >= 0.6) "rite-framed closeness" else "guarded private closeness"
                PersonRole.MERCHANT -> "bought time and travel closeness"
                else -> null
            }
            val eraMood = when {
                openness >= 0.7 -> "unashamed bodies"
                piety >= 0.7 -> "covered, careful, high-stakes if seen"
                era == TechnologyEra.TRIBAL -> "survival-close, shared heat"
                era == TechnologyEra.INDUSTRIAL || era == TechnologyEra.EARLY_INDUSTRIAL -> "tired, class-marked, overheard"
                era == TechnologyEra.SPACEFARING -> "isolated, recycled air, thin walls"
                else -> "ordinary adult closeness of this age"
            }
            return listOfNotNull(roleMood, eraMood).joinToString(", ")
        }

        private fun tabooLine(era: TechnologyEra?, piety: Double, openness: Double, tags: Set<String>): String = when {
            piety >= 0.75 && openness < 0.4 -> "public display and rank-mixing sex are punished"
            hasAny(tags, "policy:ritual_culture", "puritan") -> "rite and kinship decide who may be seen unclothed"
            era == TechnologyEra.MEDIEVAL -> "estate rank decides who may watch or speak of it"
            era == TechnologyEra.TRIBAL -> "the camp treats bodies as ordinary, not hidden"
            era == TechnologyEra.INFORMATION -> "the feed can turn a private act into a civic fact"
            else -> "norms follow this society's piety and privacy"
        }

        private fun courtshipLine(era: TechnologyEra?, pair: Double, hierarchy: Double): String = when {
            hierarchy >= 0.7 && era in setOf(TechnologyEra.MEDIEVAL, TechnologyEra.METALLURGIC) ->
                "match arranged through house and dowry before the bed is used"
            pair >= 0.7 -> "long pairing, gifts and shared work before sex"
            era == TechnologyEra.TRIBAL -> "shared heat and visible pairing inside the camp"
            era == TechnologyEra.INFORMATION -> "messages and short meetings before the apartment"
            else -> "ordinary courtship of this age before the act"
        }

        private fun eliteLine(era: TechnologyEra?, hierarchy: Double, role: PersonRole?): String = when {
            role == PersonRole.RULER || role == PersonRole.HEIR ->
                "dynastic bed is a political room, not only a private one"
            hierarchy >= 0.7 -> "rank keeps a closed room; common people use yard, loft or rented bed"
            era == TechnologyEra.TRIBAL -> "almost no elite privacy: the fire is shared"
            else -> "rank shades the room more than the act"
        }

        private fun clothingLine(era: TechnologyEra?, publicness: BodyPublicness, openness: Double): String = when {
            publicness == BodyPublicness.SHARED_CIRCLE || openness >= 0.78 -> when (era) {
                TechnologyEra.TRIBAL -> "ochre, hide straps, bodies otherwise bare"
                TechnologyEra.AGRARIAN -> "work cloth dropped at the threshold"
                else -> "clothing left in a heap, skin as the public fact"
            }
            era == TechnologyEra.MEDIEVAL -> "linen and wool peeled to the rope bed"
            era == TechnologyEra.METALLURGIC -> "soot-stained work cloth, apron off beside the forge"
            era == TechnologyEra.INDUSTRIAL || era == TechnologyEra.EARLY_INDUSTRIAL -> "factory shift clothes on a chair"
            era == TechnologyEra.SPACEFARING -> "crew undersuit peeled to webbing"
            else -> "era-correct underclothes discarded before contact"
        }

        private fun conflictLine(era: TechnologyEra?, piety: Double, jealousy: Double, tags: Set<String>): String = when {
            hasAny(tags, "at_war", "war", "martial") -> "homecoming urgency, kit still in reach"
            piety >= 0.7 && jealousy >= 0.6 -> "discovery would cost name and standing"
            era == TechnologyEra.INFORMATION -> "a recording or a neighbor can make this civic"
            else -> "ordinary risk of being overheard"
        }

        private fun visualLine(era: TechnologyEra?, publicness: BodyPublicness): String = when (era) {
            TechnologyEra.TRIBAL -> "smoke hole, ochre traces, stacked hides, bone charms"
            TechnologyEra.AGRARIAN -> "thatch beams, clay jars, straw pallet, yard light"
            TechnologyEra.URBAN -> "mudbrick slit window, clay lamp, market cloth"
            TechnologyEra.METALLURGIC -> "furnace glow, hammered plates, soot on timber"
            TechnologyEra.MEDIEVAL -> "rope bed, iron candleholder, heavy shutter"
            TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> "iron bedframe, brick, oil lamp or gas pipe"
            TechnologyEra.ELECTRIC -> "wired bulb, bakelite radio, wallpaper"
            TechnologyEra.INFORMATION -> "current apartment fixtures, dark screens, city glass"
            TechnologyEra.SPACEFARING -> "viewport, padded hatch, cabin webbing"
            null -> "era-correct props in the same room as the bodies"
        } + if (publicness == BodyPublicness.SHARED_CIRCLE) ", other adults remain in the same circle" else ""

        private fun hasAny(tags: Set<String>, vararg keys: String): Boolean =
            keys.any { key -> tags.any { it.contains(key) } }

        private fun stableHash(key: String): Long {
            var hash = -3750763034362895579L
            key.forEach { char ->
                hash = (hash xor char.code.toLong()) * 1099511628211L
            }
            return hash and Long.MAX_VALUE
        }
    }
}
