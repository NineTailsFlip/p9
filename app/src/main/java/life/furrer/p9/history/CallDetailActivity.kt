package life.furrer.p9.history

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import life.furrer.nine.contacts.ContactDetailActivity
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.hideStatusBar
import life.furrer.nine.core.util.Async
import life.furrer.nine.core.util.DateFmt
import life.furrer.p9.telecom.placeCall

private const val SIDE_PADDING_DP = 16
private const val REQUEST_CALL_PHONE_PERMISSION = 101
private const val HISTORY_LIMIT = 200

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** One number's history — every call to and from it, newest first, in a plain LinearLayout/
 *  ScrollView. `*` menu: Open contact (a known number) or Add to contacts (a platform hand-off,
 *  hidden entirely if nothing can handle it — P9 never edits the address book itself), and
 *  Delete these entries. Reached with OK from a history row in
 *  [life.furrer.p9.dial.DialerActivity]. */
class CallDetailActivity : Activity() {

    private var number: String = ""
    private var pendingDial = false
    private var loadedEntries: List<CallLogEntry> = emptyList()

    private lateinit var titleText: TextView
    private lateinit var rowContainer: LinearLayout
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        number = intent.getStringExtra(EXTRA_NUMBER) ?: ""
        if (number.isEmpty()) {
            finish()
            return
        }

        titleText = TextView(this).apply {
            textSize = 20f
            setTextColor(Palette.WHITE)
            setPadding(dp(SIDE_PADDING_DP), dp(12), dp(SIDE_PADDING_DP), dp(8))
        }
        rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollView = ScrollView(this).apply {
            addView(
                LinearLayout(this@CallDetailActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(titleText)
                    addView(rowContainer)
                },
            )
        }
        hintBar = HintBar(this).apply { setText("* menu · call dial · ‹ back") }
        menuSheet = MenuSheet(this)
        val mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(hintBar)
        }
        val root = FrameLayout(this).apply {
            addView(mainColumn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
        hideStatusBar()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private fun load() {
        val target = number
        Async.run(
            background = { CallLogStore.queryCallLog(this, HISTORY_LIMIT, number = target) },
            onResult = { entries ->
                if (entries.isEmpty()) {
                    finish()
                } else {
                    render(entries)
                }
            },
        )
    }

    private fun render(entries: List<CallLogEntry>) {
        loadedEntries = entries
        titleText.text = entries.firstOrNull()?.displayName ?: number
        rowContainer.removeAllViews()
        entries.forEach { entry -> rowContainer.addView(buildRow(entry)) }
    }

    private fun buildRow(entry: CallLogEntry): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(SIDE_PADDING_DP), dp(6), dp(SIDE_PADDING_DP), dp(6))
        addView(
            TextView(this@CallDetailActivity).apply {
                text = directionGlyph(entry.type)
                textSize = 16f
                setTextColor(if (entry.type == android.provider.CallLog.Calls.MISSED_TYPE) Palette.RED else Palette.GRAY_DIM)
                setPadding(0, 0, dp(8), 0)
            },
        )
        addView(
            TextView(this@CallDetailActivity).apply {
                text = formatDateTime(entry.date)
                textSize = 16f
                setTextColor(Palette.GRAY_DIM)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            TextView(this@CallDetailActivity).apply {
                text = formatDurationLabel(entry)
                textSize = 13f
                setTextColor(Palette.GRAY_MUTED)
            },
        )
    }

    private fun formatDateTime(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return "${date.format(DATE_FORMAT)} · ${DateFmt.formatTime(epochMillis)}"
    }

    private fun formatDurationLabel(entry: CallLogEntry): String {
        val missed = entry.type == android.provider.CallLog.Calls.MISSED_TYPE ||
            entry.type == android.provider.CallLog.Calls.REJECTED_TYPE ||
            entry.type == android.provider.CallLog.Calls.BLOCKED_TYPE
        if (missed) return ""
        val minutes = entry.durationSeconds / 60
        val seconds = entry.durationSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuSheet.handleKeyDown(keyCode, event)) return true
        when (keyCode) {
            KeyEvent.KEYCODE_CALL -> {
                placeCallOrRequestPermission(number)
                return true
            }
            KeyEvent.KEYCODE_STAR -> {
                openMenu()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openMenu() {
        val contactId = loadedEntries.firstOrNull()?.resolvedContactId
        val items = mutableListOf<MenuItem>()
        if (contactId != null) {
            items.add(MenuItem("Open contact") { openContact(contactId) })
        } else {
            val addToContactsIntent = addToContactsIntent(number)
            if (addToContactsIntent.resolveActivity(packageManager) != null) {
                items.add(MenuItem("Add to contacts") { startActivity(addToContactsIntent) })
            }
        }
        items.add(MenuItem("Delete these entries") { confirmDelete() })
        menuSheet.show(items)
    }

    private fun openContact(contactId: Long) {
        startActivity(Intent(this, ContactDetailActivity::class.java).putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contactId))
    }

    // A platform hand-off, not an editor (docs/p9-plan.md) -- on a phone with C9, this lands in
    // C9's own editor (its ContactsActivity already declares the INSERT filter); on a phone
    // without any contacts app, resolveActivity() in openMenu() hides this item entirely. P9
    // never names C9 and never requires it to be installed.
    private fun addToContactsIntent(number: String): Intent =
        Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)

    private fun confirmDelete() {
        menuSheet.show(
            listOf(
                MenuItem("Yes, delete these entries") { deleteAndFinish() },
                MenuItem("Keep") {},
            ),
        )
    }

    private fun deleteAndFinish() {
        val ids = loadedEntries.map { it.id }
        Async.run(
            background = { ids.all { CallLogStore.deleteEntry(this, it) } },
            onResult = { success ->
                if (!success) Toast.makeText(this, "Couldn't delete", Toast.LENGTH_SHORT).show()
                finish()
            },
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PHONE_PERMISSION && pendingDial &&
            checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        ) {
            pendingDial = false
            placeCall(this, number)
        }
    }

    private fun placeCallOrRequestPermission(number: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            placeCall(this, number)
        } else {
            pendingDial = true
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE_PERMISSION)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        const val EXTRA_NUMBER = "number"
    }
}
