package life.furrer.p9.history

import android.provider.CallLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_HEADER_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** One row of the dialer's call-history list — a day header, or one collapsed entry. Pure,
 *  entries-in/rows-out, per the Data layer section: day-group headers, consecutive same-number-
 *  same-type calls collapsed into one row with a count. */
sealed interface CallLogRow {
    data class DayHeader(val label: String) : CallLogRow
    data class Entry(
        val id: Long,
        val number: String,
        val displayName: String?,
        val type: Int,
        val date: Long,
        val durationSeconds: Long,
        val count: Int,
    ) : CallLogRow
}

fun CallLogRow.isSelectable(): Boolean = this is CallLogRow.Entry

/** [entries] must already be sorted newest-first (as [CallLogStore.queryCallLog] returns them) —
 *  this only groups, filters and collapses, it never re-sorts. Collapsing never crosses a day
 *  boundary — two calls either side of midnight are never "the same visit" for this purpose. */
fun buildCallLogRows(
    entries: List<CallLogEntry>,
    missedOnly: Boolean,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<CallLogRow> {
    val filtered = if (missedOnly) entries.filter { it.type == CallLog.Calls.MISSED_TYPE } else entries

    val rows = mutableListOf<CallLogRow>()
    var currentDay: LocalDate? = null
    var i = 0
    while (i < filtered.size) {
        val entry = filtered[i]
        val day = Instant.ofEpochMilli(entry.date).atZone(zone).toLocalDate()
        if (day != currentDay) {
            rows.add(CallLogRow.DayHeader(dayLabel(day, today)))
            currentDay = day
        }
        var count = 1
        var j = i + 1
        while (j < filtered.size &&
            filtered[j].number == entry.number &&
            filtered[j].type == entry.type &&
            Instant.ofEpochMilli(filtered[j].date).atZone(zone).toLocalDate() == day
        ) {
            count++
            j++
        }
        rows.add(
            CallLogRow.Entry(
                id = entry.id,
                number = entry.number,
                displayName = entry.displayName,
                type = entry.type,
                date = entry.date,
                durationSeconds = entry.durationSeconds,
                count = count,
            ),
        )
        i = j
    }
    return rows
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "TODAY"
    today.minusDays(1) -> "YESTERDAY"
    else -> "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()} ${day.format(DAY_HEADER_FORMAT)}"
}

/** ↙ incoming, ↗ outgoing, ✕ missed, ⊘ rejected/blocked — shown on every row regardless of
 *  selection, since it's the reason the row exists, not extra detail (unlike the meta line
 *  below). */
fun directionGlyph(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "↙"
    CallLog.Calls.OUTGOING_TYPE -> "↗"
    CallLog.Calls.MISSED_TYPE -> "✕"
    CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> "⊘"
    else -> "•"
}

private fun typeWord(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "incoming"
    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
    CallLog.Calls.MISSED_TYPE -> "missed"
    CallLog.Calls.REJECTED_TYPE -> "rejected"
    CallLog.Calls.BLOCKED_TYPE -> "blocked"
    else -> "call"
}

/** The meta line a selected row gains — "missed · 2 calls" for a collapsed group, direction plus
 *  duration for a single call, or just the direction word when there's no duration to show
 *  (missed/rejected/blocked). */
fun callLogMetaLine(row: CallLogRow.Entry): String = when {
    row.count > 1 -> "${typeWord(row.type)} · ${row.count} calls"
    row.type == CallLog.Calls.MISSED_TYPE || row.type == CallLog.Calls.REJECTED_TYPE || row.type == CallLog.Calls.BLOCKED_TYPE ->
        typeWord(row.type)
    else -> "${typeWord(row.type)} · ${formatCallDuration(row.durationSeconds)}"
}

private fun formatCallDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "%d:%02d".format(minutes, remainder)
}
