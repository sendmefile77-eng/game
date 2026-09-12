package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra

/** Shared era language so portraits, sex scenes and chronicle frames describe the same world. */
internal object HordeEraVisual {
    fun materialCulture(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL ->
            "prehistoric tribal society, stone-age material culture, hide, woven fiber, wood, bone and stone, " +
                "simple huts or hide tents, hearth fire, packed earth, raw natural landscape, no masonry palace"
        TechnologyEra.AGRARIAN ->
            "early agrarian village, timber, clay, thatch and simple masonry, hand tools, farms and animal pens, no industry"
        TechnologyEra.URBAN ->
            "early urban civilization, dense low-rise masonry and timber houses, markets and workshops, pre-industrial streets"
        TechnologyEra.METALLURGIC ->
            "early metalworking civilization, bronze or iron tools, furnaces and workshops, pre-modern architecture"
        TechnologyEra.MEDIEVAL ->
            "medieval material culture, hand-built stone and timber architecture, candles and hearths, no electricity"
        TechnologyEra.EARLY_INDUSTRIAL ->
            "early industrial material culture, brick workshops, steam machinery, soot, nineteenth-century technology"
        TechnologyEra.INDUSTRIAL ->
            "industrial-era material culture, factories, steel, rail and mass-produced objects, no digital devices"
        TechnologyEra.ELECTRIC ->
            "electrified early modern society, electric lighting, wired infrastructure, engines, no contemporary smartphones"
        TechnologyEra.INFORMATION ->
            "information-age society, contemporary architecture, computers, digital devices and modern infrastructure"
        TechnologyEra.SPACEFARING ->
            "advanced spacefaring civilization, mature aerospace habitat, believable high technology, off-world materials"
        null -> "historically coherent material culture with no unexplained anachronisms"
    }

    fun intimateInterior(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL ->
            "sex taking place inside a prehistoric hide tent or reed hut, packed earth floor, furs and hides as bedding, " +
                "central hearth fire lighting the bodies, wooden posts, bone and stone objects nearby, smoky air, " +
                "no tiled bathroom, no modern bedroom, no drywall, no porcelain"
        TechnologyEra.AGRARIAN ->
            "sex in a clay-and-thatch cottage, straw pallet or rough wooden platform, oil lamp or hearth light, " +
                "woven mats, earthen walls, farm tools stored against the wall"
        TechnologyEra.URBAN ->
            "sex in a pre-industrial town room of mudbrick or timber, clay lamps, woven bedding, market street audible outside"
        TechnologyEra.METALLURGIC ->
            "sex in a pre-modern dwelling beside a metal workshop, bronze or iron fittings, simple wooden bed platform, furnace glow"
        TechnologyEra.MEDIEVAL ->
            "sex in a medieval timber-framed chamber, rope bed with wool blankets, plaster walls, candlelight, no electric bulbs"
        TechnologyEra.EARLY_INDUSTRIAL ->
            "sex in a brick nineteenth-century room, iron bedframe, oil lamp or gaslight, soot-stained walls"
        TechnologyEra.INDUSTRIAL ->
            "sex in an industrial-era tenement room, iron bed, factory town outside the window, gas or early electric light"
        TechnologyEra.ELECTRIC ->
            "sex in an early-modern electrified apartment, wired lamp, mid-century furniture, no smartphone, no LED strips"
        TechnologyEra.INFORMATION ->
            "sex in a contemporary apartment that matches the information age, current furniture and lighting"
        TechnologyEra.SPACEFARING ->
            "sex in a clean spacecraft or habitat cabin, composite panels, soft instrument lighting, not a medieval room"
        null -> "sex in an interior that matches the civilization's current technological era"
    }

    fun portraitInterior(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL -> "standing in a tribal camp of hide tents and hearths, wild landscape behind the figure"
        TechnologyEra.AGRARIAN -> "standing in a village of timber and thatch, fields visible behind the figure"
        TechnologyEra.URBAN -> "standing in a pre-industrial town street of masonry and timber"
        TechnologyEra.METALLURGIC -> "standing near furnaces and metal workshops of a pre-modern settlement"
        TechnologyEra.MEDIEVAL -> "standing in a medieval courtyard of stone and timber"
        TechnologyEra.EARLY_INDUSTRIAL -> "standing by brick workshops and steam-era machinery"
        TechnologyEra.INDUSTRIAL -> "standing in an industrial city of factories and steel"
        TechnologyEra.ELECTRIC -> "standing in an electrified early-modern street"
        TechnologyEra.INFORMATION -> "standing in a contemporary city environment"
        TechnologyEra.SPACEFARING -> "standing in a spacefaring habitat or spaceport interior"
        null -> "environment consistent with the stated technological era"
    }

    fun negatives(era: TechnologyEra?): List<String> {
        val sharedModernLeak = listOf(
            "modern tiled bathroom",
            "ceramic tiles",
            "porcelain toilet",
            "glass shower",
            "IKEA furniture",
            "LED strip lighting",
            "neon cyberpunk",
            "anime bedroom",
            "modern hotel room",
        )
        val eraSpecific = when (era) {
            TechnologyEra.TRIBAL -> listOf(
                "palace", "chandelier", "luxury mansion", "ornate ballroom", "modern furniture",
                "glass skyscraper", "electric light", "car", "gun", "computer", "metal armor",
                "tailored suit", "brick house", "glass windows", "drywall", "linoleum",
            )
            TechnologyEra.AGRARIAN -> listOf(
                "chandelier", "luxury palace interior", "industrial machinery", "electric light",
                "car", "computer", "skyscraper",
            )
            TechnologyEra.URBAN, TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> listOf(
                "electric light", "modern furniture", "car", "computer", "smartphone",
                "skyscraper", "plastic objects",
            )
            TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> listOf(
                "smartphone", "flat screen", "laptop", "modern skyscraper", "spacecraft",
            )
            TechnologyEra.ELECTRIC -> listOf("smartphone", "laptop", "spacecraft", "hologram", "LED strip")
            TechnologyEra.INFORMATION -> listOf("spacecraft interior", "fantasy medieval costume", "stone-age hut")
            TechnologyEra.SPACEFARING -> listOf("medieval fantasy castle", "thatched hut", "dirt floor camp")
            null -> emptyList()
        }
        return if (era == TechnologyEra.INFORMATION || era == TechnologyEra.SPACEFARING) {
            eraSpecific
        } else {
            sharedModernLeak + eraSpecific
        }
    }
}
