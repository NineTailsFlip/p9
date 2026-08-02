package life.furrer.p9.history

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog

/** A minimal in-memory call log provider for tests — Robolectric has no working CallLogProvider,
 *  same situation FakeContactsProvider/FakeCalendarProvider already work around. Always returns
 *  every row sorted newest-first regardless of the literal sortOrder string CallLogStore passes
 *  ("date DESC LIMIT n") — good enough for the small datasets these tests use. */
class FakeCallLogProvider : ContentProvider() {

    companion object {
        val calls = mutableMapOf<Long, ContentValues>()

        fun reset() = calls.clear()

        fun putCall(id: Long, number: String, type: Int, date: Long, durationSeconds: Long = 0, cachedName: String? = null) {
            calls[id] = ContentValues().apply {
                put(CallLog.Calls.NUMBER, number)
                put(CallLog.Calls.TYPE, type)
                put(CallLog.Calls.DATE, date)
                put(CallLog.Calls.DURATION, durationSeconds)
                put(CallLog.Calls.CACHED_NAME, cachedName)
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cols = projection ?: arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        val cursor = MatrixCursor(cols)
        calls.entries.sortedByDescending { it.value.getAsLong(CallLog.Calls.DATE) }.forEach { (id, values) ->
            cursor.addRow(cols.map { col -> if (col == CallLog.Calls._ID) id else values.get(col) })
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    // A single-id URI (ContentUris.withAppendedId, what deleteEntry() builds) deletes just that
    // row; the bare CONTENT_URI (what clearAll() uses) has no id segment and deletes everything.
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = uri.lastPathSegment?.toLongOrNull()
        return if (id != null) {
            if (calls.remove(id) != null) 1 else 0
        } else {
            val count = calls.size
            calls.clear()
            count
        }
    }

    override fun getType(uri: Uri): String? = null
}
