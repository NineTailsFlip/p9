package life.furrer.p9.call

import android.os.OutcomeReceiver
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService

/** Audio routing and mute live on the bound `InCallService`, not on any particular call —
 *  P9InCallService publishes itself here when Telecom binds it (mirroring [CallRegistry]'s own
 *  "the service is the source of truth, this object is just how a screen reaches it" shape), so
 *  [CallActivity] can read/cycle them without a direct reference to the service instance. Uses
 *  the `CallEndpoint` API (API 34+) per docs/p9-plan.md, not the older, deprecated
 *  `CallAudioState`/`setAudioRoute` pair — including for mute, whose only synchronous getter
 *  (`getCallAudioState().isMuted`) turned out to be deprecated too once actually checked against
 *  the SDK, so mute state is tracked locally from `onMuteStateChanged` instead of polled.
 *
 *  `InCallService` has no synchronous "what's available" getter for `CallEndpoint`s either — only
 *  the `onAvailableCallEndpointsChanged` callback delivers that list, so [updateAvailableEndpoints]
 *  is P9InCallService's job to call whenever it fires. */
object AudioRouting {
    private var service: InCallService? = null
    private var availableEndpoints: List<CallEndpoint> = emptyList()
    private var muted = false
    private val listeners = mutableListOf<() -> Unit>()

    /** Earpiece, then speaker, then Bluetooth if available — cycling skips any type not currently
     *  in [availableEndpoints], e.g. Bluetooth with nothing paired. */
    private val cycleOrder = listOf(CallEndpoint.TYPE_EARPIECE, CallEndpoint.TYPE_SPEAKER, CallEndpoint.TYPE_BLUETOOTH)

    fun attach(service: InCallService) {
        this.service = service
        muted = false
        notifyChanged()
    }

    fun detach(service: InCallService) {
        if (this.service === service) this.service = null
    }

    /** Test-only: this is a process-wide singleton, so tests that call [setMuted] need a way to
     *  drain that state back out afterward rather than leaking it into the next test. */
    fun reset() {
        service = null
        availableEndpoints = emptyList()
        muted = false
        listeners.clear()
    }

    fun updateAvailableEndpoints(endpoints: List<CallEndpoint>) {
        availableEndpoints = endpoints
        notifyChanged()
    }

    fun updateMuted(isMuted: Boolean) {
        muted = isMuted
        notifyChanged()
    }

    fun isMuted(): Boolean = muted

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
        this.muted = muted
        notifyChanged()
    }

    /** "generic 'Bluetooth' label rather than requesting a permission for a string" (docs/p9-
     *  plan.md) — the per-device Bluetooth name needs BLUETOOTH_CONNECT, which P9 doesn't request
     *  for a label alone. */
    fun currentLabel(): String = labelFor(service?.currentCallEndpoint?.endpointType)

    fun cycle() {
        val svc = service ?: return
        val currentType = svc.currentCallEndpoint.endpointType
        val startIndex = cycleOrder.indexOf(currentType).let { if (it < 0) 0 else it }
        for (step in 1..cycleOrder.size) {
            val candidateType = cycleOrder[(startIndex + step) % cycleOrder.size]
            val candidate = availableEndpoints.firstOrNull { it.endpointType == candidateType } ?: continue
            svc.requestCallEndpointChange(
                candidate,
                svc.mainExecutor,
                object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) = notifyChanged()
                    override fun onError(error: CallEndpointException) = Unit
                },
            )
            return
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() = listeners.toList().forEach { it() }

    private fun labelFor(type: Int?): String = when (type) {
        CallEndpoint.TYPE_SPEAKER -> "speaker"
        CallEndpoint.TYPE_BLUETOOTH -> "bluetooth"
        CallEndpoint.TYPE_WIRED_HEADSET -> "headset"
        else -> "earpiece"
    }
}
