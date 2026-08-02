package life.furrer.p9.dial

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import life.furrer.nine.contacts.ContactEntry
import life.furrer.nine.contacts.ContactStore
import life.furrer.nine.contacts.ContactsActivity
import life.furrer.nine.contacts.ContactDetailActivity
import life.furrer.nine.core.Nine
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.PermissionScreen
import life.furrer.nine.core.ui.RowBinder
import life.furrer.nine.core.ui.RowListView
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.hideStatusBar
import life.furrer.nine.core.util.Async
import life.furrer.p9.history.CallDetailActivity
import life.furrer.p9.history.CallLogEntry
import life.furrer.p9.history.CallLogStore
import life.furrer.p9.telecom.DialerRole
import life.furrer.p9.telecom.placeCall

private const val REQUEST_PERMISSIONS = 100
private const val REQUEST_CALL_PHONE_PERMISSION = 101
private const val REQUEST_DIALER_ROLE = 102
private const val SIDE_PADDING_DP = 16

// The full 0.1.0 default (docs/p9-plan.md's "Call history length" Settings row) is 200; that row
// doesn't exist until M6, so this is a plain constant until then.
private const val HISTORY_LIMIT = 200

/** P9's only launcher entry: a dial string built straight from digit keys (no T9/IME involved —
 *  a phone number isn't typed via word prediction), doubling as a search across contacts and call
 *  history, and as call history browsing when nothing is typed. Dials through
 *  `TelecomManager.placeCall` once P9 holds the dialer role, `ACTION_CALL` otherwise. */
class DialerActivity : Activity() {

    private var dialString = DialString()
    private var allContacts: List<ContactEntry> = emptyList()
    private var callLog: List<CallLogEntry> = emptyList()
    private var missedOnly = false
    private var rows: List<DialRow> = emptyList()
    private var pendingDialNumber: String? = null

    private lateinit var dialStringText: TextView
    private lateinit var rowListView: RowListView
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet
    private lateinit var permissionScreen: PermissionScreen

    private var contactsObserver: ContentObserver? = null
    private var callLogObserver: ContentObserver? = null

    // Selecting a menu item via OK closes the sheet as a side effect of handling that same key's
    // ACTION_DOWN — see ContactsActivity's own menuConsumedKeyUp for the full reasoning this
    // mirrors (the matching ACTION_UP must not also fall through into this screen's own OK
    // handling).
    private var menuConsumedKeyUp: Int? = null

    private val longPressHandler = Handler(Looper.getMainLooper())
    private val starKey = LongPressKey(::openMenu, ::openSettings)
    private val zeroKey = LongPressKey({ appendChar('0') }, { appendChar('+') })
    private val backKey = LongPressKey(::backspaceOrClose, ::clearDialString)

    private val binder = object : RowBinder {
        override fun rowCount(): Int = rows.size
        override fun createRowView(context: Context): View = createDialRowContainer(context)
        override fun bind(view: View, index: Int, selected: Boolean) {
            bindDialRow(view as LinearLayout, rows[index], selected)
        }
        override fun rowHeight(index: Int): Int = dialRowHeightDp(rows[index], index == rowListView.selectedIndex)
        override fun isSelectable(index: Int): Boolean = rows[index].isSelectable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        dialStringText = TextView(this).apply {
            textSize = 24f
            setTextColor(Palette.WHITE)
            setPadding(dp(SIDE_PADDING_DP), dp(10), dp(SIDE_PADDING_DP), dp(6))
            visibility = View.GONE
        }
        rowListView = RowListView(this).apply { setBinder(binder) }
        hintBar = HintBar(this)
        menuSheet = MenuSheet(this)
        permissionScreen = PermissionScreen(this).apply {
            onGrant = { requestNeededPermissions() }
            onLeave = { finish() }
        }

        val mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dialStringText)
            addView(rowListView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(hintBar)
        }
        val root = FrameLayout(this).apply {
            addView(mainColumn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(permissionScreen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
        hideStatusBar()
        updateHint()

        if (hasNeededPermissions()) {
            loadData()
        } else {
            permissionScreen.show(permissionExplainer())
        }
    }

    private fun hasNeededPermissions(): Boolean =
        ContactStore.hasPermission(this) && CallLogStore.hasReadPermission(this)

    private fun permissionExplainer(): String =
        "P9 needs access to your Contacts and Call log, to search your address book and show who's calling.\n\n" +
            "P9 has no network permission — it never talks to a server."

    // POST_NOTIFICATIONS rides along with the two blocking permissions (docs/p9-plan.md requests
    // them "together") but isn't part of hasNeededPermissions() -- denying it means a ringing call
    // never shows its full-screen notification (M5), not that the whole dialer is unusable, so it
    // must never gate the permission screen the way READ_CONTACTS/READ_CALL_LOG do.
    private fun requestNeededPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_PERMISSIONS,
        )
    }

