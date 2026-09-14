package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectKind
import com.sendmefile77.chronosphere.adultcontracts.MediaCue
import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.history.HistoricalMemoryEngine
import com.sendmefile77.chronosphere.history.HistoricalMemoryState
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRelationship
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.people.RelationshipKind
import com.sendmefile77.chronosphere.people.SocialProfile
import com.sendmefile77.chronosphere.simulation.DeterministicRng
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.simulation.WorldSeed
import kotlin.math.abs
import kotlin.math.roundToLong

data class SocietyAdvanceResult(
    val world: LivingPlanetState,
    val people: PeopleState,
    val events: List<SimulationEvent>,
    val mediaCues: List<MediaCue>,
)

/**
 * Stable integration boundary between the deterministic core and an optional adult module.
 * The module never receives mutable core objects and can only propose bounded effects.
 */
class SocietyEngine(
    private val adultModule: AdultModule = NoOpAdultModule,
) {
    fun advance(
        fromTick: Long,
        world: LivingPlanetState,
        people: PeopleState,
        economy: EconomyState?,
        historicalMemory: HistoricalMemoryState? = null,
        historyBranchId: String? = null,
    ): SocietyAdvanceResult {
        require(world.worldSeed == people.worldSeed) { "Society world/people seed mismatch" }
        require(economy == null || economy.worldSeed == world.worldSeed) { "Society world/economy seed mismatch" }
        require(historicalMemory == null || historicalMemory.worldSeed == world.worldSeed) { "Society world/history seed mismatch" }
        require(world.tick >= fromTick) { "Cannot move society simulation backward" }
        if (adultModule.contractVersion != ADULT_CONTRACT_VERSION || world.tick <= fromTick) {
            return SocietyAdvanceResult(world, people, emptyList(), emptyList())
        }

        val annualTicks = annualBoundaries(fromTick, world.tick)
        if (annualTicks.isEmpty()) return SocietyAdvanceResult(world, people, emptyList(), emptyList())

        var currentWorld = world
        var currentPeople = people
        var currentHistoricalMemory = HistoricalMemoryEngine.reconcile(
            previous = historicalMemory,
            world = currentWorld,
            people = currentPeople,
            economy = economy,
        )
        val events = mutableListOf<SimulationEvent>()
        val media = mutableListOf<MediaCue>()

        for (annualTick in annualTicks) {
            currentHistoricalMemory = HistoricalMemoryEngine.reconcile(
                previous = currentHistoricalMemory,
                world = currentWorld,
                people = currentPeople,
                economy = economy,
            )
            for (civilization in currentWorld.civilizations.sortedBy { it.id }) {
                val profile = currentPeople.profile(civilization.id) ?: continue
                if (!notableAdultYear(annualTick, profile)) continue
                val participants = selectParticipants(
                    worldSeed = currentWorld.worldSeed,
                    civilizationId = civilization.id,
                    tick = annualTick,
                    people = currentPeople,
                    profile = profile,
                )
                if (participants.isEmpty()) continue

                val request = buildRequest(
                    tick = annualTick,
                    civilizationId = civilization.id,
                    world = currentWorld,
                    economy = economy,
                    profile = profile,
                    participants = participants,
                    historicalMemory = currentHistoricalMemory,
                    historyBranchId = historyBranchId,
                )
                val result = runCatching { adultModule.evaluate(request) }.getOrNull() ?: continue
                if (result.eventCode == "NO_OP" || !validResult(request, result)) continue

                val applied = applyEffects(
                    tick = annualTick,
                    civilizationId = civilization.id,
                    world = currentWorld,
                    people = currentPeople,
                    participants = participants,
                    result = result,
                )
                currentWorld = applied.first
                currentPeople = applied.second
                result.mediaCue?.let(media::add)

                val scandal = scandalBetween(currentPeople, participants)
                val significance = significanceOf(currentPeople, participants, result, scandal)
                events += SimulationEvent(
                    id = "society-${civilization.id}-$annualTick-${stableToken(result.eventCode)}",
                    tick = annualTick,
                    code = "ADULT_SOCIAL_EVENT",
                    actorIds = participants.map { it.id } + civilization.id,
                    locationId = participants.firstOrNull()?.settlementId,
                    numbers = mapOf("effects" to result.effects.size.toDouble()),
                    facts = buildMap {
                        put("civilization", civilization.name)
                        put("eventCode", result.eventCode)
                        put("participants", participants.joinToString(", ") { it.name })
                        significance?.let { put("significance", it) }
                        if (scandal) put("scandal", "affair")
                        result.mediaCue?.let { cue ->
                            put("mediaKey", cue.assetKey)
                            put("mediaTags", cue.tags.sorted().joinToString("|"))
                        }
                    },
                )
            }
            if (events.isNotEmpty()) {
                currentHistoricalMemory = HistoricalMemoryEngine.reconcile(
                    previous = currentHistoricalMemory,
                    world = currentWorld.copy(recentEvents = (currentWorld.recentEvents + events).takeLast(96)),
                    people = currentPeople,
                    economy = economy,
                )
            }
        }

        return SocietyAdvanceResult(
            world = currentWorld.copy(recentEvents = (currentWorld.recentEvents + events).takeLast(96)),
            people = currentPeople.copy(tick = world.tick),
            events = events,
            mediaCues = media,
        )
    }
