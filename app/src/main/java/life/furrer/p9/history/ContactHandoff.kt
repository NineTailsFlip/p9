package life.furrer.p9.history

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet

/** The phone-number types offered when saving a number — the four every contacts app and CardDAV
 *  server round-trips cleanly. Anything more specific (a custom label) is the editor's job. */
private val PHONE_TYPES = listOf(
    "Mobile" to Phone.TYPE_MOBILE,
    "Work" to Phone.TYPE_WORK,
    "Home" to Phone.TYPE_HOME,
    "Other" to Phone.TYPE_OTHER,
)

/** "Save this number" as platform hand-offs, not an editor (docs/p9-plan.md M7): P9 never holds
 *  WRITE_CONTACTS. Both intents carry [ContactsContract.Intents.Insert.PHONE] plus the
 *  [ContactsContract.Intents.Insert.PHONE_TYPE] picked here, so whichever contacts app answers —
 *  C9's editor on a Nine phone, AOSP Contacts elsewhere — opens with the number already filed
 *  under the right type:
 *
 *  - "New contact": `ACTION_INSERT` on the contacts directory — a blank contact with this number.
 *  - "Add to existing contact": `ACTION_INSERT_OR_EDIT` — the contacts app asks which contact,
 *    then opens it with the number appended as a new phone field.
 *
 *  Each item is offered only when something can handle it (P9 never names C9 or requires it),
 *  which needs the matching `<queries>` entries in P9's manifest on Android 11+. */
object ContactHandoff {

    fun menuItems(activity: Activity, menuSheet: MenuSheet, number: String): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        if (newContactIntent(number, Phone.TYPE_MOBILE).resolveActivity(activity.packageManager) != null) {
            items.add(MenuItem("New contact") { askType(activity, menuSheet) { type -> newContactIntent(number, type) } })
        }
        if (addToExistingIntent(number, Phone.TYPE_MOBILE).resolveActivity(activity.packageManager) != null) {
            items.add(MenuItem("Add to existing contact") { askType(activity, menuSheet) { type -> addToExistingIntent(number, type) } })
        }
        return items
    }

    fun newContactIntent(number: String, phoneType: Int): Intent =
        Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
            .putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, phoneType)

    fun addToExistingIntent(number: String, phoneType: Int): Intent =
        Intent(Intent.ACTION_INSERT_OR_EDIT)
            .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
            .putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, phoneType)

    private fun askType(activity: Activity, menuSheet: MenuSheet, intentFor: (Int) -> Intent) {
        menuSheet.show(PHONE_TYPES.map { (label, type) -> MenuItem(label) { activity.startActivity(intentFor(type)) } })
    }
}
