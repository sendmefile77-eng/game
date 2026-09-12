package com.sendmefile77.chronosphere.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TellamaClientTest {
    @Test
    fun apiKeyStateIsNormalized() {
        val client = TellamaClient("http://127.0.0.1:1")
        assertFalse(client.hasApiKey())
        client.setApiKey("   ")
        assertFalse(client.hasApiKey())
        client.setApiKey("  tlm_example  ")
        assertTrue(client.hasApiKey())
    }

    @Test
    fun chatNdjsonIsJoinedUntilDone() {
        val client = TellamaClient("http://127.0.0.1:1")
        val result = client.collectChatNdjson(
            sequenceOf(
                "{\"message\":{\"content\":\"Hello \"},\"done\":false}",
                "{\"message\":{\"content\":\"world\"},\"done\":false}",
                "{\"message\":{\"content\":\"!\"},\"done\":true}",
                "{\"message\":{\"content\":\" ignored\"},\"done\":false}",
            ),
        )
        assertEquals("Hello world!", result)
    }
}
