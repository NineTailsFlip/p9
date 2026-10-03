package life.furrer.p9.call

import life.furrer.p9.call.CallState.ACTIVE
import life.furrer.p9.call.CallState.DIALING
import life.furrer.p9.call.CallState.HOLDING
import life.furrer.p9.call.CallState.RINGING
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityScreenOffTest {

    @Test
    fun `applies to a dialing, active or held call on the earpiece`() {
        assertTrue(wantsProximityScreenOff(listOf(DIALING), onEarpiece = true, keypadOpen = false))
        assertTrue(wantsProximityScreenOff(listOf(ACTIVE), onEarpiece = true, keypadOpen = false))
        assertTrue(wantsProximityScreenOff(listOf(HOLDING), onEarpiece = true, keypadOpen = false))
    }

    @Test
    fun `never while anything rings, so a waiting call can be seen`() {
        assertFalse(wantsProximityScreenOff(listOf(RINGING), onEarpiece = true, keypadOpen = false))
        assertFalse(wantsProximityScreenOff(listOf(ACTIVE, RINGING), onEarpiece = true, keypadOpen = false))
    }

    @Test
    fun `not off the earpiece or with the keypad open`() {
        assertFalse(wantsProximityScreenOff(listOf(ACTIVE), onEarpiece = false, keypadOpen = false))
        assertFalse(wantsProximityScreenOff(listOf(ACTIVE), onEarpiece = true, keypadOpen = true))
    }

    @Test
    fun `not without a call`() {
        assertFalse(wantsProximityScreenOff(emptyList(), onEarpiece = true, keypadOpen = false))
    }

    @Test
    fun `near is under 5cm or under a binary sensor's range`() {
        assertTrue(isProximityNear(0f, maximumRange = 5f))
        assertFalse(isProximityNear(5f, maximumRange = 5f))
        assertTrue(isProximityNear(3f, maximumRange = 10f))
        assertFalse(isProximityNear(8f, maximumRange = 10f))
        assertTrue(isProximityNear(0f, maximumRange = 1f))
        assertFalse(isProximityNear(1f, maximumRange = 1f))
    }
}
