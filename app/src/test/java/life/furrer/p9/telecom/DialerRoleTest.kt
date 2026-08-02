package life.furrer.p9.telecom

import life.furrer.nine.core.Nine
import life.furrer.nine.core.NineApp
import life.furrer.nine.core.NineSurface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DialerRoleTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Nine.init(NineApp(id = "dialerrole-test", name = "Test", tagline = "", surfaces = listOf(NineSurface("", "", "", emptyList())), settingsActivity = Any::class.java))
    }

    @Test
    fun `hasAskedBefore defaults to false and persists once marked`() {
        assertFalse(DialerRole.hasAskedBefore(context))
        DialerRole.markAsked(context)
        assertTrue(DialerRole.hasAskedBefore(context))
    }

    @Test
    fun `isHeld does not crash when RoleManager reports no role held`() {
        // A fresh test environment never holds any role -- this just documents that isHeld()
        // fails closed (false) rather than throwing, which is what matters for the first-run
        // gate: DialerActivity must still work perfectly well without the role.
        assertFalse(DialerRole.isHeld(context))
    }
}
