package life.furrer.p9.settings

import android.app.role.RoleManager
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import life.furrer.nine.core.manual.ManualActivity
import life.furrer.nine.core.settings.NineSettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Covers P9's Phone section (just "Default phone app" so far, docs/p9-plan.md M4) on top of
 *  core's shared Theme/Backup/About sections, the same shape as C9's own SettingsActivityTest. */
@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun buildActivity(): SettingsActivity =
        Robolectric.buildActivity(
            SettingsActivity::class.java,
            NineSettingsActivity.intentFor(context, SettingsActivity::class.java, ""),
        ).setup().get()

    private fun rowContainer(activity: SettingsActivity): LinearLayout {
        val scrollView = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        return scrollView.getChildAt(0) as LinearLayout
    }

    private fun isSectionHeader(text: CharSequence): Boolean =
        text.isNotEmpty() && text.toString() == text.toString().uppercase()

    private fun rows(activity: SettingsActivity): List<TextView> =
        (0 until rowContainer(activity).childCount)
            .map { rowContainer(activity).getChildAt(it) as TextView }
            .filterNot { isSectionHeader(it.text) }

    private fun moveTo(activity: SettingsActivity, index: Int) {
        repeat(index) { activity.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0)) }
    }

    private fun press(activity: SettingsActivity, keyCode: Int) {
        activity.onKeyDown(keyCode, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0))
    }

    @Test
    fun `gets core's shared sections plus the Phone section`() {
        val labels = rows(buildActivity()).map { it.text.toString() }

        assertTrue(labels.any { it.contains("Font") })
        assertTrue(labels.any { it.contains("Hint bar") })
        assertTrue(labels.contains("Export settings"))
        assertTrue(labels.contains("Import settings"))
        assertTrue(labels.contains("Intro"))
        assertTrue(labels.contains("Key mapping"))
        assertTrue(labels.contains("About"))
        assertTrue(labels.contains("Open website"))
        assertTrue(labels.any { it.contains("Default phone app") })
    }

    @Test
    fun `Default phone app shows another app before the role is held`() {
        val activity = buildActivity()
        assertEquals("Default phone app: another app", rows(activity)[0].text.toString())
    }

    @Test
    fun `Default phone app shows P9 once the role is held`() {
        shadowOf(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_DIALER)
        val activity = buildActivity()
        assertEquals("Default phone app: P9", rows(activity)[0].text.toString())
    }

    @Test
    fun `selecting Default phone app requests the role`() {
        shadowOf(context.getSystemService(RoleManager::class.java)).addAvailableRole(RoleManager.ROLE_DIALER)
        val activity = buildActivity()
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)

        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started != null)
    }

    @Test
    fun `selecting Default phone app does nothing when the role is unavailable`() {
        val activity = buildActivity()
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `selecting Intro opens core's ManualActivity for P9's single surface`() {
        val activity = buildActivity()
        val introIndex = rows(activity).indexOfFirst { it.text == "Intro" }
        moveTo(activity, introIndex)
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ManualActivity::class.java.name, started.component?.className)
        assertEquals(ManualActivity.PAGE_INTRO, started.getStringExtra(ManualActivity.EXTRA_PAGE))
    }
}
