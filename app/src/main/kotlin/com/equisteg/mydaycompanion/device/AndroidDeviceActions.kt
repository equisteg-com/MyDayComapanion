package com.equisteg.mydaycompanion.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import com.equisteg.mydaycompanion.domain.tools.DeviceAction
import com.equisteg.mydaycompanion.domain.tools.DeviceActionLauncher
import com.equisteg.mydaycompanion.domain.tools.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale

/**
 * Hands device actions to other apps with Android intents (build doc F8). The other app shows the
 * call, message, alarm or event and the owner finishes it there. Messages and e-mails go through
 * the system chooser so a lookalike app can't silently take them; nothing is ever sent from here.
 */
class AndroidDeviceActionLauncher(private val context: Context) : DeviceActionLauncher {
    override suspend fun launch(action: DeviceAction): Boolean = withContext(Dispatchers.Main) {
        val intent = when (action) {
            is DeviceAction.SetAlarm -> Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, action.time.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, action.time.minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
            is DeviceAction.Dial -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null))
            is DeviceAction.ComposeMessage -> {
                val send = when (action.channel) {
                    DeviceAction.Channel.SMS -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.to, null)).putExtra("sms_body", action.body)
                    DeviceAction.Channel.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(action.to))
                        .putExtra(Intent.EXTRA_TEXT, action.body)
                }
                Intent.createChooser(send, null)
            }
            is DeviceAction.Navigate -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.query)))
            is DeviceAction.CalendarInsert -> {
                val begin = action.date.atTime(action.start).atZone(ZoneId.systemDefault())
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, action.title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.toInstant().toEpochMilli())
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin.plusMinutes(action.durationMinutes.toLong()).toInstant().toEpochMilli())
            }
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

/** The phone's own text-to-speech engine; offline voices are used when the phone has them. */
class AndroidSpeaker(context: Context) : Speaker {
    private val ready = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    override suspend fun speak(text: String): Boolean {
        val ok = withTimeoutOrNull(3_000) { ready.await() } ?: false
        if (!ok) return false
        return withContext(Dispatchers.Main) {
            tts.language = Locale.getDefault()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mdc-speak") == TextToSpeech.SUCCESS
        }
    }

    fun stop() {
        tts.stop()
    }
}
