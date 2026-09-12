package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.Dynasty
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeAdultScenePromptFactoryTest {
    @Test
    fun storedAdultRecipeBecomesStructuredMultiParticipantHordeScene() {
        val request = HordeAdultScenePromptFactory.createEvent(
            event = adultEvent(),
            people = people(),
        )

        requireNotNull(request)
        assertTrue(request.nsfw)
        assertTrue(request.width > request.height)
        assertTrue(request.positivePrompt.contains("missionary"))
        assertTrue(request.positivePrompt.contains("private sleeping chamber"))
        assertTrue(request.positivePrompt.contains("intimate medium-close"))
        assertTrue(request.positivePrompt.contains("medieval"))
        assertTrue(request.positivePrompt.contains("two complete adult bodies"))
        assertTrue(request.positivePrompt.contains("penetration"))
        assertTrue(request.negativePrompt.contains("child"))
        assertNull(request.referenceCacheKey)
        assertFalse(request.saveResultAsReference)
    }

    @Test
    fun participantUnder18BlocksAdultEventBeforeHorde() {
        val people = people().copy(
            persons = listOf(
                person("adult-a", 0L),
                person("adult-b", 300L),
            ),
        )
        assertNull(
            HordeAdultScenePromptFactory.createEvent(
                event = adultEvent(),
                people = people,
            ),
        )
    }

    @Test
    fun undressedCharacterUsesStructuredRecipeButNeverOverwritesReference() {
        val descriptor = AdultVisualSceneDescriptor(
            requestId = "card-a",
            intent = "character_undress",
            eventCode = "CHARACTER_CARD",
            participants = listOf(AdultParticipantRef("adult-a", 30)),
            recipeId = "card.undressed.baseline",
            sceneFamily = "character-card",
            rigLayout = "solo-bust",
            poseKey = "pose.card-idle",
            wardrobeKey = "wardrobe.undressed",
            settingKey = "set.card",
            cameraKey = "cam.portrait",
            lightingKey = "light.soft",
            explicitness = "explicit",
            effectTags = setOf("nude", "undressed"),
        )
        val request = HordeAdultScenePromptFactory.createCharacter(
            scene = ResolvedScene(
                sceneKey = "adult-card",
                recipeId = "card.undressed.baseline",
                packId = "adult-visual",
                packVersion = 1,
                styleId = "adult-style",
                wardrobeState = WardrobeState.UNDRESSED,
                bodyRigKey = "solo-bust",
                poseKey = "pose.card-idle",
                backgroundKey = "set.card",
                cameraKey = "cam.card.full",
                lightingKey = "light.soft",
                layerKeys = emptyList(),
                fallbackUsed = false,
            ),
            descriptor = descriptor,
            characterKey = "adult-a",
            ageYears = 30,
            technologyEra = TechnologyEra.TRIBAL,
        )

        assertTrue(request.nsfw)
        assertTrue(request.positivePrompt.contains("character presentation"))
        assertTrue(request.positivePrompt.contains("private character presentation environment"))
        assertTrue(request.positivePrompt.contains("prehistoric tribal"))
        assertTrue(request.referenceCacheKey != null)
        assertFalse(request.saveResultAsReference)
    }

    private fun adultEvent(): SimulationEvent = SimulationEvent(
        id = "adult-event-360",
        tick = 360L,
        code = "ADULT_SOCIAL_EVENT",
        actorIds = listOf("adult-a", "adult-b", "civ"),
        facts = mapOf(
            "civilization" to "Test Civ",
            "eventCode" to "UNION",
            "participants" to "A, B",
            "mediaKey" to "adult://recipe/union.chamber.missionary",
            "mediaTags" to listOf(
                "recipe:union.chamber.missionary",
                "family:coupling",
                "rig:pair-bed",
                "pose:pose.missionary",
                "wardrobe:wardrobe.undressed",
                "setting:set.chamber",
                "camera:cam.intimate",
                "light:light.lamp",
                "event:union",
                "pack-setting:chamber",
                "explicitness:explicit",
                "participants_2",
                "rig-plan:baseline",
                "penetration",
                "sex",
                "era:era_medieval",
                "posture:upright",
                "covering:bare_skin",
                "arms:2",
                "legs:2",
                "eyes:2",
            ).joinToString("|"),
        ),
    )

    private fun people(): PeopleState = PeopleState(
        worldSeed = 1L,
        tick = 360L,
        persons = listOf(person("adult-a", 0L), person("adult-b", 0L)),
        dynasties = emptyList<Dynasty>(),
        relationships = emptyList(),
        rulerByCivilization = emptyMap(),
        socialProfiles = emptyList(),
    )

    private fun person(id: String, birthTick: Long): NotablePerson = NotablePerson(
        id = id,
        name = id,
        civilizationId = "civ",
        settlementId = null,
        dynastyId = null,
        birthTick = birthTick,
        role = PersonRole.NOTABLE,
        prestige = 0.5,
        aptitude = 0.5,
    )
}
