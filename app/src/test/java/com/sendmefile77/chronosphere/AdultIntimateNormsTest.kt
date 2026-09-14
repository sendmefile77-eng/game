package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.people.SocialProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultIntimateNormsTest {
    @Test
    fun tribalAndInformationCulturesAreNotTheSameCostume() {
        val tribal = AdultIntimateNorms.resolve(TechnologyEra.TRIBAL, openProfile())
        val info = AdultIntimateNorms.resolve(TechnologyEra.INFORMATION, openProfile())
        assertTrue(tribal.setting.contains("hide") || tribal.setting.contains("hearth"))
        assertTrue(info.setting.contains("apartment") || info.setting.contains("screen"))
        assertNotEquals(tribal.partnership, info.partnership)
        assertNotEquals(tribal.setting, info.setting)
        assertNotEquals(tribal.clothing, info.clothing)
        assertNotEquals(tribal.visualSignature, info.visualSignature)
    }

    @Test
    fun pietyAndRitualPolicyStripPublicExtremeActs() {
        val modest = AdultIntimateNorms.resolve(
            era = TechnologyEra.MEDIEVAL,
            profile = openProfile().copy(piety = 0.86, bodyOpenness = 0.22, privacy = 0.78),
            tags = setOf("policy:ritual_culture", "puritan"),
            role = PersonRole.CLERGY,
        )
        assertFalse(modest.allowedActs.contains(AdultActionType.BUKKAKE))
        assertFalse(modest.allowedActs.contains(AdultActionType.BDSM))
        assertTrue(modest.tabooSummary.contains("punish") || modest.tabooSummary.contains("rite"))
    }

    @Test
    fun defaultActStaysInsideTheLivingAllowList() {
        val norms = AdultIntimateNorms.resolve(TechnologyEra.TRIBAL, openProfile())
        repeat(8) { sequence ->
            val act = AdultIntimateNorms.defaultAct(norms, "adult-a", 360L, sequence + 1)
            assertTrue(norms.allows(act))
        }
    }

    private fun openProfile(): SocialProfile = SocialProfile(
        civilizationId = "civ",
        privacy = 0.35,
        bodyOpenness = 0.62,
        pairBonding = 0.48,
        jealousy = 0.40,
        fertilityNorm = 0.55,
        piety = 0.32,
        statusHierarchy = 0.44,
        socialTension = 0.30,
        tags = emptySet(),
    )
}
