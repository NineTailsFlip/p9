package life.furrer.p9.call

import android.content.Intent
import android.content.pm.ServiceInfo
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.InCallService

/** Telecom's entry point into P9 — bound only while P9 holds `RoleManager.ROLE_DIALER`. Each
 *  `Call` it's told about becomes a [TelecomCallSession] added to [CallRegistry]; everything else
 *  (rendering, key handling) lives in [CallActivity]. Stays a foreground service with
 *  `phoneCall` type for as long as any call exists (required to keep running with the screen
 *  off), posting whichever of [CallNotifications]' two notifications matches the current call's
 *  state — the incoming one's full-screen intent is what actually shows [CallActivity] over a
 *  locked screen; `startActivity` below is the same-process fast path for when the screen's
 *  already on. Also publishes itself to [AudioRouting] for the duration of the binding, since
 *  mute/speaker only exist on the service, not on any one call. */
class P9InCallService : InCallService() {

    private val sessions = mutableMapOf<Call, TelecomCallSession>()
    private val onRegistryChanged = { updateForegroundNotification() }

    override fun onCreate() {
        super.onCreate()
        CallNotifications.ensureChannels(this)
        CallRegistry.addListener(onRegistryChanged)
        AudioRouting.attach(this)
    }

    override fun onDestroy() {
        AudioRouting.detach(this)
        CallRegistry.removeListener(onRegistryChanged)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        val session = TelecomCallSession(call, this)
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

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        AudioRouting.updateAvailableEndpoints(availableEndpoints)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        AudioRouting.notifyChanged()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        AudioRouting.updateMuted(isMuted)
    }

    private fun updateForegroundNotification() {
        val call = CallRegistry.all().firstOrNull()
        if (call == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        val notification = if (call.state == CallState.RINGING) {
            CallNotifications.incomingCallNotification(this, call)
        } else {
            CallNotifications.ongoingCallNotification(this, call)
        }
        startForeground(NOTIFICATION_ID_CALL, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
    }
}
