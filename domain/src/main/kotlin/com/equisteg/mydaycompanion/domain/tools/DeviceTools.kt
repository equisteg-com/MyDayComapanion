package com.equisteg.mydaycompanion.domain.tools

import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.safety.ContentSafety
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalTime

/**
 * Something another app on the phone does for the owner. The app hands it over with an Android
 * intent; the other app shows it and the owner finishes it there (presses Call, Send, Start).
 * Nothing is sent or called by MyDayCompanion itself.
 */
sealed interface DeviceAction {
    data class SetAlarm(val time: LocalTime, val label: String) : DeviceAction
    data class Dial(val number: String) : DeviceAction
    data class ComposeMessage(val channel: Channel, val to: String, val body: String) : DeviceAction
    data class Navigate(val query: String) : DeviceAction
    data class CalendarInsert(val title: String, val date: LocalDate, val start: LocalTime, val durationMinutes: Int) : DeviceAction

    enum class Channel { SMS, EMAIL }
}

/** Opens the right app. Returns false when no app on the phone can handle it. */
fun interface DeviceActionLauncher {
    suspend fun launch(action: DeviceAction): Boolean
}

internal object DeviceArgs {
    const val MAX_TEXT = 500
    const val MAX_QUERY = 200
    private val phone = Regex("^\\+?[0-9][0-9 ()\\-]{2,19}$")
    private val email = Regex("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[A-Za-z]{2,24}$")

    fun phone(s: String?): String? = s?.trim()?.takeIf { phone.matches(it) }
    fun email(s: String?): String? = s?.trim()?.takeIf { it.length <= 254 && email.matches(it) }
    fun text(s: String?, max: Int): String? = s?.trim()?.takeIf { it.isNotEmpty() && it.length <= max && !ContentSafety.looksLikeInstructionToAssistant(it) }
}

/**
 * The five device actions (build doc F8). All are L3: the owner taps Confirm and then proves it is
 * them with the fingerprint, face or screen lock, every time, whoever proposed it. Arguments are
 * strict and never written to the audit log (phone numbers and message text are personal).
 */
abstract class DeviceActionTool(private val launcher: DeviceActionLauncher) : AgentTool {
    protected abstract val keys: Array<String>
    protected abstract fun parse(args: JsonObject): DeviceAction?

    protected fun spec(id: String, description: String) = ToolSpec(
        id = id,
        description = description,
        riskLevel = RiskLevel.L3,
        agentProposable = true,
        confirmation = Confirmation.ALWAYS,
        verification = "The phone opened an app that can finish the action.",
    )

    override fun validate(args: JsonObject): ArgsValidation =
        if (args.onlyKeys(*keys) && parse(args) != null) ArgsValidation.Ok else ArgsValidation.Rejected("args")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val action = parse(args) ?: return ToolOutcome.Failure("INVALID")
        return if (launcher.launch(action)) ToolOutcome.Success("handed over") else ToolOutcome.Failure("NO_APP")
    }

    // The other app takes over from here; whether the owner then calls or sends is up to them.
    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = true
}

/** device.setAlarm — Args: {"time":"HH:mm","label"} */
class SetAlarmTool(launcher: DeviceActionLauncher) : DeviceActionTool(launcher) {
    override val spec = spec("device.setAlarm", "Set an alarm in the phone's clock app.")
    override val keys = arrayOf("time", "label")
    override fun parse(args: JsonObject): DeviceAction? {
        val time = parseTime(args.string("time")) ?: return null
        val label = args.string("label")?.takeIf { validTitle(it) }?.trim() ?: return null
        return DeviceAction.SetAlarm(time, label)
    }

    companion object {
        fun args(time: LocalTime, label: String) = JsonObject(mapOf("time" to JsonPrimitive(time.toString()), "label" to JsonPrimitive(label)))
    }
}

