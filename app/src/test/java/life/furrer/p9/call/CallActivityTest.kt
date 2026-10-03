package life.furrer.p9.call

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallActivityTest {

    @Before
    fun setUp() {
        CallRegistry.all().forEach { CallRegistry.remove(it) }
        AudioRouting.reset()
    }

    @After
    fun tearDown() {
        ProximityScreenOff.setCoveredForTest(false)
        CallRegistry.all().forEach { CallRegistry.remove(it) }
        AudioRouting.reset()
    }

    private fun down(activity: CallActivity, keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0))
    }

    private fun up(activity: CallActivity, keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0))
    }

    @Test
    fun `finishes immediately when there is no call to show`() {
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `the call key answers a ringing call`() {
        val call = FakeCallSession("1", state = CallState.RINGING)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_CALL)

        assertEquals(CallState.ACTIVE, call.state)
    }

    @Test
    fun `volume keys are consumed while ringing, both halves`() {
        CallRegistry.add(FakeCallSession("1", state = CallState.RINGING))
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        for (key in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            assertTrue(activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0)))
            assertTrue(activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, key, 0)))
        }
    }

    @Test
    fun `volume keys are not consumed once the call is active`() {
        CallRegistry.add(FakeCallSession("1", state = CallState.ACTIVE))
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        for (key in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            assertFalse(activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0)))
            assertFalse(activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, key, 0)))
        }
    }

    @Test
    fun `pressing 1 also answers a ringing call, same as OK`() {
        val call = FakeCallSession("1", state = CallState.RINGING)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_1)

        assertEquals(CallState.ACTIVE, call.state)
    }

    @Test
    fun `back rejects a ringing call rather than leaving the screen`() {
        val call = FakeCallSession("1", state = CallState.RINGING)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_BACK)

        assertEquals(CallState.DISCONNECTED, call.state)
    }

    @Test
    fun `end key disconnects an active call`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_ENDCALL)

        assertTrue(call.disconnected)
    }

    @Test
    fun `a digit outside the ringing rows never plays a tone or crashes`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_5)

        assertEquals(CallState.ACTIVE, call.state)
        assertTrue(!call.disconnected)
        assertTrue(call.dtmfTonesPlayed.isEmpty())
    }

    @Test
    fun `pressing a row's own number invokes it directly -- 1 toggles Mute`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        assertTrue(!AudioRouting.isMuted())
        down(activity, KeyEvent.KEYCODE_1) // row 1 is always Mute (see CallActionsTest)
        assertTrue(AudioRouting.isMuted())
    }

    @Test
    fun `entering keypad mode via its own number, then a digit plays and stops a DTMF tone`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        // No Hold capability, single call -> rows are Mute(1) Speaker(2) Keypad(3) End call(4).
        down(activity, KeyEvent.KEYCODE_3)
        down(activity, KeyEvent.KEYCODE_5)
        up(activity, KeyEvent.KEYCODE_5)

        assertEquals(listOf('5'), call.dtmfTonesPlayed)
        assertTrue(call.dtmfStopped)
        assertTrue(!call.disconnected) // the same digit never also picks an action row
    }

    @Test
    fun `back exits keypad mode without ending the call`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_3) // enter keypad mode
        down(activity, KeyEvent.KEYCODE_BACK) // exit keypad mode
        down(activity, KeyEvent.KEYCODE_1) // back on the action list -- 1 is Mute again

        assertTrue(AudioRouting.isMuted())
        assertTrue(!call.disconnected)
    }

    @Test
    fun `back on the action list leaves the screen without ending the call`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_BACK)

        assertTrue(activity.isFinishing)
        assertTrue(!call.disconnected)
    }

    @Test
    fun `Hold only appears -- and is reachable -- with the HOLD capability`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE, capabilities = setOf(CallCapability.HOLD))
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()

        // Rows: Mute(1) Speaker(2) Hold(3) Keypad(4) End call(5).
        down(activity, KeyEvent.KEYCODE_3)

        assertEquals(CallState.HOLDING, call.state)
    }

    @Test
    fun `keys against the cheek do nothing`() {
        val call = FakeCallSession("1", state = CallState.ACTIVE)
        CallRegistry.add(call)
        val activity = Robolectric.buildActivity(CallActivity::class.java).setup().get()
        ProximityScreenOff.setCoveredForTest(true)

        down(activity, KeyEvent.KEYCODE_ENDCALL)
        down(activity, KeyEvent.KEYCODE_BACK)

        assertEquals(CallState.ACTIVE, call.state)
        assertFalse(activity.isFinishing)
    }
}
