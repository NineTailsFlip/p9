package life.furrer.p9.history

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.util.DateFmt

const val CALL_LOG_ROW_HEIGHT_DP = 32
// 48dp is the two-line row height every other list in the suite uses, but confirmed live on
// device it clips the selected row's own underline: the main line is weighted to fill whatever's
// left after the meta line's real on-device height is subtracted (see bindEntry below), and at
// 48dp total that remainder comes up just short of what an 18sp line plus its underline actually
// needs. The extra 8dp heads-room goes entirely to the weighted main line.
const val CALL_LOG_ROW_WITH_META_HEIGHT_DP = 56
private const val HEADER_ROW_HEIGHT_DP = 24
private const val ROW_SIDE_PADDING_DP = 16

// A fixed column for the direction glyph, rather than its own natural (variable per-glyph) width
// plus end-padding, so the meta line below can indent to the exact same spot the caller name
// starts at instead of lining up with the glyph.
private const val GLYPH_COLUMN_WIDTH_DP = 24

/** Row height in dp for [row] — one line normally, two when selected (the meta line from
 *  [callLogMetaLine]), matching the calendar's own "the selected row may be taller" convention
 *  rather than [life.furrer.nine.contacts.ContactRow.NumberMatch]'s always-two-lines shape: here
 *  the extra line is detail about an already-obvious row, not the reason it matched. */
fun callLogRowHeightDp(row: CallLogRow, selected: Boolean): Int = when (row) {
    is CallLogRow.DayHeader -> HEADER_ROW_HEIGHT_DP
    is CallLogRow.Entry -> if (selected) CALL_LOG_ROW_WITH_META_HEIGHT_DP else CALL_LOG_ROW_HEIGHT_DP
}

fun bindCallLogRow(container: LinearLayout, row: CallLogRow, selected: Boolean) {
    container.removeAllViews()
    when (row) {
        is CallLogRow.DayHeader -> bindDayHeader(container, row)
        is CallLogRow.Entry -> bindEntry(container, row, selected)
    }
}

private fun bindDayHeader(container: LinearLayout, row: CallLogRow.DayHeader) {
    val context = container.context
    container.addView(
        TextView(context).apply {
            text = row.label
            textSize = 11f
            letterSpacing = 0.11f
            setTextColor(Palette.GRAY_FAINT)
            setPadding(dp(context, ROW_SIDE_PADDING_DP), 0, dp(context, ROW_SIDE_PADDING_DP), 0)
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
}

private fun bindEntry(container: LinearLayout, row: CallLogRow.Entry, selected: Boolean) {
    val context = container.context
    val textColor = if (selected) Color.WHITE else Palette.GRAY_DIM

    val mainLine = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, ROW_SIDE_PADDING_DP), 0, dp(context, ROW_SIDE_PADDING_DP), 0)
    }
    mainLine.addView(
        TextView(context).apply {
            text = directionGlyph(row.type)
            textSize = 18f
            gravity = Gravity.CENTER
            // The one place a missed call is marked in color, on the glyph only — the rest of the
            // row keeps the plain selected/unselected treatment, so the list still reads as one
            // column of text rather than a wash of red.
            setTextColor(if (row.type == android.provider.CallLog.Calls.MISSED_TYPE) Palette.RED else textColor)
        },
        LinearLayout.LayoutParams(dp(context, GLYPH_COLUMN_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    mainLine.addView(
        TextView(context).apply {
            text = row.displayName ?: row.number
            textSize = 18f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(textColor)
            paintFlags = if (selected) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )
    mainLine.addView(
        TextView(context).apply {
            text = DateFmt.formatTime(row.date)
            textSize = 13f
            setTextColor(Palette.GRAY_MUTED)
        },
    )
    // height=0 + weight=1, not WRAP_CONTENT: the container is measured at an exact height (32dp
    // or 48dp, from callLogRowHeightDp), and with two WRAP_CONTENT children whose natural heights
    // sum to more than that, LinearLayout doesn't shrink either one — it just lets the total
    // overflow the container's real bounds, which RowListView then clips. Weighting the main line
    // to fill whatever's left after the meta line takes its own natural height (the same shape
    // ContactRowRenderer's own two-line NumberMatch row already uses) is what keeps both lines
    // inside the row's actual allotted height. Confirmed live: this exact clipping happened on
    // the just-placed call's own history row.
    container.addView(mainLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

    if (selected) {
        container.addView(
            TextView(context).apply {
                text = callLogMetaLine(row)
                textSize = 12f
                setTextColor(Palette.GRAY_MUTED)
                // Lines up with the caller name, not the glyph column above it.
                setPadding(dp(context, ROW_SIDE_PADDING_DP + GLYPH_COLUMN_WIDTH_DP), 0, dp(context, ROW_SIDE_PADDING_DP), dp(context, 4))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }
}

private fun dp(context: Context, value: Int): Int = android.util.TypedValue.applyDimension(
    android.util.TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
).toInt()
