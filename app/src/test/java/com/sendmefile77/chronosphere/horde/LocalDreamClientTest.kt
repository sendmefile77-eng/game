package com.sendmefile77.chronosphere.horde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDreamClientTest {
    private val client = LocalDreamClient("http://127.0.0.1:1")

    @Test
    fun hordeSamplerMapsToLocalDreamScheduler() {
        assertEquals("dpm_karras", client.schedulerFor("k_dpmpp_2m"))
        assertEquals("dpm_sde_karras", client.schedulerFor("k_dpmpp_2m_sde"))
        assertEquals("euler_a_karras", client.schedulerFor("k_euler_a"))
        assertEquals("lcm", client.schedulerFor("lcm"))
    }

    @Test
    fun aspectRatioIsReducedForSdxlNpu() {
        assertEquals("2:3", client.aspectRatioFor(768, 1152))
        assertEquals("3:2", client.aspectRatioFor(1152, 768))
        assertEquals("1:1", client.aspectRatioFor(1024, 1024))
    }

    @Test
    fun arbitraryChronosphereSeedBecomesUnsigned32BitSeedDeterministically() {
        val first = client.localSeed("person-17:tick-120:variant:2")
        val second = client.localSeed("person-17:tick-120:variant:2")
        assertEquals(first, second)
        assertTrue(first in 0L..0xffffffffL)
    }

    @Test
    fun currentLocalDreamBackendIsAskedForPngOutput() {
        assertEquals("png", client.requestedOutputFormat())
    }

    @Test
    fun localDreamProgressReportsStepFraction() {
        val progress = LocalDreamProgress(step = 4, totalSteps = 10)
        assertEquals(0.4f, progress.fraction, 0.0001f)
        assertEquals("Local Dream · крок 4/10", progress.captionUk)
    }
}
