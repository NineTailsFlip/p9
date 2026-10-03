package life.furrer.p9.call

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import life.furrer.nine.core.util.PhoneFormat

private const val SIDE_PADDING_DP = 16
private const val ELAPSED_TICK_MS = 1000L

/** One screen, every call state (docs/p9-plan.md). Ringing gets numbered action rows (Answer/
 *  Reject/Silence ringer); an active/holding/dialing call gets [CallActions]' own numbered rows
 *  (Mute/Speaker/Hold/Swap/Merge/Keypad/End call) with a ticking elapsed timer; inside Keypad
 *  mode every digit plus `*`/`#` is a literal DTMF tone instead of picking a row.
 *
 *  Telecom state is never mirrored into this Activity's own fields beyond UI-only state (the
 *  selected row, keypad mode) — [render] re-reads [CallRegistry]/[AudioRouting] from scratch
 *  every time, so a call that changes state while this screen wasn't listening still renders
 *  correctly the moment it resumes. */
class CallActivity : Activity() {

    private var selectedIndex = 0
    private var keypadMode = false
    private var keypadDigits = ""
    private var actionRows: List<CallActionRow> = emptyList()

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var rowContainer: LinearLayout
    private lateinit var hintBar: HintBar

    private val onChanged = { render() }
    private val elapsedHandler = Handler(Looper.getMainLooper())
    private val elapsedTick = object : Runnable {
        override fun run() {
            render()
            elapsedHandler.postDelayed(this, ELAPSED_TICK_MS)
        }
    }

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
        CallRegistry.addListener(onChanged)
        AudioRouting.addListener(onChanged)
        elapsedHandler.post(elapsedTick)
        ProximityScreenOff.keypadOpen = keypadMode
        render()
    }

    override fun onPause() {
        super.onPause()
        // The keypad isn't on screen once this isn't.
        ProximityScreenOff.keypadOpen = false
        CallRegistry.removeListener(onChanged)
        AudioRouting.removeListener(onChanged)
        elapsedHandler.removeCallbacks(elapsedTick)
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
        titleText.text = call.displayName ?: call.handle?.let { PhoneFormat.display(this, it) } ?: "Unknown"
        when {
            call.state == CallState.RINGING -> renderRinging(call)
            keypadMode -> renderKeypad()
            else -> renderActive(call)
        }
    }

    private fun renderRinging(call: CallSession) {
        subtitleText.text = call.handle?.let { PhoneFormat.display(this, it) } ?: ""
        rowContainer.removeAllViews()
        listOf("Answer", "Reject", "Silence ringer").forEachIndexed { index, label ->
            rowContainer.addView(actionRow(index + 1, label, "", selected = false))
        }
        hintBar.setText("call answer · end reject · vol silence")
    }

    private fun renderKeypad() {
        subtitleText.text = keypadDigits.ifEmpty { " " }
        rowContainer.removeAllViews()
        hintBar.setText("‹ close keypad")
    }

    private fun renderActive(call: CallSession) {
        subtitleText.text = if (call.state == CallState.ACTIVE) elapsedLabel(call.connectTimeMillis) else call.state.toString()
        actionRows = CallActions.rowsFor(
            muted = AudioRouting.isMuted(),
            audioRouteLabel = AudioRouting.currentLabel(),
            onHold = call.state == CallState.HOLDING,
            capabilities = call.capabilities,
            callCount = CallRegistry.all().size,
        )
        selectedIndex = selectedIndex.coerceIn(0, actionRows.size - 1)
        rowContainer.removeAllViews()
        actionRows.forEachIndexed { index, row ->
            val label = if (row.value.isEmpty()) row.label else "${row.label}            ${row.value}"
            rowContainer.addView(actionRow(row.number, label, "", selected = index == selectedIndex))
        }
        val keypadNumber = CallActions.keypadRowNumber(actionRows)
        hintBar.setText(if (keypadNumber != null) "ok toggle · $keypadNumber keypad · end" else "ok toggle · end")
    }

    private fun elapsedLabel(connectTimeMillis: Long): String {
        if (connectTimeMillis <= 0L) return "00:00"
        val elapsedSeconds = ((System.currentTimeMillis() - connectTimeMillis) / 1000).coerceAtLeast(0)
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun actionRow(number: Int, label: String, value: String, selected: Boolean): TextView = TextView(this).apply {
        text = if (value.isEmpty()) "$number $label" else "$number $label $value"
        textSize = 18f
        setPadding(dp(SIDE_PADDING_DP), dp(10), dp(SIDE_PADDING_DP), dp(10))
        applyRowSelectionStyle(selected)
    }

    // Verify on the target device which of KEYCODE_CALL/KEYCODE_ENDCALL actually reaches this
    // Activity — PhoneWindowManager intercepts both before most apps ever see them and answers/
    // ends the call through Telecom directly (docs/p9-plan.md point 3). Handling both here is
    // this screen's second path to the same outcome, not the trigger it depends on: Telecom
    // changing the call's state is what actually drives [render] either way.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (swallowedByCheek(keyCode)) return true
        val call = CallRegistry.all().firstOrNull() ?: return super.onKeyDown(keyCode, event)

        if (call.state == CallState.RINGING) return handleRingingKey(call, keyCode)
        if (keypadMode) return handleKeypadKeyDown(call, keyCode)
        return handleActiveKey(call, keyCode)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (swallowedByCheek(keyCode)) return true
        // The matching key-down was consumed to silence the ringer; consume the release too so
        // the volume change doesn't fire from the up half.
        if (isVolumeUpDown(keyCode) && CallRegistry.all().firstOrNull()?.state == CallState.RINGING) return true
        if (keypadMode && keyCode != KeyEvent.KEYCODE_BACK) {
            CallRegistry.all().firstOrNull()?.stopDtmf()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /** With the phone against a face, any key press is a cheek, not a person — except volume,
     *  which is how you adjust a call you're listening to. */
    private fun swallowedByCheek(keyCode: Int): Boolean =
        ProximityScreenOff.isCovered &&
            keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            keyCode != KeyEvent.KEYCODE_VOLUME_MUTE

    private fun isVolumeUpDown(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    private fun handleRingingKey(call: CallSession, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_1 -> call.answer()
            KeyEvent.KEYCODE_ENDCALL, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_2 -> call.reject()
            KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> silenceRinger()
            else -> return false
        }
        return true
    }

    /** Digits pick rows, they do not send tones (docs/p9-plan.md decision 3) — Up/Down/OK drive
     *  the list, and pressing a row's own (currently-displayed) number invokes it directly, the
     *  same numbered-row convention as `MenuSheet` and L9's slots. Keypad mode is reachable only
     *  by invoking the Keypad row this way, never by any other key. */
    private fun handleActiveKey(call: CallSession, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> selectedIndex = (selectedIndex - 1).coerceIn(0, actionRows.size - 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> selectedIndex = (selectedIndex + 1).coerceIn(0, actionRows.size - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> actionRows.getOrNull(selectedIndex)?.let { invoke(call, it) }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                val n = keyCode - KeyEvent.KEYCODE_0
                actionRows.firstOrNull { it.number == n }?.let { invoke(call, it) }
            }
            KeyEvent.KEYCODE_ENDCALL -> call.disconnect()
            // Leaves the call screen without ending the call (M5's ongoing notification is what
            // brings it back) -- the one Back behavior in the suite that neither closes a screen
            // nor edits anything, since there is nothing to "cancel" about a call in progress.
            KeyEvent.KEYCODE_BACK -> finish()
            else -> return false
        }
        render()
        return true
    }

    private fun invoke(call: CallSession, row: CallActionRow) {
        when (row.label) {
            "Mute" -> AudioRouting.setMuted(!AudioRouting.isMuted())
            "Speaker" -> AudioRouting.cycle()
            "Hold" -> if (call.state == CallState.HOLDING) call.unhold() else call.hold()
            "Keypad" -> {
                setKeypadMode(true)
                keypadDigits = ""
            }
            "End call" -> call.disconnect()
            // Swap/Merge need a second CallSession to act on, which CallRegistry already exposes
            // in call-added order -- left as a should-have (docs/p9-plan.md: "drop it without
            // regret") since it needs a real two-call scenario to verify against Telecom at all.
            "Swap calls", "Merge" -> Unit
        }
    }

    private fun handleKeypadKeyDown(call: CallSession, keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setKeypadMode(false)
            render()
            return true
        }
        val tone = dtmfCharFor(keyCode) ?: return false
        call.playDtmf(tone)
        keypadDigits += tone
        render()
        return true
    }

    private fun setKeypadMode(open: Boolean) {
        keypadMode = open
        ProximityScreenOff.keypadOpen = open
    }

    private fun dtmfCharFor(keyCode: Int): Char? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0' + (keyCode - KeyEvent.KEYCODE_0)
        KeyEvent.KEYCODE_STAR -> '*'
        KeyEvent.KEYCODE_POUND -> '#'
        else -> null
    }

    // Lint flags this as needing MODIFY_PHONE_STATE, a signature/system permission a third-party
    // app can never hold — but per TelecomManager's own docs, silenceRinger() is also allowed for
    // the current default dialer app, which lint's @RequiresPermission model can't express. This
    // is a documented, real false positive, not a bug to fix by adding a permission P9 could
    // never be granted.
    @android.annotation.SuppressLint("MissingPermission")
    private fun silenceRinger() {
        getSystemService(TelecomManager::class.java)?.silenceRinger()
    }

    private fun dp(value: Int): Int = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
