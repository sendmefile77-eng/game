package com.sendmefile77.chronosphere.horde

/** Maps historical visual media tags into concise renderer language without changing world facts. */
internal object HordeHistoricalVisualPrompt {
    fun fragment(tags: Set<String>): String {
        fun value(prefix: String): String? = tags.asSequence()
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.removePrefix(prefix) }
            .sorted()
            .firstOrNull()

        val parts = buildList {
            value("cloth:")?.let { add("clothing/material tradition ${humanize(it)}") }
            value("jewel:")?.let { add("jewellery tradition ${humanize(it)}") }
            value("hair:")?.let { add("historical hairstyle ${humanize(it)}") }
            value("body-norm:")?.let { add("body presentation norm ${humanize(it)}") }
            value("arch:")?.let { add("architecture ${humanize(it)}") }
            value("set-bias:")?.let { add("preferred setting ${humanize(it)}") }
            value("cosmetic:")?.let { add("cosmetic tradition ${humanize(it)}") }
            value("publicness:")?.let { add("social visibility ${humanize(it)}") }
            tags.asSequence().filter { it.startsWith("hist:") }.map { it.removePrefix("hist:") }.sorted().take(2)
                .forEach { add("historical pressure ${humanize(it)}") }
            tags.asSequence().filter { it.startsWith("foundation:") }.map { it.removePrefix("foundation:") }.sorted().take(2)
                .forEach { add("civilizational foundation ${humanize(it)}") }
        }
        return parts.joinToString(", ")
    }

    fun signature(tags: Set<String>): String = tags.asSequence()
        .filter { tag -> PREFIXES.any(tag::startsWith) }
        .sorted()
        .joinToString(";")

    private fun humanize(value: String): String = value.replace('_', ' ').replace('-', ' ').replace('+', ' ')

    private val PREFIXES = listOf(
        "cloth:", "jewel:", "hair:", "body-norm:", "arch:", "set-bias:",
        "cosmetic:", "publicness:", "hist:", "foundation:", "policy:", "era:",
    )
}
