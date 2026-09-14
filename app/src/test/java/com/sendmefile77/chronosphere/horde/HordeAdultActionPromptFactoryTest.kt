package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.AdultActionParticipant
import com.sendmefile77.chronosphere.AdultActionPlan
import com.sendmefile77.chronosphere.AdultActionType
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeAdultActionPromptFactoryTest {
    @Test
    fun oralScenePutsTheActBeforeAnyIdlePortraitLanguage() {
        val request = HordeAdultActionPromptFactory.create(scene = scene(), plan = plan(AdultActionType.ORAL))
        assertTrue(request.nsfw)
        assertTrue(request.cacheKey.startsWith("horde-adult-action-v12|"))
        assertTrue(request.positivePrompt.startsWith("BLOWJOB:"))
        assertTrue(request.positivePrompt.contains("blowjob", ignoreCase = true))
        assertTrue(request.positivePrompt.contains("selected act lock"))
        assertTrue(request.positivePrompt.contains("primary adult face locked"))
        assertFalse(request.positivePrompt.contains("natural standing or seated pose"))
        assertTrue(request.negativePrompt.contains("standing idle portrait"))
        assertTrue(request.negativePrompt.contains("dog"))
        assertFalse(request.preferredModels.first().contains("illustrious", ignoreCase = true))
        assertTrue(request.referenceDenoisingStrength < 0.45)
        assertFalse(request.saveResultAsReference)
    }

    @Test
    fun expandedActsLeadWithTheirSelectedVisualLanguage() {
        val expected = linkedMapOf(
            AdultActionType.HANDJOB to "HANDJOB:",
            AdultActionType.CUNNILINGUS to "CUNNILINGUS:",
            AdultActionType.SIXTY_NINE to "69:",
            AdultActionType.PAIZURI to "PAIZURI:",
            AdultActionType.SCISSORING to "SCISSORING:",
            AdultActionType.MUTUAL_MASTURBATION to "MUTUAL MASTURBATION:",
            AdultActionType.FACIAL to "FACIAL:",
            AdultActionType.CREAMPIE to "CREAMPIE:",
            AdultActionType.MMF to "MMF THREESOME:",
            AdultActionType.FFM to "FFM THREESOME:",
        )

        expected.forEach { (type, prefix) ->
            val request = HordeAdultActionPromptFactory.create(scene(), plan(type))
            assertTrue("$type should lead its prompt", request.positivePrompt.startsWith(prefix))
            assertTrue("$type should carry a selected-act lock", request.positivePrompt.contains("selected act lock"))
        }
    }

    @Test
    fun classicActsStillLeadWithTheirNames() {
        val bukkake = HordeAdultActionPromptFactory.create(scene(), twoWomen(AdultActionType.BUKKAKE))
        val masturbation = HordeAdultActionPromptFactory.create(
            scene(),
            plan(AdultActionType.MASTURBATION).copy(partner = null),
        )
        val bdsm = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.BDSM))
        val futa = HordeAdultActionPromptFactory.create(scene(), twoWomen(AdultActionType.FUTANARI_ORGASM))
        assertTrue(bukkake.positivePrompt.startsWith("BUKKAKE:"))
        assertTrue(masturbation.positivePrompt.startsWith("MASTURBATION:"))
        assertTrue(bdsm.positivePrompt.startsWith("BDSM:"))
        assertTrue(futa.positivePrompt.startsWith("FUTANARI ORGASM:"))
    }

    @Test
    fun wrongActNegativesProtectSpecificSelections() {
        val footjob = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.FOOTJOB))
        val handjob = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.HANDJOB))
        val cunnilingus = HordeAdultActionPromptFactory.create(scene(), twoWomen(AdultActionType.CUNNILINGUS))
        val paizuri = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.PAIZURI))
        val mmf = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.MMF))

        assertTrue(footjob.negativePrompt.contains("blowjob"))
        assertTrue(handjob.negativePrompt.contains("footjob"))
        assertTrue(cunnilingus.negativePrompt.contains("blowjob"))
        assertTrue(paizuri.negativePrompt.contains("handjob"))
        assertTrue(mmf.negativePrompt.contains("only two people"))
    }

    @Test
    fun tribalAndMedievalFootjobsAreDifferentScenes() {
        val tribal = HordeAdultActionPromptFactory.create(
            scene(),
            plan(AdultActionType.FOOTJOB),
            technologyEra = TechnologyEra.TRIBAL,
        )
        val medieval = HordeAdultActionPromptFactory.create(
            scene(),
            plan(AdultActionType.FOOTJOB),
            technologyEra = TechnologyEra.MEDIEVAL,
        )
        assertTrue(tribal.positivePrompt.contains("FOOTJOB") || tribal.positivePrompt.contains("footjob"))
        assertTrue(tribal.positivePrompt.contains("hide tent") || tribal.positivePrompt.contains("reed hut"))
        assertTrue(medieval.positivePrompt.contains("rope bed") || medieval.positivePrompt.contains("candle"))
        assertNotEquals(tribal.cacheKey, medieval.cacheKey)
    }

    private fun plan(type: AdultActionType) = AdultActionPlan(
        type,
        1,
        AdultActionParticipant("adult-a", "A", 24, BiologicalSex.FEMALE),
        AdultActionParticipant("adult-b", "B", 26, BiologicalSex.MALE),
    )

    private fun twoWomen(type: AdultActionType) = AdultActionPlan(
        type,
        1,
        AdultActionParticipant("adult-a", "A", 24, BiologicalSex.FEMALE),
        AdultActionParticipant("adult-c", "C", 27, BiologicalSex.FEMALE),
    )

    private fun scene() = ResolvedScene(
        "card",
        "card.undressed.human",
        "base",
        1,
        "card",
        WardrobeState.UNDRESSED,
        "rig.human.card",
        "pose.card.neutral",
        "bg.card.neutral",
        "cam.card.full",
        "light.card.soft",
        emptyList(),
        false,
    )
}
