package life.furrer.p9.dial

import java.time.LocalDate
import life.furrer.nine.contacts.ContactEntry
import life.furrer.nine.core.util.StringMatch
import life.furrer.p9.history.CallLogEntry
import life.furrer.p9.history.CallLogRow
import life.furrer.p9.history.buildCallLogRows

/** One row of the dialer screen — pure, dial string + call history + contacts in, rows out. With
 *  an empty dial string this is just the call history, day-grouped (delegates the grouping/
 *  collapsing itself to [buildCallLogRows] and re-wraps its rows); with anything typed it's search
 *  matches, the same rules `:contacts`' own search uses (word-prefix name match, digit-substring
 *  number match, a NUMBERS section for number-only matches, the typed digits echoed as their own
 *  row when nothing matches) plus a RECENT section for call-log entries whose number matches. */
sealed interface DialRow {
    data class SectionHeader(val label: String) : DialRow
    data class HistoryEntry(val entry: CallLogRow.Entry) : DialRow
    data class ContactMatch(val contact: ContactEntry) : DialRow
    data class NumberMatch(val contact: ContactEntry, val matchedNumber: String) : DialRow
    data class RecentMatch(val entry: CallLogEntry) : DialRow
    data class TypedNumber(val digits: String) : DialRow

    /** Stands in for contacts and history while P9 may not read them: the dialer itself keeps
     *  working, because someone must always be able to type 112 and press Call. */
    data object GrantAccess : DialRow
}

fun DialRow.isSelectable(): Boolean = this !is DialRow.SectionHeader && this !is DialRow.TypedNumber

fun buildDialRows(
    dialString: String,
    contacts: List<ContactEntry>,
    callLog: List<CallLogEntry>,
    missedOnly: Boolean,
    today: LocalDate,
    hasAccess: Boolean = true,
): List<DialRow> {
    val rows = if (dialString.isEmpty()) {
        buildHistoryRows(callLog, missedOnly, today)
    } else {
        buildSearchRows(dialString, contacts, callLog)
    }
    return if (hasAccess) rows else rows + DialRow.GrantAccess
}

private fun buildHistoryRows(callLog: List<CallLogEntry>, missedOnly: Boolean, today: LocalDate): List<DialRow> =
    buildCallLogRows(callLog, missedOnly, today).map { row ->
        when (row) {
            is CallLogRow.DayHeader -> DialRow.SectionHeader(row.label)
            is CallLogRow.Entry -> DialRow.HistoryEntry(row)
        }
    }

private fun buildSearchRows(query: String, contacts: List<ContactEntry>, callLog: List<CallLogEntry>): List<DialRow> {
    val nameMatches = contacts.filter { StringMatch.matchesName(it.displayName, query) }
    val rows = mutableListOf<DialRow>()
    rows.addAll(nameMatches.map { DialRow.ContactMatch(it) })

    if (StringMatch.isAllDigits(query)) {
        val nameMatchIds = nameMatches.map { it.id }.toSet()
        val numberMatches = contacts
            .filter { it.id !in nameMatchIds }
            .mapNotNull { contact ->
                contact.phoneNumbers.firstOrNull { StringMatch.matchesPhoneNumber(it.number, query) }?.let { contact to it.number }
            }
        if (numberMatches.isNotEmpty()) {
            rows.add(DialRow.SectionHeader("NUMBERS"))
            numberMatches.forEach { (contact, number) -> rows.add(DialRow.NumberMatch(contact, number)) }
        }

        val matchedNumbers = (nameMatches.flatMap { c -> c.phoneNumbers.map { it.number } } + numberMatches.map { it.second }).toSet()
        val recentMatches = callLog
            .filter { StringMatch.matchesPhoneNumber(it.number, query) && it.number !in matchedNumbers }
            .distinctBy { it.number }
        if (recentMatches.isNotEmpty()) {
            rows.add(DialRow.SectionHeader("RECENT"))
            recentMatches.forEach { rows.add(DialRow.RecentMatch(it)) }
        }
    }

    if (rows.isEmpty() && StringMatch.looksDialable(query)) {
        rows.add(DialRow.SectionHeader("NUMBERS"))
        rows.add(DialRow.TypedNumber(query))
    }
    return rows
}
