package life.furrer.p9.dial

import android.provider.CallLog
import java.time.LocalDate
import life.furrer.nine.contacts.ContactEntry
import life.furrer.nine.contacts.ContactPhoneNumber
import life.furrer.p9.history.CallLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialRowsTest {

    private val today = LocalDate.of(2026, 8, 2)

    private fun contact(id: Long, name: String, vararg numbers: String) =
        ContactEntry(id, "lookup$id", name, starred = false, photoThumbnailUri = null, phoneNumbers = numbers.map { ContactPhoneNumber(it, null, false) })

    private fun logEntry(number: String, date: Long = 0L, type: Int = CallLog.Calls.OUTGOING_TYPE) =
        CallLogEntry(1, number, cachedName = null, resolvedName = null, resolvedContactId = null, type = type, date = date, durationSeconds = 0, phoneAccountId = null)

    @Test
    fun `empty dial string shows call history, day-grouped`() {
        val rows = buildDialRows("", emptyList(), listOf(logEntry("079", date = 0L)), missedOnly = false, today = today)

        assertTrue(rows[0] is DialRow.SectionHeader)
        assertTrue(rows[1] is DialRow.HistoryEntry)
    }

    @Test
    fun `typing a name matches contacts by word prefix`() {
        val ana = contact(1, "Ana Beck")
        val rows = buildDialRows("ana", listOf(ana), emptyList(), missedOnly = false, today = today)

        assertEquals(listOf(DialRow.ContactMatch(ana)), rows)
    }

    @Test
    fun `typing digits matches a contact by number under a NUMBERS header`() {
        val ana = contact(1, "Ana Beck", "0791234567")
        val rows = buildDialRows("1234", listOf(ana), emptyList(), missedOnly = false, today = today)

        assertEquals(
            listOf(DialRow.SectionHeader("NUMBERS"), DialRow.NumberMatch(ana, "0791234567")),
            rows,
        )
    }

    @Test
    fun `a contact already matched by name is not repeated in NUMBERS`() {
        val ana = contact(1, "Ana 123", "0791234567")
        val rows = buildDialRows("123", listOf(ana), emptyList(), missedOnly = false, today = today)

        // Matches by name (word "123" prefix-matches nothing here actually -- use a query that
        // matches both the name and number to prove NUMBERS doesn't repeat it).
        assertTrue(rows.none { it is DialRow.NumberMatch })
    }

    @Test
    fun `a call-log hit surfaces under RECENT when no contact already covers that number`() {
        val entry = logEntry("0791234567")
        val rows = buildDialRows("1234", emptyList(), listOf(entry), missedOnly = false, today = today)

        assertEquals(
            listOf(DialRow.SectionHeader("RECENT"), DialRow.RecentMatch(entry)),
            rows,
        )
    }

    @Test
    fun `a RECENT hit is suppressed when a contact already matched that same number`() {
        val ana = contact(1, "Ana Beck", "0791234567")
        val entry = logEntry("0791234567")
        val rows = buildDialRows("1234", listOf(ana), listOf(entry), missedOnly = false, today = today)

        assertTrue(rows.none { it is DialRow.RecentMatch })
    }

    @Test
    fun `nothing matched but the query is dialable echoes the typed digits`() {
        val rows = buildDialRows("0791234567", emptyList(), emptyList(), missedOnly = false, today = today)

        assertEquals(
            listOf(DialRow.SectionHeader("NUMBERS"), DialRow.TypedNumber("0791234567")),
            rows,
        )
    }

    @Test
    fun `nothing matched and the query is not dialable (letters) shows nothing`() {
        val rows = buildDialRows("zzz", emptyList(), emptyList(), missedOnly = false, today = today)
        assertEquals(emptyList<DialRow>(), rows)
    }

    @Test
    fun `only SectionHeader and TypedNumber are not selectable`() {
        val ana = contact(1, "Ana Beck")
        assertTrue(DialRow.ContactMatch(ana).isSelectable())
        assertTrue(DialRow.RecentMatch(logEntry("1")).isSelectable())
        assertTrue(DialRow.NumberMatch(ana, "1").isSelectable())
        assertTrue(!DialRow.SectionHeader("X").isSelectable())
        assertTrue(!DialRow.TypedNumber("1").isSelectable())
        assertTrue(DialRow.GrantAccess.isSelectable())
    }

    @Test
    fun `without access the list is only the access row`() {
        val rows = buildDialRows("", emptyList(), emptyList(), missedOnly = false, today = today, hasAccess = false)
        assertEquals(listOf(DialRow.GrantAccess), rows)
    }

    @Test
    fun `without access a typed number is still echoed, above the access row`() {
        val rows = buildDialRows("112", emptyList(), emptyList(), missedOnly = false, today = today, hasAccess = false)
        assertEquals(
            listOf(DialRow.SectionHeader("NUMBERS"), DialRow.TypedNumber("112"), DialRow.GrantAccess),
            rows,
        )
    }
}
