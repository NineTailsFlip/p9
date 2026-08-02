package life.furrer.p9.dial

import android.view.KeyEvent
import life.furrer.p9.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DialerActivityTest {

    @Test
    fun `launches without crashing`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()
        assertNotNull(activity)
    }

    @Test
    fun `holding star opens Settings`() {
        val activity = Robolectric.buildActivity(DialerActivity::class.java).setup().get()

        activity.onKeyLongPress(KeyEvent.KEYCODE_STAR, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_STAR, 0))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started.component?.className)
    }
}
