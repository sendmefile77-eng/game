package com.sendmefile77.chronosphere.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmNarrativeReplyParsingTest {
    @Test
    fun extractsDoubleQuotedFieldFromImperfectJson() {
        val raw = "prefix {\"quote\":\"Я бачу зміни й не відступлю.\",\"note\":\"рішучість\"} suffix"
        assertEquals(
            "Я бачу зміни й не відступлю.",
            LlmNarrativeWriter.flexibleField(raw, "quote", parsed = null),
        )
    }

    @Test
    fun extractsSingleQuotedFieldFromQwenStylePseudoJson() {
        val raw = "{'quote':'Ми тримаємося старих звичаїв, бо вони нас зберегли.','note':'традиціоналізм'}"
        assertEquals(
            "Ми тримаємося старих звичаїв, бо вони нас зберегли.",
            LlmNarrativeWriter.flexibleField(raw, "quote", parsed = null),
        )
    }

    @Test
    fun plainReplyDoesNotDiscardBracedOneLineAnswer() {
        val raw = "{quote: Я говоритиму прямо і коротко}"
        val parsed = LlmNarrativeWriter.plainReply(raw)
        assertTrue(parsed.contains("Я говоритиму прямо і коротко"))
    }

    @Test
    fun plainTextReplySurvivesUnchanged() {
        val raw = "Я пам'ятаю старі звичаї й не хочу ламати їх без потреби."
        assertEquals(raw, LlmNarrativeWriter.plainReply(raw))
    }
}
