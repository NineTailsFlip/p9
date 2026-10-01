package life.furrer.p9.dial

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.contacts.ContactRow
import life.furrer.nine.contacts.bindContactRow
import life.furrer.nine.contacts.contactRowHeightDp
import life.furrer.nine.contacts.createContactRowContainer
import life.furrer.nine.core.ui.Palette
import life.furrer.p9.history.bindCallLogRow
import life.furrer.p9.history.callLogRowHeightDp
import life.furrer.nine.core.util.PhoneFormat

private const val ROW_HEIGHT_DP = 32
private const val HEADER_ROW_HEIGHT_DP = 24
private const val ROW_SIDE_PADDING_DP = 16

/** [createContactRowContainer] returns nothing but a bare, empty `LinearLayout` — every [DialRow]
 *  kind is happy to reuse it as its own row container, so [life.furrer.nine.core.ui.RowListView]'s
 *  recycling never has to care which kind a recycled view last held. */
fun createDialRowContainer(context: Context): LinearLayout = createContactRowContainer(context)

fun dialRowHeightDp(row: DialRow, selected: Boolean): Int = when (row) {
    is DialRow.SectionHeader -> HEADER_ROW_HEIGHT_DP
    is DialRow.HistoryEntry -> callLogRowHeightDp(row.entry, selected)
    is DialRow.ContactMatch -> contactRowHeightDp(ContactRow.Entry(row.contact))
    is DialRow.NumberMatch -> contactRowHeightDp(ContactRow.NumberMatch(row.contact, row.matchedNumber))
    is DialRow.RecentMatch -> ROW_HEIGHT_DP
    is DialRow.TypedNumber -> ROW_HEIGHT_DP
}

/** Contact rows are handed straight to `:contacts`' own [bindContactRow] — "reuse ... :contacts'
 *  own row renderer, so a contact looks identical in P9 and C9" (docs/p9-plan.md). */
fun bindDialRow(container: LinearLayout, row: DialRow, selected: Boolean) {
    when (row) {
        is DialRow.SectionHeader -> bindSectionHeader(container, row.label)
        is DialRow.HistoryEntry -> bindCallLogRow(container, row.entry, selected)
        is DialRow.ContactMatch -> bindContactRow(container, ContactRow.Entry(row.contact), selected)
        is DialRow.NumberMatch -> bindContactRow(container, ContactRow.NumberMatch(row.contact, row.matchedNumber), selected)
        is DialRow.RecentMatch -> bindRecentMatch(container, row.entry.displayName ?: PhoneFormat.display(container.context, row.entry.number), selected)
        is DialRow.TypedNumber -> bindTypedNumber(container, row.digits)
    }
}

private fun bindSectionHeader(container: LinearLayout, label: String) {
    container.removeAllViews()
    val context = container.context
    container.addView(
        TextView(context).apply {
            text = label
            textSize = 11f
            letterSpacing = 0.11f
            setTextColor(Palette.GRAY_FAINT)
            setPadding(dp(context, ROW_SIDE_PADDING_DP), 0, dp(context, ROW_SIDE_PADDING_DP), 0)
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
}

private fun bindRecentMatch(container: LinearLayout, label: String, selected: Boolean) {
    container.removeAllViews()
    val context = container.context
    container.addView(
        TextView(context).apply {
            text = label
            textSize = 18f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(context, ROW_SIDE_PADDING_DP), 0, dp(context, ROW_SIDE_PADDING_DP), 0)
            setTextColor(if (selected) Color.WHITE else Palette.GRAY_DIM)
            paintFlags = if (selected) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
}

private fun bindTypedNumber(container: LinearLayout, digits: String) {
    container.removeAllViews()
    val context = container.context
    container.addView(
        TextView(context).apply {
            text = PhoneFormat.display(context, digits)
            textSize = 18f
            setTextColor(Palette.GRAY_DIM)
            setPadding(dp(context, ROW_SIDE_PADDING_DP), 0, dp(context, ROW_SIDE_PADDING_DP), 0)
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
}

private fun dp(context: Context, value: Int): Int = android.util.TypedValue.applyDimension(
    android.util.TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
).toInt()
