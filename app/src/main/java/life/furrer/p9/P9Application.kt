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

// Placeholder intro/key map (docs/p9-plan.md M2) — the real content is M8's job, written once
// dialing, calls and history actually exist to document.
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
                P9 ("Phone on 9 keys") is a phone app for a keypad phone with no touchscreen: an
                all-black dialer that doubles as call history, and a call screen driven entirely by
                the green key, the red key and the d-pad.

                This build is early: dialing, calls and history are still being wired up.
            """.trimIndent(),
            keyMap = listOf(
                KeyHelp("Hold *", "Open Settings."),
            ),
        ),
    ),
)
