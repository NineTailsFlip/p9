package life.furrer.p9.history

import android.content.ContentValues
import android.provider.CallLog
import android.provider.ContactsContract
import life.furrer.nine.contacts.FakeContactsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CallLogStoreTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        FakeCallLogProvider.reset()
        Robolectric.buildContentProvider(FakeCallLogProvider::class.java).create(CallLog.AUTHORITY)
        FakeContactsProvider.reset()
        Robolectric.buildContentProvider(FakeContactsProvider::class.java).create("com.android.contacts")
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun `queryCallLog returns nothing without READ_CALL_LOG`() {
        shadowOf(context).denyPermissions(android.Manifest.permission.READ_CALL_LOG)
        FakeCallLogProvider.putCall(1L, "079", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        assertTrue(CallLogStore.queryCallLog(context, 50).isEmpty())
    }

    @Test
    fun `queryCallLog reads every column`() {
        FakeCallLogProvider.putCall(1L, "079123", CallLog.Calls.MISSED_TYPE, date = 500L, durationSeconds = 0, cachedName = "Old Name")

        val entries = CallLogStore.queryCallLog(context, 50)

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("079123", entry.number)
        assertEquals(CallLog.Calls.MISSED_TYPE, entry.type)
        assertEquals(500L, entry.date)
        assertEquals("Old Name", entry.cachedName)
    }

    @Test
    fun `queryCallLog resolves a number to its current contact name from the address book`() {
        putContactWithNumber(1L, "Andreas Furrer", "+41791234567")
        // The whole point of resolving live rather than trusting the log: the log's own cached
        // label is stale (the contact was renamed since the call), and the address book's answer wins.
        // Also covers the normalization that matters in practice — the address book holds the
        // number in international form, the log recorded it as dialed.
        FakeCallLogProvider.putCall(1L, "079 123 45 67", CallLog.Calls.OUTGOING_TYPE, date = 1L, cachedName = "Cached Andreas")

        val entry = CallLogStore.queryCallLog(context, 50).single()

        assertEquals("Andreas Furrer", entry.resolvedName)
        assertEquals("Andreas Furrer", entry.displayName)
    }

    @Test
    fun `displayName falls back to the log's cached name when the address book knows nobody`() {
        FakeCallLogProvider.putCall(1L, "079123", CallLog.Calls.OUTGOING_TYPE, date = 1L, cachedName = "Cached Andreas")

        val entry = CallLogStore.queryCallLog(context, 50).single()

        assertNull(entry.resolvedName)
        assertEquals("Cached Andreas", entry.displayName)
    }

    @Test
    fun `displayName falls back to null when neither resolved nor cached name exist`() {
        FakeCallLogProvider.putCall(1L, "079123", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        assertNull(CallLogStore.queryCallLog(context, 50).single().displayName)
    }

    @Test
    fun `entries come back newest first`() {
        FakeCallLogProvider.putCall(1L, "a", CallLog.Calls.OUTGOING_TYPE, date = 100L)
        FakeCallLogProvider.putCall(2L, "b", CallLog.Calls.OUTGOING_TYPE, date = 300L)
        FakeCallLogProvider.putCall(3L, "c", CallLog.Calls.OUTGOING_TYPE, date = 200L)

        val numbers = CallLogStore.queryCallLog(context, 50).map { it.number }
        assertEquals(listOf("b", "c", "a"), numbers)
    }

    @Test
    fun `deleteEntry fails closed without WRITE_CALL_LOG`() {
        shadowOf(context).denyPermissions(android.Manifest.permission.WRITE_CALL_LOG)
        FakeCallLogProvider.putCall(1L, "079", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        assertFalse(CallLogStore.deleteEntry(context, 1L))
        assertEquals(1, FakeCallLogProvider.calls.size)
    }

    @Test
    fun `deleteEntry removes just that one entry`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_CALL_LOG)
        FakeCallLogProvider.putCall(1L, "079", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        FakeCallLogProvider.putCall(2L, "078", CallLog.Calls.OUTGOING_TYPE, date = 2L)

        assertTrue(CallLogStore.deleteEntry(context, 1L))

        val remaining = CallLogStore.queryCallLog(context, 50)
        assertEquals(listOf("078"), remaining.map { it.number })
    }

    @Test
    fun `deleteEntry for an unknown id returns false`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_CALL_LOG)
        assertFalse(CallLogStore.deleteEntry(context, 999L))
    }

    @Test
    fun `clearAll fails closed without WRITE_CALL_LOG`() {
        shadowOf(context).denyPermissions(android.Manifest.permission.WRITE_CALL_LOG)
        FakeCallLogProvider.putCall(1L, "079", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        assertFalse(CallLogStore.clearAll(context))
        assertEquals(1, FakeCallLogProvider.calls.size)
    }

    @Test
    fun `clearAll empties the whole log`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_CALL_LOG)
        FakeCallLogProvider.putCall(1L, "079", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        FakeCallLogProvider.putCall(2L, "078", CallLog.Calls.OUTGOING_TYPE, date = 2L)

        assertTrue(CallLogStore.clearAll(context))

        assertTrue(CallLogStore.queryCallLog(context, 50).isEmpty())
    }

    @Test
    fun `queryCallLog resolves the matched contact id alongside the name`() {
        putContactWithNumber(1L, "Andreas Furrer", "079123")
        FakeCallLogProvider.putCall(1L, "079123", CallLog.Calls.OUTGOING_TYPE, date = 1L)

        // The id is what a history row needs to open the contact's detail screen — resolving the
        // name without it would leave that action dead.
        assertEquals(1L, CallLogStore.queryCallLog(context, 50).single().resolvedContactId)
    }

    @Test
    fun `an unknown number resolves to no contact id, not a crash`() {
        FakeCallLogProvider.putCall(1L, "079123", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        assertNull(CallLogStore.queryCallLog(context, 50).single().resolvedContactId)
    }

    /** A contact the address book can actually look up by number: the Contacts row plus one
     *  Phone data row, which is what ContactStore.resolveNumbers reads. */
    private fun putContactWithNumber(contactId: Long, name: String, number: String) {
        FakeContactsProvider.putContact(contactId, name)
        FakeContactsProvider.putData(
            id = contactId * 10,
            contactId = contactId,
            rawContactId = contactId,
            mimetype = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            values = ContentValues().apply {
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            },
        )
    }

    @Test
    fun `queryCallLog narrowed to one number returns only that number's entries`() {
        FakeCallLogProvider.putCall(1L, "079123", CallLog.Calls.OUTGOING_TYPE, date = 1L)
        FakeCallLogProvider.putCall(2L, "044555", CallLog.Calls.INCOMING_TYPE, date = 2L)
        FakeCallLogProvider.putCall(3L, "079123", CallLog.Calls.MISSED_TYPE, date = 3L)

        assertEquals(listOf(3L, 1L), CallLogStore.queryCallLog(context, 50, number = "079123").map { it.id })
    }
}
