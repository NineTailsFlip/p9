package life.furrer.p9.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import life.furrer.nine.core.prefs.NinePrefs

private const val KEY_ASKED_DIALER_ROLE = "asked_dialer_role"

/** Whether P9 holds `RoleManager.ROLE_DIALER`, and the request flow to get it. Until it's
 *  granted, P9 still works: [life.furrer.p9.dial.DialerActivity] keeps functioning and dialing
 *  falls back to `ACTION_CALL` — Telecom just never binds [life.furrer.p9.call.P9InCallService],
 *  so there's no call screen. */
object DialerRole {
    fun isHeld(context: Context): Boolean =
        roleManager(context)?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false

    fun isAvailable(context: Context): Boolean =
        roleManager(context)?.isRoleAvailable(RoleManager.ROLE_DIALER) ?: false

    /** Start with `startActivityForResult`; the result code itself isn't meaningful to check —
     *  the only reliable way to know it took is to re-check [isHeld] once control returns. */
    fun requestIntent(context: Context): Intent = roleManager(context)!!.createRequestRoleIntent(RoleManager.ROLE_DIALER)

    /** First run asks once (docs/p9-plan.md); after that, granting the role lives as a Settings
     *  row instead of a repeated prompt. */
    fun hasAskedBefore(context: Context): Boolean = NinePrefs.of(context).getBoolean(KEY_ASKED_DIALER_ROLE, false)

    fun markAsked(context: Context) {
        NinePrefs.of(context).edit().putBoolean(KEY_ASKED_DIALER_ROLE, true).apply()
    }

    private fun roleManager(context: Context): RoleManager? = context.getSystemService(RoleManager::class.java)
}
