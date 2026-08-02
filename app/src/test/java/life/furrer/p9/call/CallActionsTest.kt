package life.furrer.p9.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallActionsTest {

    @Test
    fun `the minimal row set is Mute, Speaker, Keypad, End call in that order`() {
        val rows = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = emptySet(), callCount = 1)
        assertEquals(listOf("Mute", "Speaker", "Keypad", "End call"), rows.map { it.label })
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.number })
    }

    @Test
    fun `End call is always last`() {
        val rows = CallActions.rowsFor(
            muted = false, audioRouteLabel = "earpiece", onHold = false,
            capabilities = setOf(CallCapability.HOLD, CallCapability.MERGE_CONFERENCE), callCount = 2,
        )
        assertEquals("End call", rows.last().label)
    }

    @Test
    fun `Hold only appears with the HOLD capability`() {
        val without = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = emptySet(), callCount = 1)
        assertEquals(emptyList<String>(), without.filter { it.label == "Hold" }.map { it.label })

        val with = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = setOf(CallCapability.HOLD), callCount = 1)
        assertEquals(listOf("Hold"), with.filter { it.label == "Hold" }.map { it.label })
    }

    @Test
    fun `dropping the HOLD capability shifts every row after it down by one number`() {
        val with = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = setOf(CallCapability.HOLD), callCount = 1)
        val without = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = emptySet(), callCount = 1)

        assertEquals(4, with.first { it.label == "Keypad" }.number)
        assertEquals(3, without.first { it.label == "Keypad" }.number)
        assertEquals(5, with.first { it.label == "End call" }.number)
        assertEquals(4, without.first { it.label == "End call" }.number)
    }

    @Test
    fun `Swap calls only appears with a second call`() {
        val one = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = emptySet(), callCount = 1)
        val two = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = emptySet(), callCount = 2)

        assertEquals(false, one.any { it.label == "Swap calls" })
        assertEquals(true, two.any { it.label == "Swap calls" })
    }

    @Test
    fun `Merge only appears with a second call AND the MERGE_CONFERENCE capability`() {
        val twoNoMerge = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = emptySet(), callCount = 2)
        val twoWithMerge = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = setOf(CallCapability.MERGE_CONFERENCE), callCount = 2)
        val oneWithMergeCapability = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = setOf(CallCapability.MERGE_CONFERENCE), callCount = 1)

        assertEquals(false, twoNoMerge.any { it.label == "Merge" })
        assertEquals(true, twoWithMerge.any { it.label == "Merge" })
        assertEquals(false, oneWithMergeCapability.any { it.label == "Merge" }) // capability alone isn't enough without a second call
    }

    @Test
    fun `Mute and Hold values reflect the current state`() {
        val rows = CallActions.rowsFor(muted = true, audioRouteLabel = "speaker", onHold = true, capabilities = setOf(CallCapability.HOLD), callCount = 1)
        assertEquals("on", rows.first { it.label == "Mute" }.value)
        assertEquals("on", rows.first { it.label == "Hold" }.value)
        assertEquals("speaker", rows.first { it.label == "Speaker" }.value)
    }

    @Test
    fun `keypadRowNumber tracks the row's current position, not a fixed slot`() {
        val with = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = setOf(CallCapability.HOLD), callCount = 1)
        val without = CallActions.rowsFor(muted = false, audioRouteLabel = "earpiece", onHold = false, capabilities = emptySet(), callCount = 1)

        assertEquals(4, CallActions.keypadRowNumber(with))
        assertEquals(3, CallActions.keypadRowNumber(without))
    }

    @Test
    fun `keypadRowNumber is null if the row list somehow has no Keypad row`() {
        assertNull(CallActions.keypadRowNumber(emptyList()))
    }
}
