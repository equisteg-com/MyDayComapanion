package com.equisteg.mydaycompanion.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.UiState
import com.equisteg.mydaycompanion.voice.SpeechEngine

/** Settings > Voice: where speech is recognised, the phone-service switch, spoken replies and a voice check. */
@Composable
fun VoiceSettings(state: UiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val engine = state.speechEngine
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            when (engine) {
                SpeechEngine.ON_DEVICE -> "Voice input: recognised on this phone. Your speech doesn't leave it."
                SpeechEngine.PHONE_SERVICE -> "Voice input: this phone has no on-device recogniser, so the phone's own speech service is used, asked to work offline. MyDayCompanion itself has no internet access."
                SpeechEngine.NONE -> if (state.phoneSpeechService) {
                    "Voice input: no speech recogniser found on this phone. Install or update Speech Services by Google (or the phone's voice input) to talk to the app."
                } else {
                    "Voice input: off, because this phone has no on-device recogniser and the phone's speech service is switched off."
                }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("voice-engine"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use the phone's speech service")
                Text(
                    "Only when the phone can't recognise speech on its own. The service belongs to the phone (usually Google); offline mode is requested, but if its offline pack is missing it may use the internet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.phoneSpeechService,
                onCheckedChange = viewModel::onPhoneSpeechService,
                modifier = Modifier.testTag("phone-speech-toggle"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Speak replies")
                Text("Reads every answer and plan out loud, as well as showing it. The speaker next to an answer reads just that one; \"Read\" on My day reads the day.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.preferences.speechOutputEnabled,
                onCheckedChange = { viewModel.onPreferencesChange(state.preferences.copy(speechOutputEnabled = it)) },
                enabled = !state.busy,
                modifier = Modifier.testTag("speak-toggle"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::onTestVoice, enabled = !state.busy, modifier = Modifier.testTag("voice-test")) { Text("Test voice") }
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent(TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Speech settings") }
        }
        state.voiceCheck?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("voice-check")) }
    }
}

/** Android's text-to-speech settings screen (engine, voice data, speed). */
private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
