package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeAdultVisualEnrichmentTest {
    @Test
    fun tribalHunterChoiceAddsCampSexNotAStudioNude() {
        val prompt = HordeAdultVisualEnrichment.fragment(
            setOf(
                "era-choice:subsistence:predator_hunters",
                "policy:predator_hunters",
                "body-norm:painted-skin",
                "publicness:communal",
            ),
            TechnologyEra.TRIBAL,
            HordeAdultVisualEnrichment.Kind.SCENE,
        )
        assertTrue(prompt.contains("hunt") || prompt.contains("spears") || prompt.contains("drying meat"))
        assertTrue(prompt.contains("hearth") || prompt.contains("camp") || prompt.contains("ochre") || prompt.contains("communal"))
        assertFalse(prompt.contains("hotel"))
    }

    @Test
    fun agrarianAndIndustrialIntimacyDiverge() {
        val agrarian = HordeAdultVisualEnrichment.fragment(
            setOf("era-choice:subsistence:grain_farming", "foundation:grain_farming", "publicness:household"),
            TechnologyEra.AGRARIAN,
            HordeAdultVisualEnrichment.Kind.CHRONICLE,
        )
        val industrial = HordeAdultVisualEnrichment.fragment(
            setOf("era-choice:society:factory_discipline", "policy:factory_discipline", "publicness:crowd-close"),
            TechnologyEra.INDUSTRIAL,
            HordeAdultVisualEnrichment.Kind.CHRONICLE,
        )
        assertTrue(agrarian.contains("granary") || agrarian.contains("grain") || agrarian.contains("household"))
        assertTrue(industrial.contains("shift") || industrial.contains("whistle") || industrial.contains("tenement") || industrial.contains("factory"))
        assertNotEquals(agrarian, industrial)
    }

    @Test
    fun informationPoliciesShapeNetworkedIntimacy() {
        val prompt = HordeAdultVisualEnrichment.fragment(
            setOf(
                "era-choice:society:algorithmic_governance",
                "era-choice:mobility:remote_life",
                "person_role:scholar",
            ),
            TechnologyEra.INFORMATION,
            HordeAdultVisualEnrichment.Kind.SOCIAL,
        )
        assertTrue(prompt.contains("surveil") || prompt.contains("network") || prompt.contains("screen") || prompt.contains("telepresence") || prompt.contains("home-office") || prompt.contains("remote"))
    }

    @Test
    fun ancientBreakthroughsDoNotDominateSpacefaringSex() {
        val prompt = HordeAdultVisualEnrichment.fragment(
            setOf(
                "era-choice:breakthrough:fire",
                "era-choice:breakthrough:orbital_habitats",
                "era-choice:society:closed_ecologies",
            ),
            TechnologyEra.SPACEFARING,
            HordeAdultVisualEnrichment.Kind.SCENE,
        )
        assertTrue(prompt.contains("habitat") || prompt.contains("cabin") || prompt.contains("ecology") || prompt.contains("viewport"))
        assertFalse(prompt.contains("flint"))
        assertFalse(prompt.contains("charred cooking"))
    }

    @Test
    fun rulerStatusChangesTheAdultRoomWithoutReplacingTheEra() {
        val common = HordeAdultVisualEnrichment.fragment(
            setOf("era-choice:society:lordly_estates", "person_role:artisan"),
            TechnologyEra.MEDIEVAL,
            HordeAdultVisualEnrichment.Kind.PORTRAIT,
        )
        val ruler = HordeAdultVisualEnrichment.fragment(
            setOf("era-choice:society:lordly_estates", "person_role:ruler"),
            TechnologyEra.MEDIEVAL,
            HordeAdultVisualEnrichment.Kind.PORTRAIT,
        )
        assertTrue(ruler.contains("ruler") || ruler.contains("heir") || ruler.contains("rank"))
        assertNotEquals(common, ruler)
    }
}
