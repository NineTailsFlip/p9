package life.furrer.p9.call

/** One numbered row of the active-call action list. [value] is the trailing status text ("off",
 *  "speaker") for a toggle-shaped row, empty for a plain action like "Keypad"/"End call". */
data class CallActionRow(val number: Int, val label: String, val value: String = "")

/** Pure: call state plus the handful of things that live outside any single [CallSession]
 *  (mute/hold are read straight off the call by the caller; the current audio route is
 *  InCallService-level, from [AudioRouting]) in, the active-call action list out — the thing
 *  docs/p9-plan.md calls out as what actually gets unit-tested. Rows are derived, never
 *  hardcoded: Hold only appears with [CallCapability.HOLD], Swap/Merge only with a second call
 *  (and Merge only with [CallCapability.MERGE_CONFERENCE] too), so a row's number shifts as
 *  capabilities come and go — callers must never hardcode a row's number (see [keypadRowNumber]),
 *  only look it up by label. End call is always last, so a stray OK on a freshly-rendered list
 *  can never hang up by accident. */
object CallActions {
    fun rowsFor(
        muted: Boolean,
        audioRouteLabel: String,
        onHold: Boolean,
        capabilities: Set<CallCapability>,
        callCount: Int,
    ): List<CallActionRow> {
        val rows = mutableListOf<CallActionRow>()
        var n = 1
        rows.add(CallActionRow(n++, "Mute", if (muted) "on" else "off"))
        rows.add(CallActionRow(n++, "Speaker", audioRouteLabel))
        if (CallCapability.HOLD in capabilities) {
            rows.add(CallActionRow(n++, "Hold", if (onHold) "on" else "off"))
        }
        if (callCount > 1) {
            rows.add(CallActionRow(n++, "Swap calls"))
            if (CallCapability.MERGE_CONFERENCE in capabilities) {
                rows.add(CallActionRow(n++, "Merge"))
            }
        }
        rows.add(CallActionRow(n++, "Keypad"))
        rows.add(CallActionRow(n, "End call"))
        return rows
    }

    /** For the hint bar, which must name the Keypad row by whatever number it currently has, not
     *  a hardcoded one (docs/p9-plan.md). */
    fun keypadRowNumber(rows: List<CallActionRow>): Int? = rows.firstOrNull { it.label == "Keypad" }?.number
}
