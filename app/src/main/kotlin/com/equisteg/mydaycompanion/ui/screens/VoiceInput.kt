package com.equisteg.mydaycompanion.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.MicUse
import com.equisteg.mydaycompanion.ui.UiState
import com.equisteg.mydaycompanion.ui.VoicePhase
import com.equisteg.mydaycompanion.voice.SpeechEngine

/*
 * Voice (build doc F6), modelled on how other assistants do it:
 *  - the mic in the input box is dictation (voice-to-text): words appear live, nothing runs until "Plan it";
 *  - "Talk" opens a hands-free conversation (voice-to-voice): listen, answer out loud, listen again;
 *  - the speaker on an answer reads it aloud (text-to-voice), and "Speak replies" reads every answer.
 * The microphone is on only while the owner is talking to the app; there is no wake word.
 */

/** Material icon paths (Apache-2.0), drawn here because the core icon set has no mic. */
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

val MicIcon: ImageVector by lazy {
    icon("mic", "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z")
}
val WaveIcon: ImageVector by lazy {
    icon("graphic_eq", "M7,18h2L9,6L7,6v12zM11,22h2L13,2h-2v20zM3,14h2v-4L3,10v4zM15,18h2L17,6h-2v12zM19,10v4h2v-4h-2z")
}
val SpeakerIcon: ImageVector by lazy {
    icon("volume_up", "M3,9v6h4l5,5L12,4L7,9L3,9zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
}
val StopIcon: ImageVector by lazy { icon("stop", "M6,6h12v12H6z") }

/**
 * Runs [action] once the microphone is allowed, asking the first time. Saying no keeps typing
 * working and explains where to change it.
 */
@Composable
fun rememberMicGate(onMessage: (String) -> Unit, action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(action)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latest() else onMessage("Without the microphone you can still type everything. You can allow it later in Android Settings > Apps > MyDayCompanion.")
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) latest()
        else ask.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/** The mic inside the input box: tap to dictate, tap again to stop. */
@Composable
fun DictationMic(state: UiState, viewModel: MainViewModel) {
    val start = rememberMicGate(viewModel::onInfo) { viewModel.onDictate() }
    val pulse by animateFloatAsState(if (state.dictating) 1f + state.dictationLevel * 0.25f else 1f, label = "dictation-level")
    IconButton(
        onClick = {
            when {
                state.dictating -> viewModel.onDictate()
                viewModel.micReady(MicUse.DICTATE) -> start()
                else -> Unit
            }
        },
        enabled = !state.busy || state.dictating,
        modifier = Modifier.testTag("voice").semantics { contentDescription = if (state.dictating) "Stop dictation" else "Speak instead of typing" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (state.dictating) {
                Canvas(Modifier.size(36.dp)) { drawCircle(Color(0x33E53935), radius = size.minDimension / 2 * pulse) }
            }
            Icon(
                if (state.dictating) StopIcon else MicIcon,
                contentDescription = null,
                tint = if (state.dictating) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** "Talk": opens the hands-free conversation. */
@Composable
fun TalkButton(state: UiState, viewModel: MainViewModel) {
    val open = rememberMicGate(viewModel::onInfo) { viewModel.onVoiceOpen() }
    FilledTonalButton(
        onClick = { if (viewModel.micReady(MicUse.TALK)) open() },
        enabled = !state.busy && !state.dictating,
        modifier = Modifier.testTag("talk"),
    ) {
        Icon(WaveIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text("Talk")
    }
}

/** The full-screen voice conversation, like the voice modes of other assistants. */
@Composable
fun VoiceConversation(state: UiState, viewModel: MainViewModel) {
    val session = state.voice ?: return
    val engine = state.speechEngine
    Dialog(onDismissRequest = viewModel::onVoiceClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("voice-session"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Talk to your day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::onVoiceClose, modifier = Modifier.testTag("voice-close")) {
                        Icon(Icons.Filled.Close, contentDescription = "End conversation")
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    if (session.reply.isNotEmpty() && session.phase != VoicePhase.LISTENING) {
                        Text(
                            session.reply,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("voice-reply").semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.proposal != null && session.phase != VoicePhase.THINKING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.onVoiceAnswer(true) }, modifier = Modifier.testTag("voice-yes")) { Text("Add it") }
                            OutlinedButton(onClick = { viewModel.onVoiceAnswer(false) }, modifier = Modifier.testTag("voice-no")) { Text("Cancel") }
                        }
                    }
                }
                Orb(session.phase, session.level, onTap = viewModel::onVoiceTap)
                Spacer(Modifier.height(16.dp))
                Text(
                    when (session.phase) {
                        VoicePhase.LISTENING -> "Listening…"
                        VoicePhase.THINKING -> "Thinking…"
                        VoicePhase.SPEAKING -> "Speaking · tap to skip"
                        VoicePhase.PAUSED -> "Tap the circle to talk"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("voice-phase"),
                )
                Text(
                    session.heard.ifEmpty { if (session.phase == VoicePhase.LISTENING) "Say something like \"tomorrow gym at 7, call mom, dentist at 4\"" else "" },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = if (session.heard.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp).testTag("voice-heard"),
                )
                session.hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).testTag("voice-hint"))
                }
                Text(
                    "Say \"yes\" or \"no\" to answer, \"stop\" to finish. " + when (engine) {
                        SpeechEngine.ON_DEVICE -> "Speech is recognised on this phone."
                        SpeechEngine.PHONE_SERVICE -> "Uses the phone's speech service, offline when it can."
                        SpeechEngine.NONE -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Orb(phase: VoicePhase, level: Float, onTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val loud by animateFloatAsState(if (phase == VoicePhase.LISTENING) level else 0f, tween(120), label = "orb-level")
    val loop = rememberInfiniteTransition(label = "orb")
    val breathe by loop.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "orb-breathe")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "orb-spin")
    val scale = when (phase) {
        VoicePhase.LISTENING -> 1f + loud * 0.3f
        VoicePhase.SPEAKING -> breathe
        else -> 1f
    }
    Box(
        Modifier.size(180.dp).clickable(onClick = onTap).testTag("voice-orb").semantics {
            contentDescription = when (phase) {
                VoicePhase.LISTENING -> "Stop listening"
                VoicePhase.SPEAKING -> "Skip the reply"
                VoicePhase.PAUSED -> "Start talking"
                VoicePhase.THINKING -> "Working on it"
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 * 0.8f * scale
            drawCircle(colors.primary.copy(alpha = 0.18f), radius = (r * 1.18f).coerceAtMost(size.minDimension / 2))
            drawCircle(
                Brush.radialGradient(listOf(colors.primary, colors.tertiary), center = Offset(size.width * 0.4f, size.height * 0.35f), radius = r * 1.4f),
                radius = r,
            )
            if (phase == VoicePhase.THINKING) {
                drawArc(colors.onPrimary, startAngle = spin, sweepAngle = 90f, useCenter = false, style = Stroke(width = 6.dp.toPx()), topLeft = Offset(center.x - r * 0.6f, center.y - r * 0.6f), size = androidx.compose.ui.geometry.Size(r * 1.2f, r * 1.2f))
            }
        }
        Icon(
            when (phase) {
                VoicePhase.SPEAKING -> SpeakerIcon
                VoicePhase.THINKING -> WaveIcon
                else -> MicIcon
            },
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.size(48.dp),
        )
    }
}

/** The speaker button on an answer in the chat. */
@Composable
fun ReadAloudButton(text: String, tag: String, onRead: (String) -> Unit) {
    IconButton(onClick = { onRead(text) }, modifier = Modifier.size(32.dp).testTag(tag)) {
        Icon(SpeakerIcon, contentDescription = "Read aloud", modifier = Modifier.size(18.dp))
    }
}

/**
 * Asked once when a mic is tapped on a phone that can only listen through its own speech service
 * (most non-Pixel phones) and that switch is off (OQ-28). "Turn on" also asks for the microphone.
 */
@Composable
fun PhoneSpeechPrompt(state: UiState, viewModel: MainViewModel) {
    if (state.askPhoneSpeech == null) return
    val allow = rememberMicGate(viewModel::onInfo) { viewModel.onAllowPhoneSpeech() }
    AlertDialog(
        onDismissRequest = viewModel::onDismissPhoneSpeech,
        modifier = Modifier.testTag("phone-speech-prompt"),
        title = { Text("Use the phone's speech service?") },
        text = {
            Text(
                "This phone can't recognise speech on its own. Its own speech service (usually Google's) can, and is asked to " +
                    "work offline; if its offline pack is missing it may use the internet. MyDayCompanion itself has no internet " +
                    "access. You can change this in Settings > Voice.",
            )
        },
        confirmButton = { TextButton(onClick = { allow() }, modifier = Modifier.testTag("phone-speech-allow")) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = viewModel::onDismissPhoneSpeech, modifier = Modifier.testTag("phone-speech-later")) { Text("Not now") } },
    )
}
