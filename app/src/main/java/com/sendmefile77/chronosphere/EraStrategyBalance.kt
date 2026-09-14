package com.sendmefile77.chronosphere

/**
 * Balances the 1–3 era-direction choice.
 *
 * Long-term legacy tags still persist exactly as before. Only the immediate intervention strength
 * is redistributed: one direction is a concentrated push, two are balanced, three spread the
 * state's attention. This prevents "always pick three" from being the dominant answer.
 */
internal object EraStrategyBalance {
    private const val FOCUSED_MULTIPLIER = 1.20
    private const val BALANCED_MULTIPLIER = 1.00
    private const val BROAD_MULTIPLIER = 0.82

    fun apply(options: List<ChronicleDecisionOption>): List<ChronicleDecisionOption> {
        val eraCount = options.count(TurnChoiceComposer::isEraOption)
        val multiplier = when (eraCount) {
            1 -> FOCUSED_MULTIPLIER
            2 -> BALANCED_MULTIPLIER
            else -> BROAD_MULTIPLIER
        }
        return options.map { option ->
            if (!TurnChoiceComposer.isEraOption(option)) option
            else option.copy(strength = (option.strength * multiplier).coerceIn(0.0, 1.0))
        }
    }

    fun title(eraCount: Int): String = when (eraCount) {
        0 -> "Стратегія ще не визначена"
        1 -> "Сфокусований ривок"
        2 -> "Збалансований курс"
        else -> "Широка перебудова"
    }

    fun detail(eraCount: Int): String = when (eraCount) {
        0 -> "Оберіть хоча б один напрям."
        1 -> "Одна мета отримує всю політичну увагу: її негайний ефект посилюється на 20%."
        2 -> "Два напрями отримують повну базову силу без штрафу за розпорошення."
        else -> "Три напрями змінюють суспільство ширше, але негайний ефект кожного слабшає на 18%. Їхні довгі історичні наслідки все одно зберігаються."
    }
}
