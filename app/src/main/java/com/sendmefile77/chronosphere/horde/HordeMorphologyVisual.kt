package com.sendmefile77.chronosphere.horde

data class HordeMorphologyVisual(
    val promptFragment: String,
    val signature: String,
) {
    companion object {
        fun from(tags: Set<String>, numeric: Map<String, Double>): HordeMorphologyVisual {
            val normalized = tags.map { it.lowercase() }.toSet()
            val arms = intTag(normalized, "arms:")
            val legs = intTag(normalized, "legs:")
            val eyes = intTag(normalized, "eyes:")
            val covering = valueTag(normalized, "covering:")
            val posture = valueTag(normalized, "posture:")

            val fragments = buildList {
                arms?.takeIf { it != 2 }?.let { add("exactly $it arms") }
                legs?.takeIf { it != 2 }?.let { add("exactly $it legs") }
                eyes?.takeIf { it != 2 }?.let { add("exactly $it eyes") }
                if ("tail" in normalized) add("clearly visible anatomical tail")
                when (covering) {
                    "dense_hair" -> add("dense natural body hair covering")
                    "fine_fur" -> add("fine natural fur covering the body")
                    "scales" -> add("natural scales covering the body")
                }
                when (posture) {
                    "semi_upright" -> add("semi-upright posture")
                    "upright" -> if (arms != null || legs != null || eyes != null) add("upright posture")
                }
                numeric["morph_cranial"]?.let { value ->
                    when {
                        value >= 1.18 -> add("enlarged cranium")
                        value <= 0.82 -> add("compact cranium")
                    }
                }
                numeric["morph_eye_size"]?.let { value ->
                    when {
                        value >= 0.68 -> add("large eyes")
                        value <= 0.32 -> add("small eyes")
                    }
                }
                numeric["morph_limbs"]?.let { value ->
                    when {
                        value >= 1.18 -> add("elongated limbs")
                        value <= 0.82 -> add("short compact limbs")
                    }
                }
                numeric["morph_height"]?.let { value ->
                    when {
                        value >= 1.18 -> add("unusually tall stature")
                        value <= 0.82 -> add("unusually short stature")
                    }
                }
                numeric["morph_hair"]?.takeIf { covering == null || covering == "bare_skin" }?.let { value ->
                    when {
                        value >= 0.70 -> add("pronounced body hair")
                        value <= 0.18 -> add("very sparse body hair")
                    }
                }
            }

            val signatureParts = buildList {
                addAll(normalized.filter {
                    it.startsWith("arms:") || it.startsWith("legs:") || it.startsWith("eyes:") ||
                        it == "tail" || it.startsWith("covering:") || it.startsWith("posture:")
                }.sorted())
                listOf("morph_cranial", "morph_eye_size", "morph_limbs", "morph_height", "morph_hair").forEach { key ->
                    numeric[key]?.let { add("$key:${bucket(key, it)}") }
                }
            }

            return HordeMorphologyVisual(
                promptFragment = fragments.joinToString(", "),
                signature = if (signatureParts.isEmpty()) "baseline" else signatureParts.joinToString(";"),
            )
        }

        private fun intTag(tags: Set<String>, prefix: String): Int? =
            tags.firstOrNull { it.startsWith(prefix) }?.substringAfter(prefix)?.toIntOrNull()

        private fun valueTag(tags: Set<String>, prefix: String): String? =
            tags.firstOrNull { it.startsWith(prefix) }?.substringAfter(prefix)?.takeIf { it.isNotBlank() }

        private fun bucket(key: String, value: Double): String = when (key) {
            "morph_eye_size", "morph_hair" -> when {
                value <= 0.32 -> "low"
                value >= 0.68 -> "high"
                else -> "mid"
            }
            else -> when {
                value <= 0.82 -> "low"
                value >= 1.18 -> "high"
                else -> "mid"
            }
        }
    }
}
