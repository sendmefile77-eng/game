package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext

/**
 * Read-only adult view of historical memory.
 *
 * Core types are not imported. ChatGPT/app should pass history through the existing
 * [AdultWorldContext] culture tags and numeric map using the prefixes below.
 * Missing historical memory yields [present] = false and does not break G-001..G-007 paths.
 *
 * Tag prefixes consumed (unknown suffixes are recorded, never crash):
 * - history_policy:*
 * - history_process:{war,migration,shortage,settlement_expansion,diplomatic_alignment,
 *   technological_transition,dynastic_transition,population_divergence}
 * - history_process_stage:{emerging,active,consolidating,strained,resolved}
 * - foundation:{settlement,production,exchange,authority}
 * - history_commitment:*
 * - history_consequence:open
 * - civ:{id}
 * - person_role:{ruler,merchant,commander,scholar,cleric,artisan,courtesan}
 * - person_status:*
 * - history_branch:{id}
 */
internal data class AdultHistoricalContext(
    val present: Boolean,
    val civilizationId: String? = null,
    val branchId: String? = null,
    val tick: Long = 0L,
    val era: String? = null,
    val foundations: Set<String> = emptySet(),
    val activeProcesses: Set<String> = emptySet(),
    val processStages: Set<String> = emptySet(),
    val policies: Set<String> = emptySet(),
    val commitments: Set<String> = emptySet(),
    val openConsequences: Boolean = false,
    val personRole: String? = null,
    val personStatus: String? = null,
    val warActive: Boolean = false,
    val migrationActive: Boolean = false,
    val shortageActive: Boolean = false,
    val dynasticActive: Boolean = false,
    val techTransitionActive: Boolean = false,
    val urbanization: Double? = null,
    val wealth: Double? = null,
    val scarcity: Double? = null,
    val tradeOpenness: Double? = null,
    val warPressure: Double? = null,
    val piety: Double? = null,
    val privacy: Double? = null,
    val bodyOpenness: Double? = null,
    val technology: Double? = null,
) {
    companion object {
        const val POLICY_PREFIX = "history_policy:"
        const val PROCESS_PREFIX = "history_process:"
        const val STAGE_PREFIX = "history_process_stage:"
        const val FOUNDATION_PREFIX = "foundation:"
        const val COMMITMENT_PREFIX = "history_commitment:"
        const val CIV_PREFIX = "civ:"
        const val ROLE_PREFIX = "person_role:"
        const val STATUS_PREFIX = "person_status:"
        const val BRANCH_PREFIX = "history_branch:"
        const val CONSEQUENCE_OPEN = "history_consequence:open"

        fun from(request: AdultEventRequest): AdultHistoricalContext = from(request.context)

        fun from(context: AdultWorldContext): AdultHistoricalContext {
            val tags = AdultCulture.normalizedTags(context.cultureTags)
            val policies = suffixes(tags, POLICY_PREFIX)
            val processes = suffixes(tags, PROCESS_PREFIX).map { normalizeProcess(it) }.toSet()
            val stages = suffixes(tags, STAGE_PREFIX)
            val foundations = suffixes(tags, FOUNDATION_PREFIX)
            val commitments = suffixes(tags, COMMITMENT_PREFIX)
            val era = AdultContextSignals.eraTags(tags).sorted().firstOrNull()
            val civ = suffixes(tags, CIV_PREFIX).sorted().firstOrNull()
            val role = suffixes(tags, ROLE_PREFIX).sorted().firstOrNull()
            val status = suffixes(tags, STATUS_PREFIX).sorted().firstOrNull()
            val branch = suffixes(tags, BRANCH_PREFIX).sorted().firstOrNull()
            val present = policies.isNotEmpty() || processes.isNotEmpty() || foundations.isNotEmpty() ||
                commitments.isNotEmpty() || civ != null || branch != null ||
                CONSEQUENCE_OPEN in tags || tags.any { it.startsWith("history_") }
            return AdultHistoricalContext(
                present = present,
                civilizationId = civ,
                branchId = branch,
                tick = context.tick,
                era = era,
                foundations = foundations,
                activeProcesses = processes,
                processStages = stages,
                policies = policies,
                commitments = commitments,
                openConsequences = CONSEQUENCE_OPEN in tags,
                personRole = role,
                personStatus = status,
                warActive = "war" in processes ||
                    (AdultContextSignals.finite(context.numericContext, SocialContextKeys.WAR_PRESSURE) ?: 0.0) >= 0.55,
                migrationActive = "migration" in processes,
                shortageActive = "shortage" in processes,
                dynasticActive = "dynastic_transition" in processes,
                techTransitionActive = "technological_transition" in processes,
                urbanization = AdultContextSignals.finite(context.numericContext, SocialContextKeys.URBANIZATION),
                wealth = AdultContextSignals.finite(context.numericContext, SocialContextKeys.WEALTH),
                scarcity = AdultContextSignals.finite(context.numericContext, SocialContextKeys.SCARCITY),
                tradeOpenness = AdultContextSignals.finite(context.numericContext, SocialContextKeys.TRADE_OPENNESS),
                warPressure = AdultContextSignals.finite(context.numericContext, SocialContextKeys.WAR_PRESSURE),
                piety = AdultContextSignals.finite(context.numericContext, SocialContextKeys.PIETY),
                privacy = AdultContextSignals.finite(context.numericContext, SocialContextKeys.PRIVACY),
                bodyOpenness = AdultContextSignals.finite(context.numericContext, SocialContextKeys.BODY_OPENNESS),
                technology = AdultContextSignals.finite(context.numericContext, SocialContextKeys.TECHNOLOGY),
            )
        }

        private fun suffixes(tags: Set<String>, prefix: String): Set<String> =
            tags.filter { it.startsWith(prefix) && it.length > prefix.length }
                .map { it.removePrefix(prefix) }
                .filter { it.isNotBlank() }
                .toSet()

        private fun normalizeProcess(raw: String): String = when (raw) {
            "settlement_expansion", "expansion" -> "settlement_expansion"
            "diplomatic_alignment", "diplomacy" -> "diplomatic_alignment"
            "technological_transition", "tech" -> "technological_transition"
            "dynastic_transition", "dynasty" -> "dynastic_transition"
            "population_divergence", "divergence" -> "population_divergence"
            else -> raw
        }
    }
}
