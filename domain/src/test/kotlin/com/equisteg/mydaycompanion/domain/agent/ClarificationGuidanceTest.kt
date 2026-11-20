package com.equisteg.mydaycompanion.domain.agent

import kotlin.test.Test
import kotlin.test.assertTrue

class ClarificationGuidanceTest {
    @Test fun `moving a task names the missing details`() {
        val advice = ClarificationGuidance.forInput("Move the report")
        assertTrue(advice.contains("exact saved task"))
        assertTrue(advice.contains("Friday"))
    }

    @Test fun `unclear meetings ask for start time and duration`() {
        val advice = ClarificationGuidance.forInput("appointment")
        assertTrue(advice.contains("start time"))
        assertTrue(advice.contains("duration"))
    }

    @Test fun `unclear time provides a concrete example`() {
        assertTrue(ClarificationGuidance.forInput("call 2").contains("4pm"))
    }

    @Test fun `missing details provide an editable example`() {
        assertTrue(ClarificationGuidance.forInput("things").contains("45 minutes"))
    }
}
