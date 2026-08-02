package life.furrer.p9.call

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.VideoProfile

private const val CONNECT_VIBRATION_MS = 200L

/** The production [CallSession] — a thin live view over a real `android.telecom.Call`, with no
 *  state of its own beyond a registered [Call.Callback] to notify [CallRegistry] of changes and
 *  trigger [VibrateOnConnectSetting]'s vibration. `Call` has no stable string id of its own; the
 *  object reference persists for the call's whole lifetime (Telecom never recreates it mid-call),
 *  so its identity hash is stable enough to use. */
class TelecomCallSession(private val call: Call, private val context: Context) : CallSession {

    private var lastKnownState: CallState = mapState(call.details?.state ?: Call.STATE_NEW)

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val newState = mapState(state)
            if (justConnected(lastKnownState, newState, isOutgoing) && VibrateOnConnectSetting.isEnabled(context)) {
                vibrate()
            }
            lastKnownState = newState
            CallRegistry.notifyChanged()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = CallRegistry.notifyChanged()
    }

    init {
        call.registerCallback(callback)
    }

    fun unregister() = call.unregisterCallback(callback)

    override val id: String = System.identityHashCode(call).toString()
    override val state: CallState get() = mapState(call.details?.state ?: Call.STATE_NEW)
    override val handle: String? get() = call.details?.handle?.schemeSpecificPart
    override val displayName: String?
        get() = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: call.details?.contactDisplayName?.takeIf { it.isNotBlank() }
    override val connectTimeMillis: Long get() = call.details?.connectTimeMillis ?: 0L
    override val capabilities: Set<CallCapability> get() = mapCapabilities(call.details?.callCapabilities ?: 0)
    override val isOutgoing: Boolean get() = call.details?.callDirection == Call.Details.DIRECTION_OUTGOING

    override fun answer() = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    override fun reject() = call.reject(false, null)
    override fun disconnect() = call.disconnect()
    override fun hold() = call.hold()
    override fun unhold() = call.unhold()
    override fun playDtmf(c: Char) = call.playDtmfTone(c)
    override fun stopDtmf() = call.stopDtmfTone()

    private fun vibrate() {
        val vibratorManager = context.getSystemService(VibratorManager::class.java) ?: return
        vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(CONNECT_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun mapState(state: Int): CallState = when (state) {
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallState.RINGING
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_NEW -> CallState.DIALING
        Call.STATE_HOLDING -> CallState.HOLDING
        Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallState.DISCONNECTED
        else -> CallState.ACTIVE
    }

    private fun mapCapabilities(bits: Int): Set<CallCapability> {
        val result = mutableSetOf<CallCapability>()
        if (bits and Call.Details.CAPABILITY_HOLD != 0) result.add(CallCapability.HOLD)
        if (bits and Call.Details.CAPABILITY_MERGE_CONFERENCE != 0) result.add(CallCapability.MERGE_CONFERENCE)
        if (bits and Call.Details.CAPABILITY_MUTE != 0) result.add(CallCapability.MUTE)
        return result
    }
}
