package life.furrer.p9

import android.app.Application
import life.furrer.nine.core.KeyHelp
import life.furrer.nine.core.Nine
import life.furrer.nine.core.NineApp
import life.furrer.nine.core.NineSurface
import life.furrer.p9.settings.SettingsActivity

class P9Application : Application() {
    override fun onCreate() {
        super.onCreate()
        Nine.init(p9App)
    }
}

private val p9App = NineApp(
    id = "p9",
    name = "P9",
    tagline = "Phone on 9 keys",
    settingsActivity = SettingsActivity::class.java,
    surfaces = listOf(
        NineSurface(
            id = "",
            name = "P9",
            introText = """
                P9 ("Phone on 9 keys") is a phone app for a keypad phone with no touchscreen: a
                dialer that doubles as call history, and a call screen driven entirely by the green
                key, the red key and the d-pad.

                Dial a number directly, or type any digit to search your contacts and call history
                the same T9 way as every other Nine app. With nothing typed, the list shows your call
                history instead, newest first and grouped by day.

                For P9 to draw its own call screen and answer/end calls directly, it needs to be set
                as your phone's default app — the dialer's "*" menu offers this, and it also lives in
                Settings as "Default phone app". Until it's granted, dialing still works, it just hands
                the call off to whatever app already owns that role.
            """.trimIndent(),
            keyMap = listOf(
                KeyHelp("0–9", "Append to the dial string; the list filters live."),
                KeyHelp("Hold 0", "Insert \"+\"."),
                KeyHelp("Up / Down", "Move the selection."),
                KeyHelp("Left / Right", "Jump to the previous/next day or section."),
                KeyHelp("OK", "Open the selected contact, call, or dial the typed number."),
                KeyHelp("Call key", "Dial the selected row, or the typed digits."),
                KeyHelp("Back", "Delete the last digit."),
                KeyHelp("Hold Back", "Clear the whole dial string."),
                KeyHelp("Back, empty", "Close the dialer."),
                KeyHelp("#", "Toggle showing missed calls only."),
                KeyHelp("*", "Open the menu: contacts, new contact / add to existing contact, delete/clear history, settings."),
                KeyHelp("Hold *", "Open Settings."),
                KeyHelp("— Ringing —", ""),
                KeyHelp("Call key / OK / 1", "Answer."),
                KeyHelp("End key / Back / 2", "Reject."),
                KeyHelp("# / 3", "Silence the ringer."),
                KeyHelp("— During a call —", ""),
                KeyHelp("Up / Down", "Move the selection among Mute, Speaker, Hold, Keypad, End call."),
                KeyHelp("OK, or a row's own number", "Toggle or open the selected row."),
                KeyHelp("Keypad", "Enter DTMF mode — every digit plus \"*\"/\"#\" sends a tone."),
                KeyHelp("Back, in Keypad", "Return to the action list."),
                KeyHelp("Back, elsewhere", "Leave the call screen without ending the call."),
                KeyHelp("End key", "End the call."),
            ),
        ),
    ),
)
