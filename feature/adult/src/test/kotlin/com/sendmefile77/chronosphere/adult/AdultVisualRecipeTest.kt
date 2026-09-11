package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultVisualRecipeTest {
    private val module = DeterministicAdultModule()
    private val recipes = AdultVisualRecipeRegistry.bundled()

    @Test
    fun bundledRecipesPassValidation() {
        assertTrue(AdultVisualRecipeValidator.validate(BundledVisualRecipes.all).isEmpty())
    }

    @Test
    fun recipeSelectionIsDeterministic() {
        val request = sample()
        val first = module.evaluate(request).mediaCue
        val second = module.evaluate(request).mediaCue
        assertEquals(first, second)
        assertEquals(first!!.tags, second!!.tags)
    }

    @Test
    fun pairRecipeRejectedForSolo() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        val compatible = recipes.compatible(union, sample(participants = 1))
        assertTrue(compatible.none { "UNION" in it.eventCodes })
        val cue = recipes.mediaCue(union, sample(participants = 1), 1L)
        assertEquals("adult://recipe/${SAFE_VISUAL_FALLBACK.id}", cue.assetKey)
    }

    @Test
    fun orgyRecipeRequiresGroup() {
        val orgy = AdultPackRegistry.bundled().pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "ORGY" }
        assertTrue(recipes.compatible(orgy, sample(participants = 2)).isEmpty())
        assertTrue(recipes.compatible(orgy, sample(participants = 4, tags = setOf("libertine"))).isNotEmpty())
    }

    @Test
    fun austereCultureFiltersForbiddenAnalRecipe() {
        val anal = AdultPackRegistry.bundled().pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "ANAL_UNION" }
        assertTrue(recipes.compatible(anal, sample(tags = setOf("puritan"), participants = 2)).isEmpty())
        assertTrue(recipes.compatible(anal, sample(tags = setOf("libertine"), participants = 2)).isNotEmpty())
    }

    @Test
    fun mediaCueEncodesStableComposerMetadata() {
        val cue = module.evaluate(sample()).mediaCue!!
        assertTrue(cue.assetKey.startsWith("adult://recipe/"))
        assertTrue(cue.tags.any { it.startsWith("recipe:") })
        assertTrue(cue.tags.any { it.startsWith("pose:") })
        assertTrue(cue.tags.any { it.startsWith("wardrobe:") })
        assertTrue(cue.tags.any { it.startsWith("setting:") })
        assertTrue(cue.tags.any { it.startsWith("camera:") })
        assertTrue(cue.tags.any { it.startsWith("light:") })
        assertTrue(cue.tags.any { it.startsWith("rig:") })
        val again = module.evaluate(sample(tags = setOf("courtly"))).mediaCue!!
        assertEquals(cue.tags, again.tags)
    }

    @Test
    fun tagOrderDoesNotChangeRecipe() {
        val a = module.evaluate(sample(tags = setOf("royal", "devout", "open")))
        val b = module.evaluate(sample(tags = setOf("open", "royal", "devout")))
        assertEquals(a.eventCode, b.eventCode)
        assertEquals(a.mediaCue, b.mediaCue)
        assertEquals(a.effects, b.effects)
    }

    @Test
    fun g003EventAndEffectRegression() {
        val result = module.evaluate(sample(tags = setOf("courtly"), participants = 2))
        assertTrue(result.eventCode in BundledAdultPacks.classicEvents.map { it.code })
        result.effects.forEach { effect ->
            assertTrue(effect.magnitude.isFinite())
            assertTrue(effect.magnitude in -1.0..1.0)
        }
        assertFalse(result.mediaCue!!.assetKey.isBlank())
    }

    @Test
    fun validatorRejectsBrokenRecipe() {
        val broken = AdultVisualRecipe(
            id = "dup",
            eventCodes = setOf("UNION"),
            sceneFamily = "x",
            rigLayout = "x",
            poseKey = "",
            wardrobeKey = "x",
            settingKey = "x",
            cameraKey = "x",
            lightingKey = "x",
            effectTags = setOf("sex"),
            minParticipants = 3,
            maxParticipants = 1,
            requiredTags = setOf("open"),
            forbiddenTags = setOf("open"),
            weight = -1.0,
        )
        val errors = AdultVisualRecipeValidator.validate(listOf(broken, broken.copy(id = "dup", poseKey = "p")))
        assertTrue(errors.any { it.contains("poseKey") })
        assertTrue(errors.any { it.contains("maxParticipants") })
        assertTrue(errors.any { it.contains("required tag also forbidden") })
        assertTrue(errors.any { it.contains("weight") || it.contains("duplicate") })
    }

    @Test
    fun everyAcceptedEventHasAtLeastOneRecipe() {
        val codes = BundledAdultPacks.all.flatMap { pack -> pack.events.map { it.code } }.toSet() + "CONTEXT_HOLD"
        val covered = BundledVisualRecipes.all.flatMap { it.eventCodes }.toSet()
        assertTrue(codes.all { it in covered })
    }

    private fun sample(
        tags: Set<String> = setOf("courtly"),
        participants: Int = 2,
    ): AdultEventRequest = AdultEventRequest(
        requestId = "req-alpha",
        participants = (1..participants).map { AdultParticipantRef("person-$it", 24 + it) },
        context = AdultWorldContext(
            worldSeed = 424242L,
            tick = 120L,
            cultureTags = tags,
            numericContext = mapOf(SocialContextKeys.TENSION to 0.25),
        ),
    )
}
