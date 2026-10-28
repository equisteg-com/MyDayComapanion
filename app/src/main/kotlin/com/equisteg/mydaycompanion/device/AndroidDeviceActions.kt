package com.equisteg.mydaycompanion.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
class AndroidDeviceActionLauncher(private val context: Context, private val zone: () -> ZoneId) : DeviceActionLauncher {
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
                val begin = action.date.atTime(action.start).atZone(zone())
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

/**
 * The phone's own text-to-speech engine; offline voices are used when the phone has them. The
 * engine is started only when something is spoken and shut down when it finishes, so nothing is
 * bound in the background for owners who never use voice.
 */
class AndroidSpeaker(context: Context) : Speaker {
    private val appContext = context.applicationContext

    override suspend fun speak(text: String): Boolean = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Boolean>()
        val tts = TextToSpeech(appContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        val ok = withTimeoutOrNull(3_000) { ready.await() } ?: false
        if (!ok) {
            tts.shutdown()
            return@withContext false
        }
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = tts.shutdown()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = tts.shutdown()
            },
        )
        tts.language = Locale.getDefault()
        val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mdc-speak") == TextToSpeech.SUCCESS
        if (!queued) tts.shutdown()
        queued
    }
}
