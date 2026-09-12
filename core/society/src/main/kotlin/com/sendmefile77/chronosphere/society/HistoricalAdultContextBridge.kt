package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.history.HistoricalConsequenceStatus
import com.sendmefile77.chronosphere.history.HistoricalMemoryState
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole

/**
 * Read-only adapter from core historical memory into the stable adult-world context contract.
 * It exposes history only as normalized tags; feature/adult cannot mutate history through this path.
 */
object HistoricalAdultContextBridge {
    fun tags(
        baseTags: Set<String>,
        civilizationId: String,
        historicalMemory: HistoricalMemoryState?,
        branchId: String? = null,
        primaryPerson: NotablePerson? = null,
    ): Set<String> = buildSet {
        addAll(baseTags.map { it.lowercase() })
        add("civ:${civilizationId.lowercase()}")
        branchId?.takeIf { it.isNotBlank() }?.let { add("history_branch:${it.lowercase()}") }

        historicalMemory?.let { memory ->
            memory.foundationsFor(civilizationId)
                .sortedBy { it.kind.name }
                .forEach { foundation -> add("foundation:${foundation.kind.name.lowercase()}") }

            memory.activeProcessesFor(civilizationId)
                .sortedWith(compareByDescending<com.sendmefile77.chronosphere.history.HistoricalProcess> { it.intensity }.thenBy { it.id })
                .forEach { process ->
                    add("history_process:${process.kind.name.lowercase()}")
                    add("history_process_stage:${process.stage.name.lowercase()}")
                }

            memory.activeCommitmentsFor(civilizationId)
                .sortedWith(compareBy<com.sendmefile77.chronosphere.history.HistoricalCommitment> { it.family }.thenBy { it.choiceId })
                .forEach { commitment ->
                    add("history_commitment:${commitment.choiceId.lowercase()}")
                    add("history_policy:${commitment.choiceId.lowercase()}")
                }

            if (memory.consequences.any {
                    civilizationId in it.civilizationIds && it.status == HistoricalConsequenceStatus.OPEN
                }
            ) add("history_consequence:open")
        }

        primaryPerson?.let { person ->
            add("person_role:${roleTag(person.role)}")
            add("person_status:${statusTag(person)}")
        }
    }.toSortedSet()

    private fun roleTag(role: PersonRole): String = when (role) {
        PersonRole.RULER -> "ruler"
        PersonRole.GENERAL -> "commander"
        PersonRole.MERCHANT -> "merchant"
        PersonRole.SCHOLAR -> "scholar"
        PersonRole.CLERGY -> "cleric"
        PersonRole.HEIR -> "heir"
        PersonRole.DYNAST -> "dynast"
        PersonRole.NOTABLE -> "notable"
    }

    private fun statusTag(person: NotablePerson): String = when {
        person.role == PersonRole.RULER -> "sovereign"
        person.role == PersonRole.HEIR -> "heir"
        person.role == PersonRole.DYNAST -> "dynast"
        person.prestige >= 0.80 -> "elite"
        person.prestige >= 0.55 -> "prominent"
        else -> "common-notable"
    }
}

/**
 * AdultModule decorator used by the society bridge. It enriches the immutable request and then
 * delegates; neither the wrapped module nor this adapter receives mutable history objects.
 */
class HistoricalContextAdultModule(
    private val delegate: AdultModule,
    private val people: PeopleState,
    private val historicalMemory: HistoricalMemoryState?,
    private val branchId: String? = null,
) : AdultModule {
    override val contractVersion: Int get() = delegate.contractVersion

    override fun evaluate(request: AdultEventRequest): AdultModuleResult {
        val primary = request.participants.firstNotNullOfOrNull { participant ->
            people.persons.firstOrNull { it.id == participant.entityId }
        } ?: return delegate.evaluate(request)

        val tags = HistoricalAdultContextBridge.tags(
            baseTags = request.context.cultureTags,
            civilizationId = primary.civilizationId,
            historicalMemory = historicalMemory,
            branchId = branchId,
            primaryPerson = primary,
        )
        return delegate.evaluate(
            request.copy(context = request.context.copy(cultureTags = tags)),
        )
    }
}
