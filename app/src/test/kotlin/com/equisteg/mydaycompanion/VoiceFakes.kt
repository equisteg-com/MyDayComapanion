package com.equisteg.mydaycompanion

import com.equisteg.mydaycompanion.voice.SpeechEngine
import com.equisteg.mydaycompanion.voice.SpeechError
import com.equisteg.mydaycompanion.voice.SpeechInput
import com.equisteg.mydaycompanion.voice.TtsStatus
import com.equisteg.mydaycompanion.voice.VoiceOutput
import kotlinx.coroutines.flow.MutableStateFlow

/** A microphone the test speaks into. */
class FakeSpeechInput : SpeechInput {
    var engine = SpeechEngine.ON_DEVICE
    var listener: SpeechInput.Listener? = null
        private set
    var starts = 0
        private set
    var stops = 0
        private set

    val listening: Boolean get() = listener != null

    override fun engine() = engine

    override fun start(listener: SpeechInput.Listener) {
        if (engine == SpeechEngine.NONE) return listener.onError(SpeechError.UNAVAILABLE)
        starts++
        this.listener = listener
        listener.onReady()
    }

    override fun stop() {
        stops++
    }

    override fun cancel() {
        listener = null
    }

    /** Words heard so far, as a recogniser reports them while the owner is still talking. */
    fun partial(text: String) {
        listener!!.onLevel(0.6f)
        listener!!.onPartial(text)
    }

    /** The owner says [text] and stops talking. */
    fun say(text: String) {
        val l = listener ?: error("not listening")
        listener = null
        l.onPartial(text)
        l.onFinal(text)
    }

    fun fail(error: SpeechError) {
        val l = listener ?: error("not listening")
        listener = null
        l.onError(error)
    }
}

class FakeVoiceOutput : VoiceOutput {
    override val speaking = MutableStateFlow(false)
    var stops = 0
    var status = TtsStatus(ready = true, engine = "com.google.android.tts", language = "English (India)", languageMissing = false)
    override fun stop() {
        stops++
        speaking.value = false
    }
    override suspend fun status() = status
}
