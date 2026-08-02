package life.furrer.p9.call

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CallNotificationsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val call = FakeCallSession(id = "1", state = CallState.RINGING, displayName = "Nicole Furrer")

    private fun setInteractive(interactive: Boolean) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        shadowOf(powerManager).turnScreenOn(interactive)
    }

    @Test
    fun `incoming call uses the urgent channel and a full-screen intent when the screen is off`() {
        setInteractive(false)
        val notification = CallNotifications.incomingCallNotification(context, call)
        assertEquals("incoming_calls", notification.channelId)
        assertNotNull(notification.fullScreenIntent)
    }

    @Test
    fun `incoming call uses the quiet channel and no full-screen intent when the screen is already on`() {
        setInteractive(true)
        val notification = CallNotifications.incomingCallNotification(context, call)
        assertEquals("ongoing_call", notification.channelId)
        assertNull(notification.fullScreenIntent)
    }
}