    override fun onResume() {
        super.onResume()
        if (hasNeededPermissions()) {
            permissionScreen.hide()
            registerContentObservers()
            loadData()
            offerDialerRoleOnFirstRun()
        } else if (!permissionScreen.isShowing) {
            permissionScreen.show(permissionExplainer())
        }
    }

    // Asked once, ever (docs/p9-plan.md): after this, granting the role lives as a Settings row
    // instead of a repeated system prompt every launch.
    private fun offerDialerRoleOnFirstRun() {
        if (DialerRole.isHeld(this) || !DialerRole.isAvailable(this) || DialerRole.hasAskedBefore(this)) return
        DialerRole.markAsked(this)
        @Suppress("DEPRECATION")
        startActivityForResult(DialerRole.requestIntent(this), REQUEST_DIALER_ROLE)
    }

    override fun onPause() {
        super.onPause()
        contactsObserver?.let { contentResolver.unregisterContentObserver(it) }
        callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
        contactsObserver = null
        callLogObserver = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (hasNeededPermissions()) {
                    permissionScreen.hide()
                    loadData()
                } else {
                    permissionScreen.show(permissionExplainer())
                }
            }
            REQUEST_CALL_PHONE_PERMISSION -> {
                val number = pendingDialNumber
                pendingDialNumber = null
                if (number != null && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    placeCall(this, number)
                }
            }
        }
    }

    private fun registerContentObservers() {
        val onChange = { loadData() }
        contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }.also { contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, it) }
        callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }.also { contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, it) }
    }

    private fun loadData() {
        Async.run(
            background = { ContactStore.queryContacts(this) to CallLogStore.queryCallLog(this, HISTORY_LIMIT) },
            onResult = { (contacts, log) ->
                allContacts = contacts
                callLog = log
                rebuildRows()
            },
        )
    }

    private fun rebuildRows() {
        rows = buildDialRows(dialString.digits, allContacts, callLog, missedOnly, LocalDate.now())
        rowListView.notifyDataChanged()
        dialStringText.text = dialString.formatted()
        dialStringText.visibility = if (dialString.isEmpty) View.GONE else View.VISIBLE
        updateHint()
    }

    private fun updateHint() {
        hintBar.setText(
            if (dialString.isEmpty) {
                val toggleLabel = if (missedOnly) "# all" else "# missed"
                "* menu · $toggleLabel · ← → day"
            } else {
                "call dial · ‹ back"
            },
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (permissionScreen.isShowing) {
            if (event.action == KeyEvent.ACTION_DOWN) permissionScreen.handleKeyDown(event.keyCode)
            return true
        }
        if (menuSheet.isShowing) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                menuSheet.handleKeyDown(event.keyCode, event)
                menuConsumedKeyUp = event.keyCode
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == menuConsumedKeyUp) {
            menuConsumedKeyUp = null
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_STAR -> return dispatchLongPressKey(starKey, event)
            KeyEvent.KEYCODE_0 -> return dispatchLongPressKey(zeroKey, event)
            KeyEvent.KEYCODE_BACK -> return dispatchLongPressKey(backKey, event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                    appendChar('0' + (event.keyCode - KeyEvent.KEYCODE_0))
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    rowListView.moveSelection(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    rowListView.moveSelection(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    jumpSectionHeader(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    jumpSectionHeader(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    openSelected()
                    return true
                }
                KeyEvent.KEYCODE_POUND -> {
                    toggleMissedOnly()
                    return true
                }
                KeyEvent.KEYCODE_CALL -> {
                    dial()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** The long-press-vs-tap distinction for `*`/`0`/Back is normally the framework's own job via
     *  `onKeyLongPress` — but that relies on the key reaching `onKeyDown` via the default dispatch,
     *  which this screen's `dispatchKeyEvent` override bypasses for these three keys (same
     *  reasoning as ContactsActivity/CalendarActivity's own star handling). Tracked by hand
     *  instead: a delayed callback started on DOWN, cancelled and checked on UP. */
    private fun dispatchLongPressKey(key: LongPressKey, event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> key.onDown()
            KeyEvent.ACTION_UP -> key.onUp()
        }
        return true
    }

    private inner class LongPressKey(private val onTap: () -> Unit, private val onHold: () -> Unit) {
        private var seenDown = false
        private var fired = false
        private val runnable = Runnable {
            fired = true
            onHold()
        }

        fun onDown() {
            seenDown = true
            fired = false
            longPressHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
        }

        fun onUp() {
            longPressHandler.removeCallbacks(runnable)
            if (seenDown && !fired) onTap()
            seenDown = false
        }
    }

    private fun appendChar(char: Char) {
        dialString = dialString.append(char)
        rebuildRows()
    }

    /** Back deletes one digit rather than clearing the whole query, the one key in P9 whose
     *  meaning differs from `:contacts`' own list screens (docs/p9-plan.md decision 1) — a dial
     *  string is edited digit-by-digit, a search query is not. */
    private fun backspaceOrClose() {
        if (dialString.isEmpty) {
            finish()
        } else {
            dialString = dialString.backspace()
            rebuildRows()
        }
    }

    private fun clearDialString() {
        dialString = dialString.clear()
        rebuildRows()
    }

    private fun jumpSectionHeader(direction: Int) {
        val current = rowListView.selectedIndex
        val headerIndices = rows.withIndex().filter { it.value is DialRow.SectionHeader }.map { it.index }
        if (headerIndices.isEmpty()) return
        if (direction > 0) {
            val next = headerIndices.firstOrNull { it > current } ?: return
            rowListView.setSelectedIndex(next + 1)
        } else {
            val currentGroupStart = headerIndices.lastOrNull { it <= current } ?: return
            val prev = headerIndices.lastOrNull { it < currentGroupStart } ?: return
            rowListView.setSelectedIndex(prev + 1)
        }
    }

    private fun toggleMissedOnly() {
        missedOnly = !missedOnly
        rebuildRows()
    }

    private fun openSelected() {
        when (val row = rows.getOrNull(rowListView.selectedIndex)) {
            is DialRow.ContactMatch -> openContactDetail(row.contact.id)
            is DialRow.NumberMatch -> openContactDetail(row.contact.id)
            is DialRow.RecentMatch -> openCallDetail(row.entry.number)
            is DialRow.HistoryEntry -> openCallDetail(row.entry.number)
            is DialRow.TypedNumber -> placeCallOrRequestPermission(row.digits)
            else -> if (dialString.isDialable) placeCallOrRequestPermission(dialString.digits)
        }
    }

    private fun openContactDetail(contactId: Long) {
        startActivity(
            Intent(this, ContactDetailActivity::class.java).putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contactId),
        )
    }

    private fun openCallDetail(number: String) {
        startActivity(Intent(this, CallDetailActivity::class.java).putExtra(CallDetailActivity.EXTRA_NUMBER, number))
    }

    private fun openMenu() {
        val items = mutableListOf(MenuItem("Contacts") { startActivity(Intent(this, ContactsActivity::class.java)) })
        if (!DialerRole.isHeld(this) && DialerRole.isAvailable(this)) {
            @Suppress("DEPRECATION")
            items.add(MenuItem("Set P9 as default phone app") { startActivityForResult(DialerRole.requestIntent(this), REQUEST_DIALER_ROLE) })
        }
        items.add(MenuItem("Settings") { openSettings() })
        menuSheet.show(items)
    }

    private fun openSettings() {
        startActivity(Intent(this, Nine.app.settingsActivity))
    }

    /** The call key's target follows the same "never guess" rule as `:contacts`' own dial(): the
     *  selected row's number, or, if nothing is selected/matched, the typed digits verbatim. */
    private fun dial() {
        when (val selected = rows.getOrNull(rowListView.selectedIndex)) {
            is DialRow.ContactMatch -> dialContact(selected.contact)
            is DialRow.NumberMatch -> placeCallOrRequestPermission(selected.matchedNumber)
            is DialRow.RecentMatch -> placeCallOrRequestPermission(selected.entry.number)
            is DialRow.HistoryEntry -> placeCallOrRequestPermission(selected.entry.number)
            is DialRow.TypedNumber -> placeCallOrRequestPermission(selected.digits)
            else -> if (dialString.isDialable) placeCallOrRequestPermission(dialString.digits)
        }
    }

    private fun dialContact(contact: ContactEntry) {
        val numbers = contact.phoneNumbers
        when {
            numbers.isEmpty() -> {}
            numbers.size == 1 -> placeCallOrRequestPermission(numbers.first().number)
            else -> {
                val primary = numbers.firstOrNull { it.isSuperPrimary }
                if (primary != null) {
                    placeCallOrRequestPermission(primary.number)
                } else {
                    menuSheet.show(
                        numbers.map { n -> MenuItem("${n.label ?: "Number"}: ${n.number}") { placeCallOrRequestPermission(n.number) } },
                    )
                }
            }
        }
    }

    private fun placeCallOrRequestPermission(number: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            placeCall(this, number)
        } else {
            pendingDialNumber = number
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE_PERMISSION)
        }
    }

    private fun dp(value: Int): Int = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
