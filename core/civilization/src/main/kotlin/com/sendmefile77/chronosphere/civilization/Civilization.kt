package com.sendmefile77.chronosphere.civilization

data class Civilization(
    val id: String,
    val name: String,
    val population: Long,
    val stability: Double,
    val technology: Double,
    val treasury: Double,
    val cultureTags: Set<String> = emptySet(),
)
