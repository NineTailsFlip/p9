package life.furrer.p9.settings

import life.furrer.nine.core.NineSurface
import life.furrer.nine.core.settings.NineSettingsActivity
import life.furrer.nine.core.settings.SectionScope
import life.furrer.nine.core.settings.SettingsScreen
import life.furrer.p9.telecom.DialerRole

private const val REQUEST_DIALER_ROLE = 102

/** P9's Settings — a Phone section on top of core's shared sections (Theme, Backup, About). SIM
 *  choice, confirm-before-dialing, vibrate-on-connect and call history length (M6) join this
 *  section once there's something for each of them to configure. */
class SettingsActivity : NineSettingsActivity() {
    override fun buildAppSections(screen: SettingsScreen, surface: NineSurface) {
        screen.section("Phone") { buildPhoneSection(this) }
    }

    private fun buildPhoneSection(scope: SectionScope) {
        scope.action(
            label = "Default phone app",
            value = { if (DialerRole.isHeld(this)) "granted" else "not granted" },
            onSelect = {
                if (!DialerRole.isHeld(this)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(DialerRole.requestIntent(this), REQUEST_DIALER_ROLE)
                }
            },
        )
    }
}
