package com.sendmefile77.chronosphere.llm

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
}
