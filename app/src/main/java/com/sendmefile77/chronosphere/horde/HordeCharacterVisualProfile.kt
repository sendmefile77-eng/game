package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.people.BiologicalSex

data class HordeCharacterVisualProfile(
    val sex: BiologicalSex,
    val skinTone: String,
    val hairColor: String,
    val hairStyle: String,
    val eyeColor: String,
    val faceShape: String,
    val build: String,
) {
    val promptFragment: String
        get() = listOf(
            when (sex) {
                BiologicalSex.FEMALE -> "female"
                BiologicalSex.MALE -> "male"
            },
            skinTone,
            "$hairColor $hairStyle hair",
            "$eyeColor eyes",
            "$faceShape face",
            "$build build",
        ).joinToString(", ")

    val signature: String
        get() = listOf(sex.name, skinTone, hairColor, hairStyle, eyeColor, faceShape, build)
            .joinToString(":")

    companion object {
        private val skinTones = listOf(
            "fair skin",
            "light olive skin",
            "warm beige skin",
            "olive skin",
            "medium brown skin",
            "deep brown skin",
        )
        private val hairColors = listOf(
            "black",
            "dark brown",
            "chestnut brown",
            "auburn",
            "dark blonde",
            "blonde",
        )
        private val femaleHairStyles = listOf(
            "long straight",
            "long wavy",
            "shoulder-length wavy",
            "chin-length bob",
            "thick braided",
            "shoulder-length straight",
        )
        private val maleHairStyles = listOf(
            "short textured",
            "short wavy",
            "medium-length swept back",
            "close cropped",
            "shoulder-length straight",
            "medium-length curly",
        )
        private val eyeColors = listOf("brown", "dark brown", "hazel", "green", "gray", "blue")
        private val faceShapes = listOf("oval", "angular", "round", "heart-shaped", "long", "square")
        private val builds = listOf("slender", "lean", "average", "athletic", "solid", "broad")

        fun from(characterKey: String): HordeCharacterVisualProfile {
            require(characterKey.isNotBlank())
            val sex = BiologicalSex.fromStableKey(characterKey)
            val hairStyles = if (sex == BiologicalSex.FEMALE) femaleHairStyles else maleHairStyles
            return HordeCharacterVisualProfile(
                sex = sex,
                skinTone = pick(characterKey, "skin", skinTones),
                hairColor = pick(characterKey, "hair-color", hairColors),
                hairStyle = pick(characterKey, "hair-style", hairStyles),
                eyeColor = pick(characterKey, "eyes", eyeColors),
                faceShape = pick(characterKey, "face", faceShapes),
                build = pick(characterKey, "build", builds),
            )
        }

        private fun pick(key: String, salt: String, values: List<String>): String {
            var hash = -3750763034362895579L
            "$key:$salt".forEach { char ->
                hash = (hash xor char.code.toLong()) * 1099511628211L
            }
            val positive = hash and Long.MAX_VALUE
            return values[(positive % values.size.toLong()).toInt()]
        }
    }
}
