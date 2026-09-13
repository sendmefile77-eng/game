package com.sendmefile77.chronosphere

/**
 * One turn screen owns both the mandatory historical fork (when one exists) and the optional
 * era-development directions. This avoids a chain of modal confirmations before time can move.
 */
internal object TurnChoiceComposer {
    const val MAX_ERA_CHOICES = 3

    fun compose(
        eraDecision: ChronicleDecision,
        historicalDecision: ChronicleDecision?,
    ): ChronicleDecision {
        if (historicalDecision == null) return eraDecision
        return eraDecision.copy(
            titleUk = eraDecision.titleUk,
            promptUk = buildString {
                append("Історія вимагає відповіді: «${historicalDecision.titleUk}». ")
                append("Оберіть одну реакцію на цю подію та до трьох напрямів розвитку епохи. ")
                append("Після підтвердження світ одразу проживе наступні 100 років.")
            },
            options = historicalDecision.options + eraDecision.options,
        )
    }

    fun isEraOption(option: ChronicleDecisionOption): Boolean =
        EraTurnChoiceCatalog.family(option) != null

    fun selectionGroup(option: ChronicleDecisionOption): String =
        EraTurnChoiceCatalog.family(option)?.let { "era:$it" }
            ?: "event:${option.sourceEventId}"

    fun requiredHistoricalSources(decision: ChronicleDecision): Set<String> = decision.options
        .asSequence()
        .filterNot(::isEraOption)
        .map { it.sourceEventId }
        .toSet()

    /** Pure selection reducer shared by Compose and unit tests. */
    fun toggleSelection(
        decision: ChronicleDecision,
        selectedIds: Set<String>,
        optionId: String,
    ): Set<String> {
        val option = decision.options.firstOrNull { it.id == optionId } ?: return selectedIds
        if (option.id in selectedIds) return selectedIds - option.id

        val group = selectionGroup(option)
        val withoutSameGroup = selectedIds.filterTo(linkedSetOf()) { id ->
            val old = decision.options.firstOrNull { it.id == id }
            old == null || selectionGroup(old) != group
        }
        if (isEraOption(option)) {
            val eraCountAfterReplacement = decision.options.count { candidate ->
                candidate.id in withoutSameGroup && isEraOption(candidate)
            }
            if (eraCountAfterReplacement >= MAX_ERA_CHOICES) return selectedIds
        }
        return withoutSameGroup + option.id
    }
}
