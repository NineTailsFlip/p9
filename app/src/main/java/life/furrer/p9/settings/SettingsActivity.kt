package life.furrer.p9.settings

import life.furrer.nine.core.NineSurface
import life.furrer.nine.core.settings.NineSettingsActivity
import life.furrer.nine.core.settings.SectionScope
import life.furrer.nine.core.settings.SettingsScreen
import life.furrer.p9.call.VibrateOnConnectSetting
import life.furrer.p9.telecom.DialerRole

private const val REQUEST_DIALER_ROLE = 102

/** P9's Settings — a Phone section on top of core's shared sections (Theme, Backup, About). SIM
 *  choice, confirm-before-dialing and call history length join this section once there's a real
 *  SIM/emergency-number/history-limit story to configure. */
class SettingsActivity : NineSettingsActivity() {
    override fun buildAppSections(screen: SettingsScreen, surface: NineSurface) {
        screen.section("Phone") { buildPhoneSection(this) }
    }

    private fun buildPhoneSection(scope: SectionScope) {
        scope.action(
            label = "Default phone app",
            value = { if (DialerRole.isHeld(this)) "P9" else "another app" },
            onSelect = {
                if (!DialerRole.isHeld(this) && DialerRole.isAvailable(this)) {
                    @Suppress("DEPRECATION")
                    DialerRole.requestIntent(this)?.let { startActivityForResult(it, REQUEST_DIALER_ROLE) }
                }
            },
        )
        scope.toggle(
            label = "Vibrate on connect",
            get = { VibrateOnConnectSetting.isEnabled(this) },
            set = { VibrateOnConnectSetting.setEnabled(this, it) },
        )
        numberFormatRow(scope)
    }
}
