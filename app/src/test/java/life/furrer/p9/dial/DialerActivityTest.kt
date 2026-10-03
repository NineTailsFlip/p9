package life.furrer.p9.dial

import android.os.Looper
import android.provider.CallLog
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import java.time.Duration
import life.furrer.nine.contacts.FakeContactsProvider
import life.furrer.p9.history.FakeCallLogProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DialerActivityTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        FakeContactsProvider.reset()
        Robolectric.buildContentProvider(FakeContactsProvider::class.java).create("com.android.contacts")
        FakeCallLogProvider.reset()
        Robolectric.buildContentProvider(FakeCallLogProvider::class.java).create(CallLog.AUTHORITY)
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.READ_CALL_LOG)
    }

    private fun down(activity: DialerActivity, keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0))
    }

    private fun up(activity: DialerActivity, keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun tap(activity: DialerActivity, keyCode: Int) {
        down(activity, keyCode)
        up(activity, keyCode)
    }

    private fun dialStringOf(activity: DialerActivity): TextView {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as FrameLayout
        val mainColumn = root.getChildAt(0) as android.widget.LinearLayout
        return mainColumn.getChildAt(0) as TextView
    }

    @Test
    fun `launches without crashing and shows no dial string yet`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()
        assertEquals(android.view.View.GONE, dialStringOf(activity).visibility)
    }

    @Test
    fun `without contacts or call log access the keypad still types, so 112 can always be dialed`() {
        shadowOf(context).denyPermissions(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.READ_CALL_LOG)
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()

        tap(activity, KeyEvent.KEYCODE_1)
        tap(activity, KeyEvent.KEYCODE_1)
        tap(activity, KeyEvent.KEYCODE_2)

        assertEquals("112", dialStringOf(activity).text.toString())
    }

    @Test
    fun `digit keys build up the dial string`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()

        tap(activity, KeyEvent.KEYCODE_0)
        tap(activity, KeyEvent.KEYCODE_7)
        tap(activity, KeyEvent.KEYCODE_9)

        assertEquals("079", dialStringOf(activity).text.toString())
        assertEquals(android.view.View.VISIBLE, dialStringOf(activity).visibility)
    }

    @Test
    fun `back deletes one digit rather than clearing the whole string`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()
        tap(activity, KeyEvent.KEYCODE_0)
        tap(activity, KeyEvent.KEYCODE_7)

        tap(activity, KeyEvent.KEYCODE_BACK)

        assertEquals("0", dialStringOf(activity).text.toString())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `back on an empty dial string closes the screen`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()
        tap(activity, KeyEvent.KEYCODE_BACK)
        assertTrue(activity.isFinishing)
    }

    // The long-press-vs-tap timer is a manually posted Handler callback (see DialerActivity's
    // own LongPressKey doc), not the framework's onKeyLongPress — advancing Robolectric's
    // simulated main-thread clock past the long-press timeout is what actually fires it.
    private fun holdPastLongPressTimeout() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 50L))
    }

    @Test
    fun `holding zero inserts a plus instead of a zero`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()

        down(activity, KeyEvent.KEYCODE_0)
        holdPastLongPressTimeout()
        up(activity, KeyEvent.KEYCODE_0)

        assertEquals("+", dialStringOf(activity).text.toString())
    }

    @Test
    fun `holding back clears the whole dial string instead of just one digit`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()
        tap(activity, KeyEvent.KEYCODE_0)
        tap(activity, KeyEvent.KEYCODE_7)

        down(activity, KeyEvent.KEYCODE_BACK)
        holdPastLongPressTimeout()
        up(activity, KeyEvent.KEYCODE_BACK)

        assertEquals("", dialStringOf(activity).text.toString())
        assertFalse(activity.isFinishing)
    }
}