/** device.dial — opens the dialler with the number filled in. Args: {"number"} */
class DialTool(launcher: DeviceActionLauncher) : DeviceActionTool(launcher) {
    override val spec = spec("device.dial", "Open the phone app with a number ready to call.")
    override val keys = arrayOf("number")
    override fun parse(args: JsonObject): DeviceAction? = DeviceArgs.phone(args.string("number"))?.let { DeviceAction.Dial(it) }
}

/** device.composeMessage — an SMS or e-mail draft the owner sends themselves. Args: {"channel":"SMS|EMAIL","to","body"} */
class ComposeMessageTool(launcher: DeviceActionLauncher) : DeviceActionTool(launcher) {
    override val spec = spec("device.composeMessage", "Open a text message or e-mail draft for the owner to send.")
    override val keys = arrayOf("channel", "to", "body")
    override fun parse(args: JsonObject): DeviceAction? {
        val channel = DeviceAction.Channel.entries.firstOrNull { it.name == args.string("channel") } ?: return null
        val to = when (channel) {
            DeviceAction.Channel.SMS -> DeviceArgs.phone(args.string("to"))
            DeviceAction.Channel.EMAIL -> DeviceArgs.email(args.string("to"))
        } ?: return null
        val body = DeviceArgs.text(args.string("body"), DeviceArgs.MAX_TEXT) ?: return null
        return DeviceAction.ComposeMessage(channel, to, body)
    }
}

/** device.navigate — directions in the maps app. Args: {"query"} */
class NavigateTool(launcher: DeviceActionLauncher) : DeviceActionTool(launcher) {
    override val spec = spec("device.navigate", "Open directions to a place in the maps app.")
    override val keys = arrayOf("query")
    override fun parse(args: JsonObject): DeviceAction? = DeviceArgs.text(args.string("query"), DeviceArgs.MAX_QUERY)?.let { DeviceAction.Navigate(it) }
}

/** device.calendarInsert — the calendar app opens a new event, filled in, for the owner to save. Args: {"title","date","start","durationMinutes"} */
class CalendarInsertTool(launcher: DeviceActionLauncher) : DeviceActionTool(launcher) {
    override val spec = spec("device.calendarInsert", "Open a new calendar event, filled in, for the owner to save.")
    override val keys = arrayOf("title", "date", "start", "durationMinutes")
    override fun parse(args: JsonObject): DeviceAction? {
        val title = args.string("title")?.takeIf { validTitle(it) }?.trim() ?: return null
        val date = parseDate(args.string("date")) ?: return null
        val start = parseTime(args.string("start")) ?: return null
        val minutes = args.int("durationMinutes")?.takeIf { validDuration(it) } ?: return null
        return DeviceAction.CalendarInsert(title, date, start, minutes)
    }
}

/** Speaks text aloud through the phone's own text-to-speech. */
fun interface Speaker {
    suspend fun speak(text: String): Boolean
}

/**
 * voice.speak — reads a summary aloud (the day's brief, what was just added). L1, run by the app
 * when the owner taps "Read my day" or has turned spoken replies on. Args: {"text"}
 */
class SpeakTool(private val speaker: Speaker) : AgentTool {
    override val spec = ToolSpec(
        id = "voice.speak",
        description = "Read a short summary aloud.",
        riskLevel = RiskLevel.L1,
        featureFlag = Flag.VOICE.key,
        agentProposable = false,
        confirmation = Confirmation.NEVER,
        verification = "The text-to-speech engine accepted the text.",
    )

    override fun validate(args: JsonObject): ArgsValidation {
        val text = args.string("text")
        return if (args.onlyKeys("text") && text != null && text.isNotBlank() && text.length <= DeviceArgs.MAX_TEXT * 2) ArgsValidation.Ok else ArgsValidation.Rejected("text")
    }

    override suspend fun execute(args: JsonObject): ToolOutcome =
        if (speaker.speak(args.string("text")!!)) ToolOutcome.Success("spoken") else ToolOutcome.Failure("TTS_UNAVAILABLE")

    override suspend fun verify(args: JsonObject, outcome: ToolOutcome.Success): Boolean = true
}
