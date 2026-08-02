package life.furrer.p9.call

import android.app.Activity
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.applyRowSelectionStyle
import life.furrer.nine.core.ui.hideStatusBar

private const val SIDE_PADDING_DP = 16

/** One screen, every call state (docs/p9-plan.md). Ringing gets its own numbered action rows
 *  (Answer/Reject/Silence ringer) here in M5; the active-call action list (mute, speaker, hold,
 *  keypad — [life.furrer.p9.call]'s equivalent of the suite's `CallActions`) is M6's job, so a
 *  non-ringing call still just shows its state and can be ended for now.
 *
 *  Telecom state is never mirrored into this Activity's own fields — [render] re-reads
 *  [CallRegistry] from scratch every time, so a call that changes state while this screen wasn't
 *  listening still renders correctly the moment it resumes. */
class CallActivity : Activity() {

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var rowContainer: LinearLayout
    private lateinit var hintBar: HintBar
    private val onRegistryChanged = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        titleText = TextView(this).apply {
            textSize = 22f
            setTextColor(Palette.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(SIDE_PADDING_DP), dp(24), dp(SIDE_PADDING_DP), dp(4))
        }
        subtitleText = TextView(this).apply {
            textSize = 15f
            setTextColor(Palette.GRAY_DIM)
            gravity = Gravity.CENTER
            setPadding(dp(SIDE_PADDING_DP), 0, dp(SIDE_PADDING_DP), dp(24))
        }
        rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hintBar = HintBar(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleText)
            addView(subtitleText)
            addView(rowContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(hintBar)
        }
        setContentView(root)
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
        titleText.text = call.displayName ?: call.handle ?: "Unknown"
        if (call.state == CallState.RINGING) {
            subtitleText.text = call.handle ?: ""
            renderRingingRows()
            hintBar.setText("call answer · end reject")
        } else {
            subtitleText.text = call.state.toString()
            renderEndOnlyRow()
            hintBar.setText("end hang up")
        }
    }

    private fun renderRingingRows() {
        rowContainer.removeAllViews()
        listOf("Answer", "Reject", "Silence ringer").forEachIndexed { index, label ->
            rowContainer.addView(actionRow(index + 1, label))
        }
    }

    private fun renderEndOnlyRow() {
        rowContainer.removeAllViews()
        rowContainer.addView(actionRow(1, "End call"))
    }

    private fun actionRow(number: Int, label: String): TextView = TextView(this).apply {
        text = "$number $label"
        textSize = 18f
        setPadding(dp(SIDE_PADDING_DP), dp(10), dp(SIDE_PADDING_DP), dp(10))
        applyRowSelectionStyle(selected = false)
    }

    // Verify on the target device which of KEYCODE_CALL/KEYCODE_ENDCALL actually reaches this
    // Activity — PhoneWindowManager intercepts both before most apps ever see them and answers/
    // ends the call through Telecom directly (docs/p9-plan.md point 3). Handling both here is
    // this screen's second path to the same outcome, not the trigger it depends on: Telecom
    // removing (or changing the state of) the call is what actually drives [render] either way.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val call = CallRegistry.all().firstOrNull() ?: return super.onKeyDown(keyCode, event)
        if (call.state == CallState.RINGING) {
            when (keyCode) {
                KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_1 -> {
                    call.answer()
                    return true
                }
                KeyEvent.KEYCODE_ENDCALL, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_2 -> {
                    call.reject()
                    return true
                }
                KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_3 -> {
                    silenceRinger()
                    return true
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_ENDCALL || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_1) {
            call.disconnect()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Lint flags this as needing MODIFY_PHONE_STATE, a signature/system permission a third-party
    // app can never hold — but per TelecomManager's own docs, silenceRinger() is also allowed for
    // the current default dialer app, which lint's @RequiresPermission model can't express. This
    // is a documented, real false positive (see the lint baseline), not a bug to fix by adding a
    // permission P9 could never be granted.
    @android.annotation.SuppressLint("MissingPermission")
    private fun silenceRinger() {
        getSystemService(TelecomManager::class.java)?.silenceRinger()
    }

    private fun dp(value: Int): Int = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
