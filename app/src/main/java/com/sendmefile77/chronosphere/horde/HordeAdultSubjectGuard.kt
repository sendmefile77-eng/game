package com.sendmefile77.chronosphere.horde

/**
 * Adult-path adapter: historical domestication language from the SFW layer
 * ("tamed dogs", pens, herds) must never become the picture's subject.
 *
 * Does not rewrite [HordeDecisionVisualCue] / [LocalDreamMaterialCueBridge].
 * It only sanitizes already-assembled adult/portrait prompts.
 */
internal object HordeAdultSubjectGuard {
    const val HUMAN_LOCK =
        "solo adult human as the only subject, complete visible person filling the frame, " +
            "a human adult body, not an animal, not an empty room"

    const val EROTIC_LOCK =
        "explicit adult nude human, visible breasts or chest, visible genitals, erotic body language"

    val NEGATIVES = listOf(
        "dog",
        "puppy",
        "wolf",
        "fox",
        "cat",
        "horse",
        "cow",
        "goat",
        "sheep",
        "livestock as subject",
        "animal only",
        "animal as the main subject",
        "no humans",
        "no person",
        "empty room",
        "empty interior",
        "vacant tent",
        "still life",
        "furry",
        "anthro",
        "bestiality",
        "zoophilia",
        "animal focus",
    )

    fun sanitize(prompt: String): String {
        var out = prompt
        REPLACEMENTS.forEach { (from, to) ->
            out = out.replace(from, to, ignoreCase = true)
        }
        return out.replace(Regex(",\\s*,+"), ", ").trim(',', ' ')
    }

    fun stripAnimalSubject(fragment: String): String = sanitize(fragment)

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
