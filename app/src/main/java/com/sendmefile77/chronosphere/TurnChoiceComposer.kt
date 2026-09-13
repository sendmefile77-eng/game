package com.sendmefile77.chronosphere

/**
 * One turn screen owns both the mandatory historical fork (when one exists) and the optional
 * era-development directions. This avoids a chain of modal confirmations before time can move.
 */
internal object TurnChoiceComposer {
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
}
