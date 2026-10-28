package com.equisteg.mydaycompanion.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat

/**
 * Press-to-talk (build doc F6). The microphone is used only between the tap and the end of the
 * sentence; there is no wake word and nothing listens in the background. Only the phone's
 * on-device recogniser is used, so speech never leaves the phone; there is no cloud fallback
 * (docs/prebuild/12-open-questions.md, "Voice"). Phones without one show the button disabled.
 * The words land in the input box; nothing happens until the owner taps "Plan it".
 */
@Composable
fun VoiceInputButton(enabled: Boolean, onText: (String) -> Unit, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember { createRecognizer(context) }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    fun start() {
        val r = recognizer ?: return onMessage("Voice input isn't available on this phone.")
        r.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    listening = false
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
                }
                override fun onError(error: Int) {
                    listening = false
                    onMessage(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the mic and try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Voice input needs the microphone permission."
                            else -> "Voice input stopped. You can type instead."
                        },
                    )
                }
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // belt and braces; the recogniser is on-device already
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
        )
        listening = true
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else onMessage("Without the microphone you can still type everything.")
    }

    OutlinedButton(
        onClick = {
            when {
                listening -> { recognizer?.stopListening(); listening = false }
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
                else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        enabled = enabled && recognizer != null,
        modifier = modifier.testTag("voice"),
    ) { Text(if (recognizer == null) "🎤 Needs on-device voice" else if (listening) "Listening… tap to stop" else "🎤 Speak") }
}

/** True when speech is recognised on the phone itself. */
fun isOnDeviceRecognition(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

/** On-device only, never the phone's default (possibly cloud) recogniser. */
private fun createRecognizer(context: Context): SpeechRecognizer? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    } else {
        null
    }
