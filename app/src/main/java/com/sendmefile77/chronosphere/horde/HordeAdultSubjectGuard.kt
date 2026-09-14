package com.sendmefile77.chronosphere.horde

/**
 * Adult-path adapter: historical domestication language from the SFW layer
 * ("tamed dogs", pens, herds) must never become the picture's subject.
 *
 * A chimera / evolved humanoid *is* a valid subject. A quadruped animal is not.
 * Erotic overlay must not replace the named face and body identity.
 *
 * Does not rewrite [HordeDecisionVisualCue] / [LocalDreamMaterialCueBridge].
 * It only sanitizes already-assembled adult/portrait prompts.
 */
internal object HordeAdultSubjectGuard {
    const val HUMAN_LOCK =
        "the only subject is an adult person of this lineage: a human or evolved humanoid chimera " +
            "standing as a person, complete visible body filling the frame, never a quadruped animal, " +
            "never an empty room"

    const val PERSON_LOCK = HUMAN_LOCK

    const val CHIMERA_LOCK =
        "humanoid chimera person as the subject, bipedal adult body, extra limbs or tail or " +
            "nonhuman covering belong to this one person, not a separate animal in frame"

    const val IDENTITY_LOCK =
        "keep this specific adult face and body identity; erotic action must not replace the person, " +
            "same hair, same eyes, same skin, same face shape"

    /**
     * Keeps adult imagery unmistakably adult and sensual rather than drifting into kawaii / youthful
     * character design. This is injected only on 18+ adult paths.
     */
    const val EROTIC_LOCK =
        "unmistakably adult mature presentation, mature adult facial features and adult body proportions, " +
            "confident sensual expression, direct erotic gaze, provocative erotic body language, " +
            "explicit adult nude person, visible breasts or chest, visible genitals, sexually charged pose"

    val NEGATIVES = animalSubjectNegatives(chimeric = false)

    fun animalSubjectNegatives(chimeric: Boolean): List<String> = buildList {
        addAll(
            listOf(
                "dog",
                "puppy",
                "wolf as subject",
                "fox as subject",
                "cat as subject",
                "horse as subject",
                "cow",
                "goat",
                "sheep",
                "livestock as subject",
                "animal only",
                "animal as the main subject",
                "quadruped as subject",
                "no humans",
                "no person",
                "empty room",
                "empty interior",
                "vacant tent",
                "still life",
                "bestiality",
                "zoophilia",
                "animal focus",
                "childlike face",
                "baby face",
                "underage",
                "teen",
                "schoolgirl",
                "kawaii",
                "moe",
                "chibi",
                "childlike proportions",
            ),
        )
        if (!chimeric) {
            add("furry")
            add("anthro")
        }
    }

    fun looksChimeric(text: String): Boolean {
        val source = text.lowercase()
        return source.contains("exactly 3 arms") ||
            source.contains("exactly 4 arms") ||
            source.contains("exactly 6 arms") ||
            source.contains("exactly 3 legs") ||
            source.contains("exactly 4 legs") ||
            source.contains("exactly 3 eyes") ||
            source.contains("exactly 4 eyes") ||
            source.contains("anatomical tail") ||
            source.contains("natural scales") ||
            source.contains("fine natural fur") ||
            source.contains("chimera") ||
            source.contains("nonstandard humanoid")
    }

    fun identityFragment(source: String): String {
        val lower = source.lowercase()
        val bits = linkedSetOf<String>()
        SKIN.find(lower)?.value?.let(bits::add)
        HAIR.find(lower)?.value?.let(bits::add)
        EYES.find(lower)?.value?.let(bits::add)
        FACE.find(lower)?.value?.let(bits::add)
        BUILD.find(lower)?.value?.let(bits::add)
        LIMB.findAll(lower).forEach { bits += it.value }
        if (lower.contains("anatomical tail")) bits += "visible anatomical tail"
        when {
            lower.contains("natural scales") -> bits += "natural scales covering the body"
            lower.contains("fine natural fur") -> bits += "fine natural fur covering the body"
            lower.contains("dense natural body hair") -> bits += "dense natural body hair covering"
        }
        return bits.joinToString(", ")
    }

    fun sanitize(prompt: String): String {
        var out = prompt
        REPLACEMENTS.forEach { (from, to) ->
            out = out.replace(from, to, ignoreCase = true)
        }
        return out.replace(Regex(",\\s*,+"), ", ").trim(',', ' ')
    }

    fun stripAnimalSubject(fragment: String): String = sanitize(fragment)

    private val SKIN = Regex(
        "fair skin|light olive skin|warm beige skin|olive skin|medium brown skin|deep brown skin",
    )
    private val HAIR = Regex(
        "(?:black|dark brown|chestnut brown|auburn|dark blonde|blonde) " +
            "(?:long straight|long wavy|shoulder-length wavy|chin-length bob|thick braided|" +
            "shoulder-length straight|short textured|short wavy|medium-length swept back|" +
            "close cropped|medium-length curly) hair",
    )
    private val EYES = Regex("(?:brown|dark brown|hazel|green|gray|blue) eyes")
    private val FACE = Regex("(?:oval|angular|round|heart-shaped|long|square) face")
    private val BUILD = Regex("(?:slender|lean|average|athletic|solid|broad) build")
    private val LIMB = Regex("exactly \\d+ (?:arms|legs|eyes)")

    private val REPLACEMENTS = listOf(
        "tamed dogs or herd animals living beside people, leashes, pens, feed piles and animals assisting daily work"
            to "domestication only as background props: leather straps, a taming post, hide bundles, no living animal in frame",
        "tamed dogs, animal pens, daily animal work"
            to "leather taming straps and a post in the background, no living animal in frame",
        "tamed dogs or herd animals living beside people"
            to "domestication gear stored at the edge of a human shelter",
        "animals assisting daily work"
            to "human work after animals have been penned out of frame",
        "sex in the animal pen margin of camp"
            to "sex inside the human sleeping shelter",
        "bodies marked by daily work with animals"
            to "work-worn human bodies",
        "tamed dogs" to "leather taming straps",
        "herd animals" to "herding tack stored aside",
        "animal pen margin" to "human shelter",
        "animal pens" to "a closed gate at the edge of frame",
        "animal pen" to "camp edge",
    )
}
