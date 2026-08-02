package life.furrer.p9.call

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRegistryTest {

    @After
    fun tearDown() {
        // CallRegistry is a process-wide singleton -- drain it so tests don't leak state into
        // each other.
        CallRegistry.all().forEach { CallRegistry.remove(it) }
    }

    @Test
    fun `starts empty`() {
        assertTrue(CallRegistry.all().isEmpty())
    }

    @Test
    fun `add appends a call`() {
        val call = FakeCallSession("1")
        CallRegistry.add(call)
        assertEquals(listOf(call), CallRegistry.all())
    }

    @Test
    fun `remove drops a call by id`() {
        val call = FakeCallSession("1")
        CallRegistry.add(call)
        CallRegistry.remove(call)
        assertTrue(CallRegistry.all().isEmpty())
    }

    @Test
    fun `a second call arriving keeps the first -- call waiting`() {
        val first = FakeCallSession("1")
        val second = FakeCallSession("2")
        CallRegistry.add(first)
        CallRegistry.add(second)

        assertEquals(listOf(first, second), CallRegistry.all())
    }

    @Test
    fun `removing one of two calls leaves the other -- swap after the first ends`() {
        val first = FakeCallSession("1")
        val second = FakeCallSession("2")
        CallRegistry.add(first)
        CallRegistry.add(second)

        CallRegistry.remove(first)

        assertEquals(listOf(second), CallRegistry.all())
    }

    @Test
    fun `add notifies listeners`() {
        var notified = false
        val listener = { notified = true }
        CallRegistry.addListener(listener)
        try {
            CallRegistry.add(FakeCallSession("1"))
            assertTrue(notified)
        } finally {
            CallRegistry.removeListener(listener)
        }
    }

    @Test
    fun `notifyChanged pings listeners without changing the call list`() {
        var notifications = 0
        val listener: () -> Unit = { notifications++ }
        CallRegistry.addListener(listener)
        try {
            CallRegistry.notifyChanged()
            assertEquals(1, notifications)
            assertTrue(CallRegistry.all().isEmpty())
        } finally {
            CallRegistry.removeListener(listener)
        }
    }

    @Test
    fun `removeListener stops further notifications`() {
        var notifications = 0
        val listener: () -> Unit = { notifications++ }
        CallRegistry.addListener(listener)
        CallRegistry.removeListener(listener)

        CallRegistry.add(FakeCallSession("1"))

        assertEquals(0, notifications)
    }
}
