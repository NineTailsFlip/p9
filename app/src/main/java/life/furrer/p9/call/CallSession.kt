package life.furrer.p9.call

enum class CallState { DIALING, RINGING, ACTIVE, HOLDING, DISCONNECTED }

enum class CallCapability { HOLD, MERGE_CONFERENCE, MUTE }

/** Everything the UI needs from a live call. `android.telecom.Call` is final and unmockable
 *  without a mocking dependency this repo doesn't take on, so this is implemented once for real
 *  over it ([TelecomCallSession]) and by a fake in tests — the same shape as the suite's
 *  `Fake*Provider` classes, for the same reason. [CallRegistry], the M6 `CallActions` and
 *  `CallActivity` see only this interface, so the whole call screen — every state transition,
 *  every derived action row — is testable on the JVM; only [P9InCallService]'s own adapter
 *  touches Telecom, and it's thin enough to verify by hand on the device. */
interface CallSession {
    val id: String
    val state: CallState
    val handle: String?
    val displayName: String?
    val connectTimeMillis: Long
    val capabilities: Set<CallCapability>

    fun answer()
    fun reject()
    fun disconnect()
    fun hold()
    fun unhold()
    fun playDtmf(c: Char)
    fun stopDtmf()
}
