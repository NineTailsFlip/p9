package life.furrer.p9.history

import android.provider.CallLog
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CallLogRowsTest {

    private val today = LocalDate.of(2026, 8, 2)
    private val zone = ZoneOffset.UTC

    private fun millisAt(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(
        id: Long,
        number: String,
        date: Long,
        type: Int = CallLog.Calls.INCOMING_TYPE,
        durationSeconds: Long = 0,
        name: String? = null,
    ) = CallLogEntry(id, number, cachedName = name, resolvedName = null, resolvedContactId = null, type = type, date = date, durationSeconds = durationSeconds, phoneAccountId = null)

    @Test
    fun `groups entries under TODAY and YESTERDAY headers`() {
        val entries = listOf(
            entry(1, "079", millisAt(today, 14, 2)),
            entry(2, "078", millisAt(today.minusDays(1), 21, 14)),
        )

        val rows = buildCallLogRows(entries, missedOnly = false, today = today, zone = zone)

        assertEquals(
            listOf(
                CallLogRow.DayHeader("TODAY"),
                CallLogRow.Entry(1, "079", null, null, CallLog.Calls.INCOMING_TYPE, entries[0].date, 0, 1),
                CallLogRow.DayHeader("YESTERDAY"),
                CallLogRow.Entry(2, "078", null, null, CallLog.Calls.INCOMING_TYPE, entries[1].date, 0, 1),
            ),
            rows,
        )
    }

    @Test
    fun `older days get a weekday and date label`() {
        val oldDate = today.minusDays(10) // 2026-07-23, a Thursday
        val rows = buildCallLogRows(listOf(entry(1, "079", millisAt(oldDate, 9, 0))), missedOnly = false, today = today, zone = zone)

        val header = rows.first() as CallLogRow.DayHeader
        assertEquals("THU 23 Jul", header.label)
    }

    @Test
    fun `consecutive same-number-same-type calls on the same day collapse with a count`() {
        val entries = listOf(
            entry(3, "079", millisAt(today, 15, 0), type = CallLog.Calls.MISSED_TYPE),
            entry(2, "079", millisAt(today, 14, 30), type = CallLog.Calls.MISSED_TYPE),
            entry(1, "079", millisAt(today, 14, 0), type = CallLog.Calls.MISSED_TYPE),
        )

        val rows = buildCallLogRows(entries, missedOnly = false, today = today, zone = zone)

        assertEquals(2, rows.size) // one day header + one collapsed entry
        val collapsed = rows[1] as CallLogRow.Entry
        assertEquals(3, collapsed.count)
        assertEquals(3L, collapsed.id) // the newest call's own id
    }

    @Test
    fun `collapsing never crosses a day boundary`() {
        val entries = listOf(
            entry(2, "079", millisAt(today, 0, 2), type = CallLog.Calls.MISSED_TYPE),
            entry(1, "079", millisAt(today.minusDays(1), 23, 58), type = CallLog.Calls.MISSED_TYPE),
        )

        val rows = buildCallLogRows(entries, missedOnly = false, today = today, zone = zone)

        // TODAY header, one entry, YESTERDAY header, one entry -- not collapsed into one.
        assertEquals(4, rows.size)
    }

    @Test
    fun `a different number or type breaks the collapse`() {
        val entries = listOf(
            entry(3, "079", millisAt(today, 15, 0), type = CallLog.Calls.OUTGOING_TYPE),
            entry(2, "078", millisAt(today, 14, 30), type = CallLog.Calls.OUTGOING_TYPE),
            entry(1, "079", millisAt(today, 14, 0), type = CallLog.Calls.OUTGOING_TYPE),
        )

        val rows = buildCallLogRows(entries, missedOnly = false, today = today, zone = zone)

        assertEquals(4, rows.size) // header + 3 separate entries, none collapsed
    }

    @Test
    fun `missedOnly filters out everything else, including its own day headers`() {
        val entries = listOf(
            entry(1, "079", millisAt(today, 14, 0), type = CallLog.Calls.MISSED_TYPE),
            entry(2, "078", millisAt(today, 13, 0), type = CallLog.Calls.OUTGOING_TYPE),
        )

        val rows = buildCallLogRows(entries, missedOnly = true, today = today, zone = zone)

        assertEquals(listOf(CallLogRow.DayHeader::class, CallLogRow.Entry::class), rows.map { it::class })
        assertEquals(CallLog.Calls.MISSED_TYPE, (rows[1] as CallLogRow.Entry).type)
    }

    @Test
    fun `directionGlyph maps every type`() {
        assertEquals("↙", directionGlyph(CallLog.Calls.INCOMING_TYPE))
        assertEquals("↗", directionGlyph(CallLog.Calls.OUTGOING_TYPE))
        assertEquals("✕", directionGlyph(CallLog.Calls.MISSED_TYPE))
        assertEquals("⊘", directionGlyph(CallLog.Calls.REJECTED_TYPE))
        assertEquals("⊘", directionGlyph(CallLog.Calls.BLOCKED_TYPE))
    }

    @Test
    fun `callLogMetaLine shows direction and duration for a single call`() {
        val row = CallLogRow.Entry(1, "079", null, null, CallLog.Calls.OUTGOING_TYPE, 0L, durationSeconds = 135, count = 1)
        assertEquals("outgoing · 2:15", callLogMetaLine(row))
    }

    @Test
    fun `callLogMetaLine shows just the direction word when there is no duration to show`() {
        val row = CallLogRow.Entry(1, "079", null, null, CallLog.Calls.MISSED_TYPE, 0L, durationSeconds = 0, count = 1)
        assertEquals("missed", callLogMetaLine(row))
    }

    @Test
    fun `callLogMetaLine shows the collapsed count instead of a duration`() {
        val row = CallLogRow.Entry(1, "079", null, null, CallLog.Calls.MISSED_TYPE, 0L, durationSeconds = 0, count = 3)
        assertEquals("missed · 3 calls", callLogMetaLine(row))
    }
}
