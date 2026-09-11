package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicAdultModuleTest {
    private val module = DeterministicAdultModule()

    @Test
    fun reportsContractVersionOne() {
        assertEquals(ADULT_CONTRACT_VERSION, module.contractVersion)
        assertEquals(1, module.contractVersion)
    }

    @Test
    fun evaluationIsDeterministicForIdenticalRequests() {
        val request = sampleRequest()
        val first = module.evaluate(request)
        val second = module.evaluate(request)
        assertEquals(first, second)
        assertEquals(first.requestId, second.requestId)
        assertEquals(first.eventCode, second.eventCode)
        assertEquals(first.effects, second.effects)
        assertEquals(first.mediaCue, second.mediaCue)
    }

    @Test
    fun preservesRequestIdExactly() {
        val request = sampleRequest(requestId = "req-keep-me-exactly")
        val result = module.evaluate(request)
        assertEquals("req-keep-me-exactly", result.requestId)
    }

    @Test
    fun eventCodeIsStableAndNonBlank() {
        val result = module.evaluate(sampleRequest())
        assertTrue(result.eventCode.isNotBlank())
        assertFalse(result.eventCode.any { it.isWhitespace() })
        assertEquals(result.eventCode, module.evaluate(sampleRequest()).eventCode)
    }

    @Test
    fun effectMagnitudesAreFiniteAndBounded() {
        val contexts = listOf(
            sampleRequest(),
            sampleRequest(
                requestId = "puritan-court",
                tags = setOf("puritan", "dynastic"),
                numeric = mapOf("stability" to 0.8, "piety" to 1.0),
            ),
            sampleRequest(
                requestId = "open-rite",
                tags = setOf("libertine", "fertility_cult"),
                numeric = mapOf("fertility" to 0.9),
            ),
            sampleRequest(
                requestId = "solo-adult",
                participants = listOf(AdultParticipantRef("only-adult", 41)),
            ),
        )
        contexts.forEach { request ->
            module.evaluate(request).effects.forEach { effect ->
                assertTrue(effect.magnitude.isFinite())
                assertTrue(effect.magnitude in -1.0..1.0)
                assertTrue(effect.targetId.isNotBlank())
                assertTrue(effect.reasonCode.isNotBlank())
            }
        }
    }

    @Test
    fun cultureTagOrderDoesNotChangeResult() {
        val a = sampleRequest(tags = setOf("royal", "devout", "open"))
        val b = sampleRequest(tags = setOf("open", "royal", "devout"))
        assertEquals(module.evaluate(a), module.evaluate(b))
    }

    @Test
    fun mediaCueIsLogicalKeyOnly() {
        val cue = module.evaluate(sampleRequest()).mediaCue
        assertTrue(cue != null)
        assertTrue(cue!!.assetKey.startsWith("adult://"))
        assertTrue(cue.assetKey.isNotBlank())
        assertTrue(cue.tags.isNotEmpty())
    }

    private fun sampleRequest(
        requestId: String = "req-alpha",
        participants: List<AdultParticipantRef> = listOf(
            AdultParticipantRef("person-a", 27),
            AdultParticipantRef("person-b", 31),
        ),
        tags: Set<String> = setOf("courtly"),
        numeric: Map<String, Double> = mapOf("tension" to 0.25),
    ): AdultEventRequest = AdultEventRequest(
        requestId = requestId,
        participants = participants,
        context = AdultWorldContext(
            worldSeed = 424242L,
            tick = 120L,
            cultureTags = tags,
            numericContext = numeric,
        ),
    )
}
