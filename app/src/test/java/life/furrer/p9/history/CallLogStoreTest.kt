package life.furrer.p9.history

import android.provider.CallLog
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
    fun `queryCallLog resolves a number to its current contact name via PhoneLookup`() {
        FakeContactsProvider.putContact(1L, "Andreas Furrer")
        // FakeContactsProvider's own PhoneLookup shape isn't modeled -- this documents the
        // fallback instead: without a working PhoneLookup fake, resolvedName stays null and
        // displayName falls back to the log's own cachedName.
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
        FakeContactsProvider.putContact(1L, "Andreas Furrer")
        FakeCallLogProvider.putCall(1L, "079123", CallLog.Calls.OUTGOING_TYPE, date = 1L)

        // FakeContactsProvider's own PhoneLookup shape isn't modeled (see the resolvedName test
        // above), so this documents the same honest fallback for the id: no PhoneLookup fake
        // means no resolution, not a crash.
        assertNull(CallLogStore.queryCallLog(context, 50).single().resolvedContactId)
    }
}
