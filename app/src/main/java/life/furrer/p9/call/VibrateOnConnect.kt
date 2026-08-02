package life.furrer.p9.call

import android.content.Context
import life.furrer.nine.core.prefs.NinePrefs

private const val KEY_VIBRATE_ON_CONNECT = "vibrate_on_connect"

/** Pure: did this state change mean "the other side just picked up"? Only true for an outgoing
 *  call's first transition into [CallState.ACTIVE] — an incoming call doesn't need the tell,
 *  you already know you answered it, and a call re-entering ACTIVE from HOLDING is a resume, not
 *  a fresh connect. */
fun justConnected(previousState: CallState, newState: CallState, isOutgoing: Boolean): Boolean =
    isOutgoing && newState == CallState.ACTIVE && previousState != CallState.ACTIVE && previousState != CallState.HOLDING

/** "The classic feature-phone tell that the other side picked up, and the only way to know
 *  without looking" (docs/p9-plan.md's Settings section) — default on. */
object VibrateOnConnectSetting {
    fun isEnabled(context: Context): Boolean = NinePrefs.of(context).getBoolean(KEY_VIBRATE_ON_CONNECT, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        NinePrefs.of(context).edit().putBoolean(KEY_VIBRATE_ON_CONNECT, enabled).apply()
    }
}
