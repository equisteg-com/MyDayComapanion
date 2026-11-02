package com.equisteg.mydaycompanion.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Which recogniser turns speech into text on this phone. */
enum class SpeechEngine {
    /** Android's on-device recogniser (Android 12+): audio never leaves the phone. */
    ON_DEVICE,

    /** The phone's own speech service (usually Google's), asked to work offline. The owner can switch it off. */
    PHONE_SERVICE,

    /** Nothing usable: no recogniser, or only the phone service and the owner switched it off. */
    NONE,
}

enum class SpeechError { NO_MATCH, NO_PERMISSION, BUSY, UNAVAILABLE, OTHER }

/** Listens for one utterance at a time. Callbacks arrive on the main thread. */
interface SpeechInput {
    fun engine(): SpeechEngine

    /** Starts listening; ignored while already listening. */
    fun start(listener: Listener)

    /** Stops listening and delivers what was heard so far. */
    fun stop()

    /** Stops listening and drops what was heard; no callback follows. */
    fun cancel()

    interface Listener {
        fun onReady() {}
        fun onPartial(text: String) {}

        /** Microphone loudness, 0 to 1, a few times a second; drives the animation. */
        fun onLevel(level: Float) {}
        fun onFinal(text: String)
        fun onError(error: SpeechError)
    }
}

/** For builds or tests with no speech input. */
object NoSpeechInput : SpeechInput {
    override fun engine() = SpeechEngine.NONE
    override fun start(listener: SpeechInput.Listener) = listener.onError(SpeechError.UNAVAILABLE)
    override fun stop() = Unit
    override fun cancel() = Unit
}

/**
 * Android's SpeechRecognizer, press-to-talk only: the microphone is on between start and the end
 * of one utterance, never in the background and never with a wake word.
 *
 * Order: the on-device recogniser first. When the phone has none (most non-Pixel phones, including
 * vivo / iQOO), or it can't do the phone's language yet, the phone's own speech service is used if
 * the owner allows it, asked to prefer offline. This app has no internet permission either way.
 *
 * Things real phones do that this handles: "recogniser busy" after a quick second tap (the
 * recogniser is rebuilt and tried once more), an error after results or results twice (only the
 * first ending counts), empty results (reported as no match), and an on-device recogniser that is
 * listed but has no language pack (it asks the phone to download the pack, then uses the phone
 * service for now).
 */
class AndroidSpeechInput(
    context: Context,
    private val phoneServiceAllowed: () -> Boolean,
) : SpeechInput {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var recognizerEngine = SpeechEngine.NONE
    private var session = 0
    private var onDeviceFailed = false
    private var downloadAsked = false
    private val main = Handler(Looper.getMainLooper())

    override fun engine(): SpeechEngine = when {
        !onDeviceFailed && onDeviceAvailable() -> SpeechEngine.ON_DEVICE
        phoneServiceAllowed() && SpeechRecognizer.isRecognitionAvailable(appContext) -> SpeechEngine.PHONE_SERVICE
        else -> SpeechEngine.NONE
    }

    override fun start(listener: SpeechInput.Listener) = start(listener, retried = false)

    private fun start(listener: SpeechInput.Listener, retried: Boolean) {
        val engine = engine()
        if (engine == SpeechEngine.NONE) return listener.onError(SpeechError.UNAVAILABLE)
        val r = recognizerFor(engine) ?: return listener.onError(SpeechError.UNAVAILABLE)
        val id = ++session
        var heard = false
        var finished = false
        fun finish(block: () -> Unit) {
            if (finished || id != session) return
            finished = true
            block()
        }
        r.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (id == session) listener.onReady() }
                override fun onBeginningOfSpeech() { heard = true }
                override fun onRmsChanged(rmsdB: Float) { if (id == session && !finished) listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (id == session && !finished) listener.onLevel(0f) }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!text.isNullOrEmpty() && id == session && !finished) { heard = true; listener.onPartial(text) }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    finish { if (text.isNullOrEmpty()) listener.onError(SpeechError.NO_MATCH) else listener.onFinal(text) }
                }
                override fun onError(error: Int) {
                    if (finished || id != session) return
                    val languageProblem = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE ||
                        error == ERROR_SERVER_DISCONNECTED || error == SpeechRecognizer.ERROR_SERVER
                    when {
                        // The on-device recogniser exists but can't do this language (yet): ask for the pack, use the phone service now.
                        engine == SpeechEngine.ON_DEVICE && languageProblem && !heard -> {
                            finished = true
                            onDeviceFailed = true
                            askForLanguagePack()
                            // Not from inside the dying recogniser's own callback.
                            main.post {
                                release()
                                if (engine() != SpeechEngine.NONE) start(listener, retried = true) else listener.onError(SpeechError.UNAVAILABLE)
                            }
                        }
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && !retried -> {
                            finished = true
                            main.post {
                                release()
                                start(listener, retried = true)
                            }
                        }
                        else -> finish { listener.onError(map(error)) }
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        try {
            r.startListening(intent(engine))
        } catch (e: RuntimeException) {
            release()
            finish { listener.onError(SpeechError.OTHER) }
        }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        session++
        recognizer?.cancel()
    }

    /** Frees the recogniser; the next start makes a new one. */
    fun release() {
        session++
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
        recognizerEngine = SpeechEngine.NONE
    }

    private fun recognizerFor(engine: SpeechEngine): SpeechRecognizer? {
        if (recognizer != null && recognizerEngine == engine) return recognizer
        release()
        val r = try {
            if (engine == SpeechEngine.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (e: RuntimeException) {
            null
        }
        recognizer = r
        recognizerEngine = if (r == null) SpeechEngine.NONE else engine
        return r
    }

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)

    /** Android 13+: asks the phone to fetch the on-device language pack, once per app start. */
    private fun askForLanguagePack() {
        if (downloadAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        downloadAsked = true
        runCatching {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            r.triggerModelDownload(intent(SpeechEngine.ON_DEVICE))
            r.destroy()
        }
    }

    private fun intent(engine: SpeechEngine) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        // Ask the phone service to stay on the phone; it is the on-device recogniser's default anyway.
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // A natural pause ends the turn, as in other voice assistants; ignored by recognisers that don't support it.
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (engine == SpeechEngine.ON_DEVICE) 1_500L else 1_800L)

    private fun map(error: Int): SpeechError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_MATCH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.NO_PERMISSION
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.BUSY
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE, ERROR_SERVER_DISCONNECTED -> SpeechError.UNAVAILABLE
        else -> SpeechError.OTHER
    }

    private companion object {
        // Added in Android 12; spelled out so the app also compiles the mapping on older API levels.
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
