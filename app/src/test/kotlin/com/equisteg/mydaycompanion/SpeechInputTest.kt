package com.equisteg.mydaycompanion

import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.equisteg.mydaycompanion.voice.AndroidSpeechInput
import com.equisteg.mydaycompanion.voice.SpeechEngine
import com.equisteg.mydaycompanion.voice.SpeechError
import com.equisteg.mydaycompanion.voice.SpeechInput
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real Android speech input against Robolectric's SpeechRecognizer: engine choice and the quirks real phones have. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpeechInputTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var phoneServiceAllowed = true
    private val input = AndroidSpeechInput(app) { phoneServiceAllowed }

    private val events = mutableListOf<String>()
    private val listener = object : SpeechInput.Listener {
        override fun onPartial(text: String) { events += "partial:$text" }
        override fun onFinal(text: String) { events += "final:$text" }
        override fun onError(error: SpeechError) { events += "error:$error" }
    }

    @After
    fun reset() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        input.release()
    }

    private fun latest() = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    private fun results(text: String?) = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, if (text == null) arrayListOf() else arrayListOf(text)) }

    /** Makes the phone look like it has a speech service (as phones with Google's speech services do). */
    private fun installPhoneService() {
        val component = ComponentName("com.google.android.tts", "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService")
        val pm = shadowOf(app.packageManager)
        pm.addServiceIfNotPresent(component)
        pm.addIntentFilterForService(component, IntentFilter(RecognitionService.SERVICE_INTERFACE))
    }

    @Test
    fun `the on-device recogniser is used first`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        assertEquals(SpeechEngine.ON_DEVICE, input.engine())
    }

    @Test
    fun `without an on-device recogniser the phone service is used only when allowed`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        installPhoneService()
        assertEquals(SpeechEngine.PHONE_SERVICE, input.engine())
        phoneServiceAllowed = false
        assertEquals(SpeechEngine.NONE, input.engine(), "switched off: no fallback at all")
    }

    @Test
    fun `nothing to listen with is reported, not silently ignored`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        phoneServiceAllowed = false
        input.start(listener)
        assertEquals(listOf("error:UNAVAILABLE"), events)
    }

    @Test
    fun `live words, then the final text, once`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        input.start(listener)
        val r = latest()
        val intent = r.lastRecognizerIntent
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false), "asks for live words")
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false), "asks to stay offline")
        r.triggerOnPartialResults(results("call"))
        r.triggerOnResults(results("call mom"))
        r.triggerOnError(SpeechRecognizer.ERROR_CLIENT) // some phones send an error after the results
        assertEquals(listOf("partial:call", "final:call mom"), events)
    }

    @Test
    fun `empty results count as not heard`() {
        input.start(listener)
        latest().triggerOnResults(results(null))
        assertEquals(listOf("error:NO_MATCH"), events)
    }

    @Test
    fun `an on-device recogniser without the language falls back to the phone service`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        installPhoneService()
        input.start(listener)
        latest().triggerOnError(12) // ERROR_LANGUAGE_NOT_SUPPORTED
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SpeechEngine.PHONE_SERVICE, input.engine())
        latest().triggerOnResults(results("buy milk"))
        assertEquals(listOf("final:buy milk"), events)
    }

    @Test
    fun `a busy recogniser is rebuilt and tried once more`() {
        input.start(listener)
        val first = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        shadowOf(first).triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(ShadowSpeechRecognizer.getLatestSpeechRecognizer() !== first, "a new recogniser")
        latest().triggerOnResults(results("gym at 7"))
        assertEquals(listOf("final:gym at 7"), events)
    }

    @Test
    fun `an on-device recogniser that fails before it is ready falls back to the phone service`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        installPhoneService()
        input.start(listener)
        latest().triggerOnError(SpeechRecognizer.ERROR_CLIENT) // listed, but can't bind or has no model
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SpeechEngine.PHONE_SERVICE, input.engine())
        latest().triggerOnResults(results("buy milk"))
        assertEquals(listOf("final:buy milk"), events)
    }

    @Test
    fun `a retry waiting to run is dropped when listening is cancelled`() {
        input.start(listener)
        val first = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        shadowOf(first).triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        input.cancel() // the conversation closed, or the app left the screen
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(ShadowSpeechRecognizer.getLatestSpeechRecognizer() === first, "the microphone is not opened again")
        assertEquals(emptyList(), events)
    }
}
