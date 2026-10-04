package life.furrer.p9.telecom

import android.app.role.RoleManager
import life.furrer.nine.core.util.SystemRole

/** Whether P9 holds `RoleManager.ROLE_DIALER`, and the request flow to get it. Until it's
 *  granted, P9 still works: [life.furrer.p9.dial.DialerActivity] keeps functioning and dialing
 *  falls back to `ACTION_CALL` — Telecom just never binds [life.furrer.p9.call.P9InCallService],
 *  so there's no call screen. First run asks once (docs/p9-plan.md); after that, granting the
 *  role lives as a Settings row instead of a repeated prompt. The prefs key is what existing
 *  installs already store — never rename it. */
val DialerRole = SystemRole(RoleManager.ROLE_DIALER, "asked_dialer_role")
