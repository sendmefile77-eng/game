package com.sendmefile77.chronosphere.history

data class HistoricalEvent(
    val id: String,
    val tick: Long,
    val type: String,
    val actorIds: List<String>,
    val placeId: String? = null,
    val numericFacts: Map<String, Double> = emptyMap(),
    val tags: Set<String> = emptySet(),
)
