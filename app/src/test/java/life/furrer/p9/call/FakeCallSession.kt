package life.furrer.p9.call

/** The test double [CallSession]'s own doc calls for — a plain in-memory stand-in for a real
 *  `android.telecom.Call`. */
class FakeCallSession(
    override val id: String,
    override var state: CallState = CallState.DIALING,
    override val handle: String? = null,
    override val displayName: String? = null,
    override val connectTimeMillis: Long = 0L,
    override val capabilities: Set<CallCapability> = emptySet(),
    override val isOutgoing: Boolean = false,
) : CallSession {
    var disconnected = false
        private set
    val dtmfTonesPlayed = mutableListOf<Char>()
    var dtmfStopped = false
        private set

    override fun answer() {
        state = CallState.ACTIVE
    }

    override fun reject() {
        state = CallState.DISCONNECTED
    }

    override fun disconnect() {
        disconnected = true
        state = CallState.DISCONNECTED
    }

    override fun hold() {
        state = CallState.HOLDING
    }

    override fun unhold() {
        state = CallState.ACTIVE
    }

    override fun playDtmf(c: Char) {
        dtmfTonesPlayed.add(c)
    }

    override fun stopDtmf() {
        dtmfStopped = true
    }
}
