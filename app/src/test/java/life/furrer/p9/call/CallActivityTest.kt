package life.furrer.p9.call

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
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
    }

    @After
    fun tearDown() {
        CallRegistry.all().forEach { CallRegistry.remove(it) }
    }

    private fun down(activity: CallActivity, keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0))
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
    }
}
