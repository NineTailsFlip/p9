package life.furrer.p9.dial

import life.furrer.nine.core.util.StringMatch
import life.furrer.nine.core.util.NumberFormat
import life.furrer.nine.core.util.PhoneFormat

/** The dial field's own text-editing state — pure, no Android imports. Append/backspace/clear,
 *  `+` insertion (hold-0), and the "Service code" mode the `*` menu can enter: while active, `*`
 *  and `#` are typed literally instead of meaning menu/missed-calls toggle, so an MMI code like
 *  `*#06#` or `#31#` can be entered key by key. Outside the mode `#` is still typed literally
 *  once something is typed, and `*` comes from the menu's "Type *". [formatted] follows the
 *  "Number format" setting via [PhoneFormat.formatTyping]: the typed number's own country's
 *  grouping, filled in as it's typed, with the SIM's country assumed for a number without a
 *  country code. */
data class DialString(val digits: String = "", val serviceCodeMode: Boolean = false) {

    fun append(char: Char): DialString = copy(digits = digits + char)

    fun backspace(): DialString = if (digits.isEmpty()) this else copy(digits = digits.dropLast(1))

    fun clear(): DialString = DialString()

    /** The menu's own "Service code" item: switches `*`/`#` to literal until [clear] or
     *  [exitServiceCodeMode] (Back on an empty dial string). Inserts nothing, since codes start
     *  with `*` (`*#06#`) and `#` (`#31#`) alike. */
    fun enterServiceCodeMode(): DialString = copy(serviceCodeMode = true)

    fun exitServiceCodeMode(): DialString = copy(serviceCodeMode = false)

    val isEmpty: Boolean get() = digits.isEmpty()

    /** Whether the call key should have anything to dial at all. */
    val isDialable: Boolean get() = StringMatch.looksDialable(digits) || isServiceCode || (serviceCodeMode && digits.isNotEmpty())

    /** An MMI/USSD code (`*#06#`, `*100#`, `**21*079…#`): digits with a `*` or `#` in them. Placed
     *  through Telecom like a call, which hands it to the telephony stack instead of dialing. */
    val isServiceCode: Boolean
        get() = digits.isNotEmpty() && digits.all { it.isDigit() || it in "*#+" } && digits.any { it in "*#" }

    /** The `N` of an Android secret code `*#*#N#*#*` (`*#*#4636#*#*` is the testing menu). These
     *  never reach the network: they're broadcast to whichever app registered for them. */
    val secretCode: String?
        get() = SECRET_CODE.matchEntire(digits)?.groupValues?.get(1)

    fun formatted(style: NumberFormat, homeIso: String?): String = PhoneFormat.formatTyping(digits, style, homeIso)

    private companion object {
        val SECRET_CODE = Regex("[*]#[*]#([0-9]+)#[*]#[*]")
    }
}
