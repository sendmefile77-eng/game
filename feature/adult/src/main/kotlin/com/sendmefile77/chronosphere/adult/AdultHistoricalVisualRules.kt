package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest

internal data class AdultVisualSignature(
    val cloth: String,
    val jewelry: String,
    val hair: String,
    val bodyNorm: String,
    val architecture: String,
    val settingBias: String,
    val cosmetics: String,
    val publicness: String,
)

internal object AdultHistoricalVisualRules {
    fun signature(request: AdultEventRequest): AdultVisualSignature {
        val ctx = AdultHistoricalContext.from(request)
        val era = ctx.era ?: AdultContextSignals.eraTags(request.context.cultureTags).sorted().firstOrNull()
        val base = eraBase(era)
        return evolve(base, ctx)
    }

    fun mediaTags(request: AdultEventRequest): Set<String> {
        val ctx = AdultHistoricalContext.from(request)
        val sign = signature(request)
        return buildSet {
            ctx.era?.let { add("era:$it") }
            ctx.civilizationId?.let { add("civ:$it") }
            ctx.branchId?.let { add("branch:$it") }
            ctx.personRole?.let { add("role:$it") }
            ctx.personStatus?.let { add("status:$it") }
            ctx.activeProcesses.sorted().take(4).forEach { add("hist:$it") }
            ctx.foundations.sorted().forEach { add("foundation:$it") }
            ctx.policies.sorted().take(3).forEach { add("policy:$it") }
            add("cloth:${sign.cloth}")
            add("jewel:${sign.jewelry}")
            add("hair:${sign.hair}")
            add("body-norm:${sign.bodyNorm}")
            add("arch:${sign.architecture}")
            add("set-bias:${sign.settingBias}")
            add("cosmetic:${sign.cosmetics}")
            add("publicness:${sign.publicness}")
        }
    }

    private fun eraBase(era: String?): AdultVisualSignature = when (era) {
        EraTags.TRIBAL -> AdultVisualSignature("hide-wrap", "bone-bead", "braid-paint", "painted-skin", "hearth-camp", "firelit-ground", "earth-paint", "communal")
        EraTags.AGRARIAN -> AdultVisualSignature("undyed-linen", "copper-loop", "tied-work", "work-bare-arms", "farmstead", "granary-yard", "oil-plain", "household")
        EraTags.URBAN, EraTags.METALLURGIC -> AdultVisualSignature("dyed-wool", "hammered-copper", "city-plait", "open-shoulder", "workshop-street", "craft-quarter", "kohl-dust", "street-visible")
        EraTags.MEDIEVAL -> AdultVisualSignature("layered-wool", "status-metal", "veiled-or-circlet", "estate-covered", "court-hall", "solar-chamber", "court-tint", "rank-gated")
        EraTags.EARLY_INDUSTRIAL, EraTags.INDUSTRIAL -> AdultVisualSignature("mill-cloth", "factory-chain", "pinned-urban", "corset-street", "brick-tenement", "loft-or-alley", "cheap-rouge", "crowd-close")
        EraTags.ELECTRIC, EraTags.INFORMATION -> AdultVisualSignature("cut-tailored", "heirloom-metal", "styled-mass", "media-body", "lit-apartment", "private-room", "advert-glam", "mediated")
        EraTags.SPACEFARING -> AdultVisualSignature("sealed-duty", "lineage-inlay", "cropped-or-bound", "habitat-bare", "habitat-ring", "cabin-or-garden", "sterile-sheen", "module-private")
        else -> AdultVisualSignature("plain-cloth", "simple-bead", "natural", "modest", "dwelling", "interior", "none", "private")
    }

    private fun evolve(base: AdultVisualSignature, ctx: AdultHistoricalContext): AdultVisualSignature {
        var cloth = base.cloth
        var jewelry = base.jewelry
        var body = base.bodyNorm
        var arch = base.architecture
        var setting = base.settingBias
        var publicness = base.publicness
        var cosmetics = base.cosmetics
        if ("exchange" in ctx.foundations) {
            jewelry = when (jewelry) {
                "bone-bead" -> "copper-loop"
                "copper-loop", "hammered-copper" -> "status-metal"
                else -> jewelry
            }
            publicness = if (publicness == "household") "street-visible" else publicness
        }
        if ("authority" in ctx.foundations) {
            cloth = when (cloth) {
                "hide-wrap", "undyed-linen" -> "status-wrap"
                "dyed-wool" -> "layered-wool"
                else -> cloth
            }
            setting = when (ctx.personRole) {
                "ruler" -> "throne-solar"
                "commander" -> "war-tent-or-keep"
                else -> setting
            }
        }
        if (ctx.warActive) {
            setting = if (ctx.personRole == "commander") "war-tent-or-keep" else "homecoming-yard"
            body = if ((ctx.bodyOpenness ?: 0.4) >= 0.55) "soldier-undress" else body
        }
        if (ctx.migrationActive) {
            jewelry = "$jewelry+foreign-inlay"
            cosmetics = "mixed-cosmetic"
        }
        if (ctx.shortageActive) {
            cloth = "mended-$cloth"
            publicness = "household"
        }
        if ((ctx.wealth ?: 0.0) >= 0.75) {
            jewelry = "elite-$jewelry"
            cosmetics = if (cosmetics == "none") "status-tint" else cosmetics
        }
        if ((ctx.bodyOpenness ?: 0.0) >= 0.7) {
            body = when (ctx.era) {
                EraTags.TRIBAL -> "painted-nude"
                EraTags.AGRARIAN -> "field-undress"
                EraTags.MEDIEVAL -> "court-decollete"
                else -> "open-body"
            }
        }
        if ((ctx.piety ?: 0.0) >= 0.75 && (ctx.bodyOpenness ?: 1.0) < 0.45) {
            body = "ritually-covered"
            publicness = "cloistered"
        }
        if (ctx.techTransitionActive) arch = "$arch+new-material"
        return base.copy(cloth = cloth, jewelry = jewelry, bodyNorm = body, architecture = arch, settingBias = setting, publicness = publicness, cosmetics = cosmetics)
    }
}
