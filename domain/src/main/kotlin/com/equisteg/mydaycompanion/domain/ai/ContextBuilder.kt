package com.equisteg.mydaycompanion.domain.ai

import com.equisteg.mydaycompanion.domain.memory.MemoryItem
import com.equisteg.mydaycompanion.domain.memory.MemorySensitivity
import com.equisteg.mydaycompanion.domain.memory.MemoryStatus
import com.equisteg.mydaycompanion.domain.safety.InputNormalizer
import java.time.LocalDate

/**
 * Builds the minimal context a model needs. Sensitive memories are never included, only active
 * (user-approved) memories are used, and everything user-derived is passed as untrusted data.
 */
class ContextBuilder(
    private val maxMemories: Int = 10,
) {
    fun taskExtraction(userText: String, today: LocalDate, memories: List<MemoryItem>): AiRequest {
        val prompt = PromptRegistry.TASK_EXTRACTION
        val usable = memories
            .filter { it.status == MemoryStatus.ACTIVE && it.deletedAt == null && it.sensitivity != MemorySensitivity.SENSITIVE }
            .take(maxMemories)
        return AiRequest(
            task = AiTask.TASK_EXTRACTION,
            promptId = prompt.id,
            promptVersion = prompt.version,
            instructions = prompt.instructions,
            untrustedInput = InputNormalizer.normalize(userText),
            context = buildMap {
                put("today", today.toString())
                if (usable.isNotEmpty()) put("preferences", usable.joinToString("\n") { "- " + it.content })
            },
        )
    }
}
