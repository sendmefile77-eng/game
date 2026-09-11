package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.evolution.BiologicalRank
import com.sendmefile77.chronosphere.evolution.BodyPlan
import com.sendmefile77.chronosphere.evolution.EvolutionPopulation
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.MorphologyProfile
import com.sendmefile77.chronosphere.evolution.PopulationLineage
import com.sendmefile77.chronosphere.evolution.Posture
import com.sendmefile77.chronosphere.evolution.SkinCovering

object EvolutionSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_EVOLUTION_V1"

    fun encode(state: EvolutionState): String = buildString {
        appendLine(HEADER)
        appendLine("WORLD\t${state.worldSeed}\t${state.tick}")
        state.lineages.sortedBy { it.id }.forEach { lineage ->
            val m = lineage.morphology
            val b = lineage.bodyPlan
            appendLine(
                listOf(
                    "LINEAGE",
                    esc(lineage.id), esc(lineage.parentLineageId.orEmpty()), esc(lineage.secondaryParentLineageId.orEmpty()),
                    esc(lineage.label), esc(lineage.originSettlementId.orEmpty()), lineage.formedTick,
                    lineage.rank.name, lineage.generation, lineage.divergenceFromOrigin,
                    listOf(
                        m.heightScale, m.massScale, m.limbScale, m.shoulderHipRatio, m.cranialScale,
                        m.pigmentation, m.hairCoverage, m.eyeSize, m.coldAdaptation, m.heatAdaptation,
                        m.oxygenAdaptation, m.radiationTolerance, m.boneDensity, m.fertilityBaseline,
                        m.longevityScale, m.sexualDimorphism,
                    ).joinToString(","),
                    listOf(
                        b.armPairs, b.legPairs, b.eyeCount, b.digitCount,
                        if (b.hasTail) 1 else 0, b.posture.name, b.covering.name,
                    ).joinToString(","),
                    lineage.tags.sorted().joinToString(",") { esc(it) },
                ).joinToString("\t"),
            )
        }
        state.populations.sortedBy { it.id }.forEach { population ->
            appendLine(
                listOf(
                    "POP",
                    esc(population.id), esc(population.settlementId), esc(population.lineageId),
                    population.population, population.isolation, population.geneFlow, population.mutationPressure,
                    encodeWeights(population.ancestry), encodeWeights(population.culturalIdentity),
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String): EvolutionState {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported evolution save format" }
        val world = lines.getOrNull(1)?.split('\t') ?: error("Missing evolution WORLD row")
        require(world.size >= 3 && world[0] == "WORLD") { "Malformed evolution WORLD row" }

        val lineages = lines.drop(2).filter { it.startsWith("LINEAGE\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 13) { "Malformed LINEAGE row" }
            val m = p[10].split(',').map { it.toDouble() }
            require(m.size == 16) { "Malformed morphology payload" }
            val b = p[11].split(',')
            require(b.size == 7) { "Malformed body-plan payload" }
            PopulationLineage(
                id = unesc(p[1]),
                parentLineageId = unesc(p[2]).ifBlank { null },
                secondaryParentLineageId = unesc(p[3]).ifBlank { null },
                label = unesc(p[4]),
                originSettlementId = unesc(p[5]).ifBlank { null },
                formedTick = p[6].toLong(),
                rank = BiologicalRank.valueOf(p[7]),
                generation = p[8].toInt(),
                divergenceFromOrigin = p[9].toDouble(),
                morphology = MorphologyProfile(
                    heightScale = m[0], massScale = m[1], limbScale = m[2], shoulderHipRatio = m[3],
                    cranialScale = m[4], pigmentation = m[5], hairCoverage = m[6], eyeSize = m[7],
                    coldAdaptation = m[8], heatAdaptation = m[9], oxygenAdaptation = m[10],
                    radiationTolerance = m[11], boneDensity = m[12], fertilityBaseline = m[13],
                    longevityScale = m[14], sexualDimorphism = m[15],
                ),
                bodyPlan = BodyPlan(
                    armPairs = b[0].toInt(), legPairs = b[1].toInt(), eyeCount = b[2].toInt(), digitCount = b[3].toInt(),
                    hasTail = b[4] == "1", posture = Posture.valueOf(b[5]), covering = SkinCovering.valueOf(b[6]),
                ),
                tags = p[12].takeIf { it.isNotBlank() }?.split(',')?.map { unesc(it) }?.toSet().orEmpty(),
            )
        }
        val populations = lines.drop(2).filter { it.startsWith("POP\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 9) { "Malformed POP row" }
            val lineageId = unesc(p[3])
            EvolutionPopulation(
                id = unesc(p[1]), settlementId = unesc(p[2]), lineageId = lineageId,
                population = p[4].toLong(), isolation = p[5].toDouble(), geneFlow = p[6].toDouble(),
                mutationPressure = p[7].toDouble(), ancestry = decodeWeights(p[8], lineageId),
                culturalIdentity = p.getOrNull(9)?.takeIf { it.isNotBlank() }?.let(::decodeWeightsOptional).orEmpty(),
            )
        }
        return EvolutionState(
            worldSeed = world[1].toLong(), tick = world[2].toLong(), lineages = lineages, populations = populations,
        )
    }

    private fun encodeWeights(values: Map<String, Double>): String =
        values.entries.sortedBy { it.key }.joinToString(",") { "${esc(it.key)}:${it.value}" }

    private fun decodeWeights(value: String, fallbackLineageId: String): Map<String, Double> =
        if (value.isBlank()) mapOf(fallbackLineageId to 1.0) else decodeWeightsOptional(value)

    private fun decodeWeightsOptional(value: String): Map<String, Double> = value.split(',').associate { item ->
        val separator = item.lastIndexOf(':')
        require(separator > 0) { "Malformed weights payload" }
        unesc(item.substring(0, separator)) to item.substring(separator + 1).toDouble()
    }

    private fun esc(value: String): String = value.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace(",", "%2C").replace(":", "%3A")
    private fun unesc(value: String): String = value.replace("%3A", ":").replace("%2C", ",").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
