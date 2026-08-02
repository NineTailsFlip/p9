package life.furrer.p9.call

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

/** Telecom's entry point into P9 — bound only while P9 holds `RoleManager.ROLE_DIALER`. Each
 *  `Call` it's told about becomes a [TelecomCallSession] added to [CallRegistry]; everything else
 *  (rendering, key handling) lives in [CallActivity], which this only has to launch. No ringtone
 *  or foreground-service handling yet (M5); no notifications yet either, so the call screen is
 *  the only way back to an in-progress call for now. */
class P9InCallService : InCallService() {

    private val sessions = mutableMapOf<Call, TelecomCallSession>()

    override fun onCallAdded(call: Call) {
        val session = TelecomCallSession(call)
        sessions[call] = session
        CallRegistry.add(session)
        startActivity(
            Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onCallRemoved(call: Call) {
        val session = sessions.remove(call) ?: return
        session.unregister()
        CallRegistry.remove(session)
    }
}
