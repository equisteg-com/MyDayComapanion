package com.equisteg.mydaycompanion.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.equisteg.mydaycompanion.domain.tools.Speaker
import com.equisteg.mydaycompanion.domain.voice.SpokenReplies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** What the owner can see about spoken replies, and how to stop one. */
interface VoiceOutput {
    /** True from the moment text is accepted until the engine finishes, stops or fails. */
    val speaking: StateFlow<Boolean>

    /** Stops the reply being spoken, if any. */
    fun stop()

    /** Which engine and language the phone would use; for Settings. Starts the engine if needed. */
    suspend fun status(): TtsStatus
}

data class TtsStatus(val ready: Boolean, val engine: String?, val language: String?, val languageMissing: Boolean)

object SilentVoiceOutput : VoiceOutput {
    override val speaking: StateFlow<Boolean> = MutableStateFlow(false)
    override fun stop() = Unit
    override suspend fun status() = TtsStatus(ready = false, engine = null, language = null, languageMissing = false)
}

/**
 * The phone's own text-to-speech engine (offline voices when the phone has them; nothing is sent
 * by this app). One engine is kept while replies are being spoken and shut down after a minute of
 * quiet, so nothing stays bound in the background for owners who never use voice.
 *
 * Fixes over the first version: the engine is reused instead of being rebuilt (and its audio cut)
 * for every reply; start-up may take up to 8 seconds on slower phones (3 was too short on some);
 * the phone's language falls back to English when its voice data is missing instead of failing
 * silently; replies play as assistant speech with audio focus, so music ducks under them; and
 * [speaking] lets the voice conversation wait for the end of a reply before listening again.
 */
class AndroidSpeaker(context: Context) : Speaker, VoiceOutput {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null // main thread only
    private var ready: CompletableDeferred<Boolean>? = null
    private var engineReady = false
    private var focus: AudioFocusRequest? = null
    private var languageMissing = false
    private val utterances = AtomicInteger()
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private val idleShutdown = Runnable { shutdown() }

    override suspend fun speak(text: String): Boolean = withContext(Dispatchers.Main) {
        val clean = SpokenReplies.forSpeech(text)
        if (clean.isEmpty()) return@withContext true
        val engine = engine() ?: return@withContext false
        main.removeCallbacks(idleShutdown)
        val id = "mdc-${utterances.incrementAndGet()}"
        _speaking.value = true
        requestFocus()
        val queued = engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        if (!queued) finished()
        queued
    }

    override fun stop() {
        // At once when called on the main thread, so a reply queued right after it is not cut off.
        if (Looper.myLooper() == Looper.getMainLooper()) return stopNow()
        val id = latestId()
        main.post { if (id == latestId()) stopNow() }
    }

    private fun stopNow() {
        tts?.stop()
        finished()
    }

    override suspend fun status(): TtsStatus = withContext(Dispatchers.Main) {
        val engine = engine()
        main.removeCallbacks(idleShutdown)
        if (!_speaking.value) main.postDelayed(idleShutdown, IDLE_MILLIS)
        TtsStatus(
            ready = engine != null,
            engine = engine?.defaultEngine,
            language = engine?.voice?.locale?.displayName,
            languageMissing = languageMissing,
        )
    }

    /** Starts the engine once and waits for it; null when the phone has no working engine. */
    private suspend fun engine(): TextToSpeech? {
        tts?.let { existing -> if (engineReady) return existing }
        val waiting = ready?.takeIf { !it.isCompleted } ?: CompletableDeferred<Boolean>().also { d ->
            ready = d
            tts?.shutdown()
            tts = TextToSpeech(appContext) { status -> d.complete(status == TextToSpeech.SUCCESS) }
        }
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { waiting.await() } ?: false
        val engine = tts
        if (!ok || engine == null) {
            shutdown()
            return null
        }
        if (!engineReady) {
            configure(engine)
            engineReady = true
        }
        return engine
    }

    private fun configure(engine: TextToSpeech) {
        engine.setAudioAttributes(attributes)
        val wanted = Locale.getDefault()
        val result = engine.setLanguage(wanted)
        languageMissing = result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
        if (languageMissing) engine.setLanguage(Locale.ENGLISH)
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                }

                // Only the latest reply ends the speaking state; an older one cut off by QUEUE_FLUSH doesn't.
                override fun onDone(utteranceId: String?) {
                    main.post { if (utteranceId == latestId()) finished() }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    main.post { if (utteranceId == latestId()) finished() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { finished() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { finished() }
                }
            },
        )
    }

    private fun latestId() = "mdc-${utterances.get()}"

    private fun finished() {
        _speaking.value = false
        abandonFocus()
        main.removeCallbacks(idleShutdown)
        main.postDelayed(idleShutdown, IDLE_MILLIS)
    }

    private fun shutdown() {
        tts?.let { runCatching { it.stop() }; runCatching { it.shutdown() } }
        tts = null
        ready = null
        engineReady = false
        _speaking.value = false
        abandonFocus()
    }

    private fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
        focus = request
        audio.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        val request = focus ?: return
        focus = null
        audio.abandonAudioFocusRequest(request)
    }

    private companion object {
        const val INIT_TIMEOUT_MILLIS = 8_000L
        const val IDLE_MILLIS = 60_000L
        val attributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
