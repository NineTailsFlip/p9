package life.furrer.p9.call

/** The observable list of calls [P9InCallService] currently knows about — the single source of
 *  truth every screen renders from (docs/p9-plan.md's "Telecom state is never mirrored": if a
 *  piece of UI state can be derived from here, derive it on every render, never cache it
 *  separately). A plain singleton, not a per-Activity instance — [P9InCallService] can outlive
 *  and run independently of whatever screen is currently showing. */
object CallRegistry {
    private val calls = mutableListOf<CallSession>()
    private val listeners = mutableListOf<() -> Unit>()

    fun all(): List<CallSession> = calls.toList()

    fun add(call: CallSession) {
        calls.add(call)
        notifyListeners()
    }

    fun remove(call: CallSession) {
        calls.removeAll { it.id == call.id }
        notifyListeners()
    }

    /** For a call whose identity hasn't changed but whose state/details have (ringing -> active,
     *  a capability appearing/disappearing) — [TelecomCallSession] calls this from its own
     *  `Call.Callback` so listeners re-render without polling. */
    fun notifyChanged() = notifyListeners()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }
}
