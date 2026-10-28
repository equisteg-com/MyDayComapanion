package com.equisteg.mydaycompanion.domain.safety

/** Bounds and cleans free text before it reaches any parser or model. */
object InputNormalizer {
    const val MAX_INPUT_CHARS = 2_000

    private val controlChars = Regex("[\\p{Cc}&&[^\\n\\t]]")
    private val whitespace = Regex("[ \\t]+")

    fun normalize(text: String): String = text
        .replace(controlChars, " ")
        .replace(whitespace, " ")
        .trim()
        .take(MAX_INPUT_CHARS)
}
