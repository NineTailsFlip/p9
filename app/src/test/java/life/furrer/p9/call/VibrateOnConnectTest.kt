package life.furrer.p9.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrateOnConnectTest {

    @Test
    fun `an outgoing call going from dialing to active just connected`() {
        assertTrue(justConnected(CallState.DIALING, CallState.ACTIVE, isOutgoing = true))
    }

    @Test
    fun `an incoming call reaching active did not just connect -- you already know you answered it`() {
        assertFalse(justConnected(CallState.RINGING, CallState.ACTIVE, isOutgoing = false))
    }

    @Test
    fun `resuming from hold back to active is not a fresh connect`() {
        assertFalse(justConnected(CallState.HOLDING, CallState.ACTIVE, isOutgoing = true))
    }

    @Test
    fun `already active to active again (a no-op state re-notify) is not a fresh connect`() {
        assertFalse(justConnected(CallState.ACTIVE, CallState.ACTIVE, isOutgoing = true))
    }

    @Test
    fun `going to any state other than active is never a connect`() {
        assertFalse(justConnected(CallState.DIALING, CallState.DISCONNECTED, isOutgoing = true))
    }
}
