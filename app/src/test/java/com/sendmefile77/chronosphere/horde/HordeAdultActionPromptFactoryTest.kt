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
        assertTrue(request.cacheKey.startsWith("horde-adult-action-v8|"))
        assertTrue(request.positivePrompt.startsWith("ORAL SEX:"))
        assertTrue(request.positivePrompt.contains("blowjob") || request.positivePrompt.contains("cunnilingus"))
        assertTrue(request.positivePrompt.contains("mouth"))
        assertTrue(request.positivePrompt.contains("the only sex act"))
        assertFalse(request.positivePrompt.contains("natural standing or seated pose"))
        assertTrue(request.negativePrompt.contains("standing idle portrait"))
        assertTrue(request.negativePrompt.contains("footjob"))
        assertFalse(request.preferredModels.first().contains("illustrious", ignoreCase = true))
        assertTrue(request.referenceDenoisingStrength < 0.45)
        assertFalse(request.saveResultAsReference)
    }

    @Test
    fun newActsLeadWithTheirNames() {
        val bukkake = HordeAdultActionPromptFactory.create(scene(), twoWomen(AdultActionType.BUKKAKE))
        val masturbation = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.MASTURBATION).copy(partner = null))
        val bdsm = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.BDSM))
        val futa = HordeAdultActionPromptFactory.create(scene(), twoWomen(AdultActionType.FUTANARI_ORGASM))
        assertTrue(bukkake.positivePrompt.startsWith("BUKKAKE:"))
        assertTrue(bukkake.positivePrompt.contains("semen") || bukkake.positivePrompt.contains("penises"))
        assertTrue(masturbation.positivePrompt.startsWith("MASTURBATION:"))
        assertTrue(bdsm.positivePrompt.startsWith("BDSM:"))
        assertTrue(futa.positivePrompt.startsWith("FUTANARI ORGASM:"))
        assertTrue(futa.positivePrompt.contains("penis"))
    }

    @Test
    fun tribalAndMedievalFootjobsAreDifferentScenes() {
        val tribal = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.FOOTJOB), technologyEra = TechnologyEra.TRIBAL)
        val medieval = HordeAdultActionPromptFactory.create(scene(), plan(AdultActionType.FOOTJOB), technologyEra = TechnologyEra.MEDIEVAL)
        assertTrue(tribal.positivePrompt.contains("FOOTJOB") || tribal.positivePrompt.contains("footjob"))
        assertTrue(tribal.positivePrompt.contains("hide tent") || tribal.positivePrompt.contains("reed hut"))
        assertTrue(medieval.positivePrompt.contains("rope bed") || medieval.positivePrompt.contains("candle"))
        assertTrue(tribal.negativePrompt.contains("modern tiled bathroom"))
        assertNotEquals(tribal.cacheKey, medieval.cacheKey)
    }

    private fun plan(type: AdultActionType) = AdultActionPlan(type, 1, AdultActionParticipant("adult-a", "A", 24, BiologicalSex.FEMALE), AdultActionParticipant("adult-b", "B", 26, BiologicalSex.MALE))
    private fun twoWomen(type: AdultActionType) = AdultActionPlan(type, 1, AdultActionParticipant("adult-a", "A", 24, BiologicalSex.FEMALE), AdultActionParticipant("adult-c", "C", 27, BiologicalSex.FEMALE))
    private fun scene() = ResolvedScene("card", "card.undressed.human", "base", 1, "card", WardrobeState.UNDRESSED, "rig.human.card", "pose.card.neutral", "bg.card.neutral", "cam.card.full", "light.card.soft", emptyList(), false)
}
