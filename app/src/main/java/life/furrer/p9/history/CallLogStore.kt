package life.furrer.p9.history

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import life.furrer.nine.contacts.ContactStore
import life.furrer.nine.contacts.resolveNumbers
import life.furrer.nine.core.util.int
import life.furrer.nine.core.util.long
import life.furrer.nine.core.util.mapRows
import life.furrer.nine.core.util.string
import life.furrer.nine.core.util.stringOr

/** One row of `CallLog.Calls`, before day-grouping/collapsing (see [CallLogRows]). [resolvedName]
 *  and [resolvedContactId] come from the address book itself ([ContactStore.resolveNumbers])
 *  rather than the log's own [CallLog.Calls.CACHED_NAME] — that column is the *log's* best-effort
 *  label at call time, which can be stale (a contact renamed since) or simply absent (a call from
 *  before the number was ever saved); the address book always reflects who it is now. */
data class CallLogEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val resolvedName: String?,
    val resolvedContactId: Long?,
    val type: Int,
    val date: Long,
    val durationSeconds: Long,
    val phoneAccountId: String?,
) {
    /** What the list actually shows — the address book's live answer, falling back to the log's own
     *  cached label, falling back to nothing (the number is shown on its own). */
    val displayName: String? get() = resolvedName ?: cachedName
}

object CallLogStore {
    fun hasReadPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** The dialer's own read, per the Data layer section: newest first, limited by the history-
     *  length setting, phone numbers resolved to contacts through [ContactStore.resolveNumbers] —
     *  one read of the phone table for the whole batch rather than one PhoneLookup round trip per
     *  distinct number, which is what made this load grow slower with every new correspondent.
     *  [number] narrows the read to one number's entries in the provider itself, so a detail
     *  screen doesn't read (and resolve) the whole log just to throw most of it away. */
    fun queryCallLog(context: Context, limit: Int, number: String? = null): List<CallLogEntry> {
        if (!hasReadPermission(context)) return emptyList()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        // At most `limit` rows (the screens ask for a couple of hundred), so by-name reads are fine here.
        val raw = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            number?.let { "${CallLog.Calls.NUMBER} = ?" },
            number?.let { arrayOf(it) },
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        ).mapRows { cursor ->
            CallLogEntry(
                id = cursor.long(CallLog.Calls._ID),
                number = cursor.stringOr(CallLog.Calls.NUMBER, ""),
                cachedName = cursor.string(CallLog.Calls.CACHED_NAME),
                resolvedName = null,
                resolvedContactId = null,
                type = cursor.int(CallLog.Calls.TYPE),
                date = cursor.long(CallLog.Calls.DATE),
                durationSeconds = cursor.long(CallLog.Calls.DURATION),
                phoneAccountId = cursor.string(CallLog.Calls.PHONE_ACCOUNT_ID),
            )
        }

        val resolved = ContactStore.resolveNumbers(context, raw.map { it.number })
        return raw.map { entry ->
            val match = resolved[entry.number]
            entry.copy(resolvedName = match?.displayName, resolvedContactId = match?.contactId)
        }
    }

    fun deleteEntry(context: Context, id: Long): Boolean {
        if (!hasWritePermission(context)) return false
        val uri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id)
        return context.contentResolver.delete(uri, null, null) > 0
    }

    fun clearAll(context: Context): Boolean {
        if (!hasWritePermission(context)) return false
        return context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) >= 0
    }
}
