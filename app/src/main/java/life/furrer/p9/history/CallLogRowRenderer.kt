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
const val CALL_LOG_ROW_WITH_META_HEIGHT_DP = 48
private const val HEADER_ROW_HEIGHT_DP = 24
private const val ROW_SIDE_PADDING_DP = 16

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
            // The one place a missed call is marked in color, on the glyph only — the rest of the
            // row keeps the plain selected/unselected treatment, so the list still reads as one
            // column of text rather than a wash of red.
            setTextColor(if (row.type == android.provider.CallLog.Calls.MISSED_TYPE) Palette.RED else textColor)
            setPadding(0, 0, dp(context, 8), 0)
        },
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
    container.addView(mainLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    if (selected) {
        container.addView(
            TextView(context).apply {
                text = callLogMetaLine(row)
                textSize = 12f
                setTextColor(Palette.GRAY_MUTED)
                setPadding(dp(context, ROW_SIDE_PADDING_DP), 0, dp(context, ROW_SIDE_PADDING_DP), dp(context, 4))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }
}

private fun dp(context: Context, value: Int): Int = android.util.TypedValue.applyDimension(
    android.util.TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
).toInt()
