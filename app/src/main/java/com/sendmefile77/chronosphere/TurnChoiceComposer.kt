package com.sendmefile77.chronosphere

/**
 * One turn screen owns the mandatory historical fork and the era-development directions.
 *
 * When the simulation has not produced a natural unresolved event, CenturyDilemmaCatalog supplies
 * an era-specific dilemma. A century therefore never collapses into a sterile "pick upgrades and
 * wait" screen: the player must answer one concrete problem and then choose the long-term course.
 */
internal object TurnChoiceComposer {
    const val MAX_ERA_CHOICES = 3

    fun compose(
        eraDecision: ChronicleDecision,
        historicalDecision: ChronicleDecision?,
    ): ChronicleDecision {
        val fork = historicalDecision ?: CenturyDilemmaCatalog.fromEraDecision(eraDecision)
        val era = EraExperience.eraFromDecision(eraDecision)
        val chapter = era?.let(EraExperience::chapter)

        return eraDecision.copy(
            titleUk = eraDecision.titleUk,
            promptUk = buildString {
                if (chapter != null) {
                    append(chapter.title).append(". ")
                    append(chapter.opening)
                    append("\n\n")
                    append(chapter.power)
                    append("\n\n")
                }
                if (fork != null) {
                    append("ЦЬОГО СТОЛІТТЯ · ")
                    append(fork.titleUk)
                    append("\n")
                    append(fork.promptUk)
                    append("\n\n")
                    append("Оберіть одну відповідь на цю ситуацію та від одного до трьох напрямів розвитку епохи. ")
                } else {
                    append("Оберіть від одного до трьох напрямів розвитку епохи. ")
                }
                append("Після підтвердження світ одразу проживе наступні 100 років, а наслідки стануть частиною його історії.")
            },
            options = fork?.options.orEmpty() + eraDecision.options,
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
