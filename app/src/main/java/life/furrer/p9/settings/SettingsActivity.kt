package life.furrer.p9.settings

import life.furrer.nine.core.NineSurface
import life.furrer.nine.core.settings.NineSettingsActivity
import life.furrer.nine.core.settings.SettingsScreen

/** P9's Settings — nothing beyond core's shared sections (Theme, Backup, About) yet. The Phone
 *  section (default phone app, SIM choice, confirm-before-dialing, ...) lands with M4/M6, once
 *  there is something for it to configure. */
class SettingsActivity : NineSettingsActivity() {
    override fun buildAppSections(screen: SettingsScreen, surface: NineSurface) {
        // No app-specific sections yet.
    }
}
