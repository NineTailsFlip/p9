package life.furrer.p9.dial

import org.junit.Assert.assertEquals
import life.furrer.nine.core.util.NumberFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialStringTest {

    @Test
    fun `append builds up the digits in order`() {
        val result = DialString().append('0').append('7').append('9')
        assertEquals("079", result.digits)
    }

    @Test
    fun `backspace drops the last character`() {
        assertEquals("07", DialString("079").backspace().digits)
    }

    @Test
    fun `backspace on an empty string is a no-op`() {
        assertEquals("", DialString().backspace().digits)
    }

    @Test
    fun `clear resets to empty and exits service code mode`() {
        val cleared = DialString("*#06#", serviceCodeMode = true).clear()
        assertEquals("", cleared.digits)
        assertFalse(cleared.serviceCodeMode)
    }

    @Test
    fun `isEmpty reflects the digits only`() {
        assertTrue(DialString().isEmpty)
        assertFalse(DialString("0").isEmpty)
    }

    @Test
    fun `enterServiceCodeMode inserts a leading star and flips the mode on`() {
        val result = DialString().enterServiceCodeMode()
        assertEquals("*", result.digits)
        assertTrue(result.serviceCodeMode)
    }

    @Test
    fun `exitServiceCodeMode leaves the digits untouched`() {
        val result = DialString("*#06#", serviceCodeMode = true).exitServiceCodeMode()
        assertEquals("*#06#", result.digits)
        assertFalse(result.serviceCodeMode)
    }

    @Test
    fun `isDialable is true for a plain number`() {
        assertTrue(DialString("+41791234567").isDialable)
    }

    @Test
    fun `isDialable is false for an empty string`() {
        assertFalse(DialString().isDialable)
    }

    @Test
    fun `isDialable is true for a non-empty service code even though star is not normally dialable`() {
        assertTrue(DialString("*#06#", serviceCodeMode = true).isDialable)
    }

    @Test
    fun `formatted groups by the home country as it's typed, or not at all when as saved`() {
        assertEquals("079 123 45 67", DialString("0791234567").formatted(NumberFormat.BY_COUNTRY, "CH"))
        assertEquals("041 700 4", DialString("0417004").formatted(NumberFormat.BY_COUNTRY, "CH"))
        assertEquals("+44 7751 234023", DialString("+447751234023").formatted(NumberFormat.BY_COUNTRY, "CH"))
        assertEquals("0791234567", DialString("0791234567").formatted(NumberFormat.AS_SAVED, "CH"))
    }

    @Test
    fun `a service code is never regrouped`() {
        assertEquals("*#06#", DialString("*#06#", serviceCodeMode = true).formatted(NumberFormat.BY_COUNTRY, "CH"))
    }
}
