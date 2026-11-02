package com.equisteg.mydaycompanion.domain.voice

import com.equisteg.mydaycompanion.domain.agent.AgentProposal
import com.equisteg.mydaycompanion.domain.ai.RuleBasedTaskExtractor
import com.equisteg.mydaycompanion.domain.ai.text.ClauseSplitter
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VoiceTest {
    private val today = LocalDate.of(2026, 10, 6)

    @Test
    fun `short spoken answers are read as yes, no or stop`() {
        listOf("Yes", "yeah.", "OK", "okay add it", "Sounds good!", "go ahead please", "yes please", "Add them", "do it, thanks").forEach {
            assertEquals(VoiceIntent.CONFIRM, VoiceIntents.parse(it), it)
        }
        listOf("no", "Nope", "cancel", "never mind", "don't add it", "no thanks").forEach {
            assertEquals(VoiceIntent.CANCEL, VoiceIntents.parse(it), it)
        }
        listOf("stop", "Bye!", "that's all", "thank you that's all", "okay stop", "I'm done").forEach {
            assertEquals(VoiceIntent.STOP, VoiceIntents.parse(it), it)
        }
    }

    @Test
    fun `a yes with more words is a new request, never an approval`() {
        listOf("yes and call mom at 5", "yes delete everything", "add it and also buy milk", "no I meant tomorrow", "call Raj", "").forEach {
            assertEquals(VoiceIntent.OTHER, VoiceIntents.parse(it), it)
        }
    }

    @Test
    fun `the plan is read back with times in order and a question`() = runTest {
        val p = TestGraph().orchestrator.propose("tomorrow I have a meeting at 10 a.m. then call mom and go to the gym at 6 pm")
        assertIs<AgentProposal.Ready>(p)
        val spoken = SpokenReplies.proposal(p, today)
        assertEquals("Tomorrow: Meeting at 10 AM, Call mom at 11 AM and Go to the gym at 6 PM. Shall I add this to your day?", spoken)
    }

    @Test
    fun `tasks that are already saved are not read out`() = runTest {
        val g = TestGraph()
        g.tasks.upsert(listOf(com.equisteg.mydaycompanion.domain.model.Task("old", "Clinic results", com.equisteg.mydaycompanion.domain.model.Priority.MEDIUM, 30, today.plusDays(1), personal = true, createdAt = g.clock.instant())))
        val p = g.orchestrator.propose("tomorrow call Raj at 4 pm")
        assertIs<AgentProposal.Ready>(p)
        val spoken = SpokenReplies.proposal(p, today)
        assertTrue("Call Raj at 4 PM" in spoken, spoken)
        assertFalse("Clinic" in spoken, spoken)
    }

    @Test
    fun `times are spoken the way people say them`() {
        assertEquals("9 AM", SpokenReplies.time(LocalTime.of(9, 0)))
        assertEquals("6:30 PM", SpokenReplies.time(LocalTime.of(18, 30)))
        assertEquals("noon", SpokenReplies.time(LocalTime.NOON))
        assertEquals("12:15 AM", SpokenReplies.time(LocalTime.of(0, 15)))
    }

    @Test
    fun `text for speech drops emoji and symbols and stays short`() {
        assertEquals("Added to your day.", SpokenReplies.forSpeech("✅ Added to your day. 🎉"))
        assertEquals("Next: Call Raj", SpokenReplies.forSpeech("• Next: **Call Raj**"))
        val long = "This is a sentence. ".repeat(100)
        assertTrue(SpokenReplies.forSpeech(long).length <= 600)
        assertTrue(SpokenReplies.forSpeech(long).endsWith("."))
    }

    @Test
    fun `spoken number words become times and lengths`() {
        val x = RuleBasedTaskExtractor().extract("call the bank at three thirty p.m. and study for forty five minutes", today, LocalTime.of(7, 0))
        assertEquals(listOf("15:30"), x.fixedEvents.map { it.start })
        assertEquals(listOf(45), x.tasks.map { it.durationMinutes })
        val y = RuleBasedTaskExtractor().extract("gym at seven in the morning and one task to finish the deck", today, LocalTime.of(5, 0))
        assertEquals(listOf("07:00"), y.fixedEvents.map { it.start })
    }

    @Test
    fun `then links a step to the one before it`() {
        val c = ClauseSplitter.splitLinked("meeting at 10 then call mom and buy milk")
        assertEquals(listOf("meeting at 10", "call mom", "buy milk"), c.map { it.text })
        assertEquals(listOf(false, true, false), c.map { it.afterPrevious })
    }

    @Test
    fun `run-on speech without commas is split into items`() {
        assertEquals(
            listOf("doctor's appointment at 11:30 a.m.", "pick up kids at 3:30 p.m.", "cook dinner at 7"),
            ClauseSplitter.split("doctor's appointment at 11:30 a.m. pick up kids at 3:30 p.m. and cook dinner at 7"),
        )
        assertEquals(listOf("meet Tom at 3 tomorrow"), ClauseSplitter.split("meet Tom at 3 tomorrow"), "a date after a time stays with its item")
        assertEquals(listOf("call mom at 5 for 10 minutes"), ClauseSplitter.split("call mom at 5 for 10 minutes"))
    }
}
