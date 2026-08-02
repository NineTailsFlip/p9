package life.furrer.p9.call

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.TextView
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.hideStatusBar

/** One screen, every call state (docs/p9-plan.md) — for now, just enough to prove the Telecom
 *  wiring: render whatever [CallRegistry] holds and end the call on request. Ringing UI,
 *  answer/reject, mute/hold/keypad and the ongoing notification all land in M5/M6; this
 *  deliberately does none of that yet.
 *
 *  Telecom state is never mirrored into this Activity's own fields — [render] re-reads
 *  [CallRegistry] from scratch on every call, so a call that changes state while this screen
 *  isn't listening (it was never backgrounded independently of the call, since there's no
 *  notification yet to leave it from) still renders correctly the moment it resumes. */
class CallActivity : Activity() {

    private lateinit var stateText: TextView
    private val onRegistryChanged = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        stateText = TextView(this).apply {
            textSize = 22f
            setTextColor(Palette.WHITE)
            gravity = Gravity.CENTER
        }
        setContentView(stateText)
        hideStatusBar()
    }

    override fun onResume() {
        super.onResume()
        CallRegistry.addListener(onRegistryChanged)
        render()
    }

    override fun onPause() {
        super.onPause()
        CallRegistry.removeListener(onRegistryChanged)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private fun render() {
        val call = CallRegistry.all().firstOrNull()
        if (call == null) {
            finish()
            return
        }
        stateText.text = "${call.displayName ?: call.handle ?: "Unknown"}\n${call.state}"
    }

    // Verify on the target device which of KEYCODE_CALL/KEYCODE_ENDCALL actually reaches this
    // Activity — PhoneWindowManager intercepts both before most apps ever see them and answers/
    // ends the call through Telecom directly (docs/p9-plan.md point 3). Handling both here is
    // this screen's second path to the same outcome, not the trigger it depends on: Telecom
    // removing the call from CallRegistry is what actually ends this screen either way.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENDCALL || keyCode == KeyEvent.KEYCODE_BACK) {
            CallRegistry.all().firstOrNull()?.disconnect()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
