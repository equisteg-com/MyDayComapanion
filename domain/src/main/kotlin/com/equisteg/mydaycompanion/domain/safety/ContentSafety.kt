package com.equisteg.mydaycompanion.domain.safety

/**
 * Heuristic detector for text that tries to instruct the assistant (prompt injection) or to
 * plant rules in memory (memory poisoning). It is a defence-in-depth signal only: the real
 * protection is that model output can never authorise a tool call by itself (see
 * ToolAuthorizationPolicy).
 */
object ContentSafety {
    /** Phrases aimed at the assistant itself. Rare in genuine to-do items, so tasks containing them are dropped. */
    private val assistantDirected = listOf(
        "ignore (all |any )?(of )?(the )?(previous|prior|above|earlier|your) (instructions|rules|prompts?)",
        "disregard (all |any )?(the )?(previous|prior|above|earlier|your) ",
        "\\byou are now\\b",
        "system prompt",
        "developer mode",
        "(grant|give) (yourself|me|the assistant|this app) (full |all )?(access|permissions?)",
        "(disable|turn off|skip|bypass) (the |all )?(confirmations?|policy|policies|security|permissions?|audit)",
        "always (allow|approve|confirm) ",
        "without (asking|confirmation|confirming)",
        "<\\s*/?\\s*(system|tool|assistant)\\s*>",
        "\\btool_call\\b|\\bfunction_call\\b",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** Extra patterns that are fine in a task title but must never become a remembered rule. */
    private val memoryOnly = listOf(
        "delete (all|every|everything)",
        "wipe (all|everything|my data|the data)",
        "(send|upload|post|forward|share) (all |my )?(data|tasks|memories|contacts|messages|passwords?)",
        "https?://",
        "\\bpassword\\b|\\bpin\\b|\\botp\\b|\\bcvv\\b",
        "\\b(permission|authori[sz]ation|confirmation)s?\\b",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    fun looksLikeInstructionToAssistant(text: String): Boolean = assistantDirected.any { it.containsMatchIn(text) }

    fun unsafeForMemory(text: String): Boolean =
        looksLikeInstructionToAssistant(text) || memoryOnly.any { it.containsMatchIn(text) }
}
