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
            "sex taking place inside a prehistoric hide tent or reed hut, packed earth floor, stacked furs as bedding, " +
                "open hearth fire and woodsmoke, wooden posts wrapped in sinew, ochre-stained hides, bone charms, " +
                "no tiled bathroom, no modern bedroom, no drywall, no porcelain"
        TechnologyEra.AGRARIAN ->
            "sex in a clay-and-thatch cottage, straw pallet on a dirt-packed floor, oil lamp, hanging onions and grain baskets, " +
                "woven mats, cracked earthen walls, wooden rake and hoe in the corner"
        TechnologyEra.URBAN ->
            "sex in a cramped pre-industrial town room of mudbrick and timber, clay lamps, market awnings visible through a small window"
        TechnologyEra.METALLURGIC ->
            "sex in a dwelling beside a bronze-working shop, hammered metal bowls, furnace glow through the doorway, soot on timber"
        TechnologyEra.MEDIEVAL ->
            "sex in a medieval timber-framed chamber, rope bed with wool blankets, rush-covered floor, heavy wooden shutters, candle stubs"
        TechnologyEra.EARLY_INDUSTRIAL ->
            "sex in a soot-stained brick room, iron bedframe, oil lamp, factory chimneys outside a grimy window"
        TechnologyEra.INDUSTRIAL ->
            "sex in an industrial tenement, iron bed, peeling wallpaper, gaslight, railway smoke outside"
        TechnologyEra.ELECTRIC ->
            "sex in an early-modern wired apartment, bakelite lamp, radio cabinet, wallpaper, no smartphone"
        TechnologyEra.INFORMATION ->
            "sex in a contemporary apartment, current furniture, window city glow, screens off in the background"
        TechnologyEra.SPACEFARING ->
            "sex in a spacecraft cabin, padded bulkheads, oval viewport, soft instrument lighting, composite panels"
        null -> "sex in an interior that matches the civilization's current technological era"
    }

    fun distinctiveMarker(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL -> "unique tribal-era markers: ochre body paint traces, smoke hole overhead, mammoth or deer hide bedding"
        TechnologyEra.AGRARIAN -> "unique agrarian markers: thatch roof beams, clay jars, straw bedding"
        TechnologyEra.URBAN -> "unique early-city markers: mudbrick walls, narrow window slit, woven market cloth"
        TechnologyEra.METALLURGIC -> "unique metal-age markers: bronze tools on a shelf, furnace light, hammered plates"
        TechnologyEra.MEDIEVAL -> "unique medieval markers: iron candleholder, wooden shutter, wool blanket on a rope bed"
        TechnologyEra.EARLY_INDUSTRIAL -> "unique early-industrial markers: iron bedframe, coal dust, oil lamp glass"
        TechnologyEra.INDUSTRIAL -> "unique industrial markers: brick tenement, gas pipe, factory window"
        TechnologyEra.ELECTRIC -> "unique electric-era markers: wired ceiling bulb, bakelite radio, wallpapered room"
        TechnologyEra.INFORMATION -> "unique information-age markers: modern drywall apartment, glass window, current fixtures"
        TechnologyEra.SPACEFARING -> "unique spacefaring markers: viewport stars, padded hatch, cabin webbing"
        null -> "era-specific props must be visible in the background"
    }

    fun portraitInterior(era: TechnologyEra?): String = when (era) {
        TechnologyEra.TRIBAL -> "standing in a tribal camp of hide tents and hearths, wild landscape and smoke behind the figure"
        TechnologyEra.AGRARIAN -> "standing in a village of timber and thatch, grain fields behind the figure"
        TechnologyEra.URBAN -> "standing in a pre-industrial town street of masonry, timber and market stalls"
        TechnologyEra.METALLURGIC -> "standing near furnaces and bronze workshops of a pre-modern settlement"
        TechnologyEra.MEDIEVAL -> "standing in a medieval courtyard of stone, timber and candle niches"
        TechnologyEra.EARLY_INDUSTRIAL -> "standing by brick workshops, chimneys and steam-era machinery"
        TechnologyEra.INDUSTRIAL -> "standing in an industrial city of factories, steel and rail"
        TechnologyEra.ELECTRIC -> "standing in an electrified early-modern street with wired lamps"
        TechnologyEra.INFORMATION -> "standing in a contemporary city street"
        TechnologyEra.SPACEFARING -> "standing in a spacefaring habitat corridor with viewports"
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
                "car", "computer", "skyscraper", "hide tent camp",
            )
            TechnologyEra.URBAN, TechnologyEra.METALLURGIC, TechnologyEra.MEDIEVAL -> listOf(
                "electric light", "modern furniture", "car", "computer", "smartphone",
                "skyscraper", "plastic objects", "prehistoric cave",
            )
            TechnologyEra.EARLY_INDUSTRIAL, TechnologyEra.INDUSTRIAL -> listOf(
                "smartphone", "flat screen", "laptop", "modern skyscraper", "spacecraft", "thatched hut",
            )
            TechnologyEra.ELECTRIC -> listOf("smartphone", "laptop", "spacecraft", "hologram", "LED strip", "stone-age hut")
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
