package life.furrer.p9.dial

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import life.furrer.nine.core.Nine
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.hideStatusBar

/** P9's only launcher entry. Empty shell for now (docs/p9-plan.md M2): just enough to install,
 *  launch, and reach Settings/Manual. Dialing, call history and search land in M3. */
class DialerActivity : Activity() {

    private lateinit var hintBar: HintBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        hintBar = HintBar(this).apply { setText("hold * settings") }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(this@DialerActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(hintBar)
        }
        setContentView(root)
        hideStatusBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    // No SearchField/menu yet to reserve KEYCODE_STAR away from the framework's own dispatch, so
    // the plain onKeyLongPress callback is enough here — M3 switches to a manual dispatchKeyEvent
    // override once the dial string and `*` menu exist, matching ContactsActivity/CalendarActivity.
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            startActivity(Intent(this, Nine.app.settingsActivity))
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }
}
