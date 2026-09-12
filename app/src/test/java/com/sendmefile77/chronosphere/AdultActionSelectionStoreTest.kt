package com.sendmefile77.chronosphere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdultActionSelectionStoreTest {
    @Test
    fun remembersTheLastChosenActAfterAFreshRead() {
        AdultActionSelectionStore.clear("person-keep")
        val stored = AdultActionSelectionStore.remember("person-keep", AdultActionType.FOOTJOB)
        assertEquals(AdultActionType.FOOTJOB, stored.type)
        assertEquals(1, stored.sequence)
        assertEquals(stored, AdultActionSelectionStore.get("person-keep"))

        val second = AdultActionSelectionStore.remember("person-keep", AdultActionType.ORAL)
        assertEquals(AdultActionType.ORAL, second.type)
        assertEquals(2, second.sequence)
        AdultActionSelectionStore.clear("person-keep")
        assertNull(AdultActionSelectionStore.get("person-keep"))
    }
}
