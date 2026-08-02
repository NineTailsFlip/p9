package life.furrer.p9.history

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract

/** One row of `CallLog.Calls`, before day-grouping/collapsing (see [CallLogRows]). [resolvedName]
 *  and [resolvedContactId] come from [ContactsContract.PhoneLookup] rather than the log's own
 *  [CallLog.Calls.CACHED_NAME] — that column is the *log's* best-effort label at call time, which
 *  can be stale (a contact renamed since) or simply absent (a call from before the number was
 *  ever saved); PhoneLookup always reflects the address book as it stands now. */
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
    /** What the list actually shows — PhoneLookup's live answer, falling back to the log's own
     *  cached label, falling back to nothing (the number is shown on its own). */
    val displayName: String? get() = resolvedName ?: cachedName
}

object CallLogStore {
    fun hasReadPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** The dialer's own read, per the Data layer section: newest first, limited by the history-
     *  length setting, phone numbers resolved to contacts through PhoneLookup rather than by
     *  string-matching against the contacts list. Resolution is cached per call to this function
     *  (one PhoneLookup query per *distinct* number in the batch, not per row) so a long log
     *  against a large address book doesn't do one provider round trip per entry. */
    fun queryCallLog(context: Context, limit: Int): List<CallLogEntry> {
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
        val raw = mutableListOf<CallLogEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val accountIdIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)
            while (cursor.moveToNext()) {
                raw.add(
                    CallLogEntry(
                        id = cursor.getLong(idIndex),
                        number = cursor.getString(numberIndex) ?: "",
                        cachedName = cursor.getString(nameIndex),
                        resolvedName = null,
                        resolvedContactId = null,
                        type = cursor.getInt(typeIndex),
                        date = cursor.getLong(dateIndex),
                        durationSeconds = cursor.getLong(durationIndex),
                        phoneAccountId = cursor.getString(accountIdIndex),
                    ),
                )
            }
        }

        val resolved = resolveContacts(context, raw.map { it.number }.distinct())
        return raw.map { entry ->
            val match = resolved[entry.number]
            entry.copy(resolvedName = match?.first, resolvedContactId = match?.second)
        }
    }

    private fun resolveContacts(context: Context, numbers: List<String>): Map<String, Pair<String, Long>> {
        if (numbers.isEmpty()) return emptyMap()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyMap()

        val result = mutableMapOf<String, Pair<String, Long>>()
        numbers.forEach { number ->
            if (number.isBlank()) return@forEach
            val uri: Uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val name = cursor.getString(nameIndex)
                    if (name != null) result[number] = name to cursor.getLong(idIndex)
                }
            }
        }
        return result
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
