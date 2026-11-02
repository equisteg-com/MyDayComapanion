package com.equisteg.mydaycompanion

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.VoicePhase
import com.equisteg.mydaycompanion.voice.SpeechEngine
import com.equisteg.mydaycompanion.voice.SpeechError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Voice (F6): dictation into the input (voice-to-text), spoken answers (text-to-voice) and the
 * hands-free conversation (voice-to-voice), end to end through the real view model, orchestrator,
 * planner and `voice.speak` tool. Only the microphone and the speaker are fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class VoiceUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val graph = TestGraph()
    private val services = TestServices(graph)
    private val viewModel = MainViewModel(services, Dispatchers.Unconfined)
    private val mic get() = services.speechInput
    private val spoken get() = graph.phone.spoken
    private val tomorrow = LocalDate.of(2026, 10, 7)

    @Before
    fun allowMicrophone() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    /** Runs the main thread until [condition] holds; the view model works there. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        repeat(400) {
            if (condition()) return
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue(condition(), what)
    }

    @Test
    fun `talk mode plans a spoken day, reads it back and adds it on a spoken yes`() {
        compose.setContent { AppNavigation(viewModel) }
        compose.onNodeWithTag("talk").performScrollTo().performClick()
        waitFor("listening") { mic.listening }
        assertTrue(compose.onAllNodes(hasTestTag("voice-session")).fetchSemanticsNodes().isNotEmpty(), "conversation screen open")

        mic.say("tomorrow I have a meeting at 10 a.m. then call mom and go to the gym at 6 pm")
        waitFor("plan read back") { spoken.isNotEmpty() }
        assertEquals("Tomorrow: Meeting at 10 AM, Call mom at 11 AM and Go to the gym at 6 PM. Shall I add this to your day?", spoken.single())
        assertEquals(0, runBlocking { graph.tasks.count() }, "nothing is saved before the owner says yes")
        waitFor("listening for the answer") { mic.listening }
        compose.onNodeWithTag("voice-yes").assertTextContains("Add it")

        mic.say("yes please")
        waitFor("saved") { runBlocking { graph.tasks.count() } == 2 }
        waitFor("confirmation spoken") { spoken.size == 2 }
        assertEquals("Added to your day.", spoken.last())
        val saved = runBlocking { graph.plans.get(tomorrow) }!!
        assertEquals(listOf("Meeting", "Call mom", "Go to the gym"), saved.blocks.map { it.title })

        waitFor("listening again") { mic.listening }
        mic.say("stop")
        waitFor("closed") { viewModel.state.value.voice == null }
        assertTrue(compose.onAllNodes(hasTestTag("voice-session")).fetchSemanticsNodes().isEmpty())
        assertFalse(mic.listening, "the microphone is off once the conversation ends")
    }

    @Test
    fun `a yes with more words is a new request and never approves the plan`() {
        viewModel.onVoiceOpen()
        mic.say("tomorrow call Raj at 4 pm")
        waitFor("proposal") { viewModel.state.value.proposal != null && mic.listening }
        mic.say("yes and buy milk")
        waitFor("new proposal") { viewModel.state.value.proposal?.extraction?.tasks?.any { it.title == "Buy milk" } == true }
        assertEquals(0, runBlocking { graph.tasks.count() })
    }

    @Test
    fun `saying no drops the plan`() {
        viewModel.onVoiceOpen()
        mic.say("call the bank at 3 pm")
        waitFor("proposal") { viewModel.state.value.proposal != null && mic.listening }
        mic.say("no")
        waitFor("dropped") { viewModel.state.value.proposal == null && spoken.size == 2 }
        assertEquals("Okay, I won't add it.", spoken.last())
        assertEquals(0, runBlocking { graph.tasks.count() })
    }

    @Test
    fun `questions are answered out loud and the conversation keeps listening`() {
        viewModel.onVoiceOpen()
        mic.say("what's next?")
        waitFor("answer spoken") { spoken.isNotEmpty() && mic.listening }
        assertEquals(viewModel.state.value.chat.last().text, spoken.single())
        assertEquals(2, mic.starts)
    }

    @Test
    fun `silence twice pauses, and a tap on the circle listens again`() {
        viewModel.onVoiceOpen()
        mic.fail(SpeechError.NO_MATCH)
        waitFor("listens once more") { mic.listening }
        mic.fail(SpeechError.NO_MATCH)
        waitFor("paused") { viewModel.state.value.voice?.phase == VoicePhase.PAUSED }
        assertFalse(mic.listening)
        viewModel.onVoiceTap()
        waitFor("listening") { mic.listening }
    }

    @Test
    fun `the conversation closes and the mic turns off when the app leaves the screen`() {
        viewModel.onVoiceOpen()
        waitFor("listening") { mic.listening }
        viewModel.onAppBackground()
        assertNull(viewModel.state.value.voice)
        assertFalse(mic.listening)
    }

    @Test
    fun `the input mic shows words as they are spoken and plans nothing until Plan it`() {
        compose.setContent { AppNavigation(viewModel) }
        compose.onNodeWithTag("voice").performClick()
        waitFor("dictating") { mic.listening && viewModel.state.value.dictating }
        mic.partial("call mom")
        waitFor("live words") { viewModel.state.value.input == "call mom" }
        mic.say("call mom at 5 pm")
        waitFor("final words") { viewModel.state.value.input == "call mom at 5 pm" && !viewModel.state.value.dictating }
        compose.onNodeWithTag("input").assertTextContains("call mom at 5 pm")
        assertNull(viewModel.state.value.proposal, "dictation only fills the box")

        compose.onNodeWithTag("submit").performClick()
        waitFor("proposal") { viewModel.state.value.proposal != null }
    }

    @Test
    fun `speak replies reads answers and plans aloud`() {
        runBlocking { graph.preferences.set(UserPreferences(onboardingDone = true, speechOutputEnabled = true)) }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        waitFor("preferences") { vm.state.value.preferencesLoaded }
        vm.onAsk("What's next?")
        waitFor("answer spoken") { spoken.isNotEmpty() }
        vm.onInputChange("tomorrow gym at 7 am")
        vm.onSubmit()
        waitFor("plan spoken") { spoken.size == 2 }
        assertEquals("Tomorrow: Gym at 7 AM. Shall I add this to your day?", spoken.last())
        vm.onAcceptProposal()
        waitFor("added spoken") { spoken.size == 3 }
        assertEquals("Added to your day.", spoken.last())
    }

    @Test
    fun `the speaker on an answer reads just that answer`() {
        compose.setContent { AppNavigation(viewModel) }
        viewModel.onAsk("What's left?")
        waitFor("answer") { viewModel.state.value.chat.size == 2 }
        compose.onNodeWithTag("read-aloud-1").performClick()
        waitFor("spoken") { spoken.isNotEmpty() }
        assertEquals(viewModel.state.value.chat.last().text, spoken.single())
    }

    @Test
    fun `with no recogniser the mic says what to do instead of failing silently`() {
        mic.engine = SpeechEngine.NONE
        compose.setContent { AppNavigation(viewModel) }
        compose.onNodeWithTag("talk").performScrollTo().performClick()
        waitFor("message") { viewModel.state.value.message != null }
        assertTrue("speech" in viewModel.state.value.message!!.lowercase(), viewModel.state.value.message!!)
        assertNull(viewModel.state.value.voice)
        assertEquals(0, mic.starts)
    }

    @Test
    fun `test voice says which engine speaks and listens`() {
        viewModel.onTestVoice()
        waitFor("checked") { viewModel.state.value.voiceCheck != null }
        assertEquals("Speaking: works (English (India)). Listening: on this phone.", viewModel.state.value.voiceCheck)
        assertEquals("Hi! This is how I'll read your day.", spoken.single())
    }

    @Test
    fun `the phone speech service switch is kept on the phone`() {
        viewModel.onPhoneSpeechService(false)
        assertFalse(services.deviceSettings.phoneSpeechService)
        assertFalse(viewModel.state.value.phoneSpeechService)
    }

    @Test
    fun `spoken answers never go into History`() {
        viewModel.onVoiceOpen()
        mic.say("tomorrow call the clinic at 4 pm")
        waitFor("spoken") { spoken.isNotEmpty() }
        val audit = runBlocking { graph.audit.all() }.joinToString { it.toString() }
        assertFalse("clinic" in audit.lowercase(), "spoken text is never audited")
        assertTrue(runBlocking { graph.audit.all() }.any { it.action == "voice.speak" })
    }
}
