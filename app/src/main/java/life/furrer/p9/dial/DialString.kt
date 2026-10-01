package life.furrer.p9.dial

import life.furrer.nine.core.util.StringMatch
import life.furrer.nine.core.util.NumberFormat
import life.furrer.nine.core.util.PhoneFormat

/** The dial field's own text-editing state — pure, no Android imports. Append/backspace/clear,
 *  `+` insertion (hold-0), and the "Service code" mode the `*` menu can enter (docs/p9-plan.md
 *  decision 2): while active, `*`/`#` are typed literally instead of meaning menu/favorites-
 *  toggle, so an MMI code like `*#06#` can be entered digit-by-digit. [formatted] follows the
 *  "Number format" setting via [PhoneFormat.formatTyping]: the typed number's own country's
 *  grouping, filled in as it's typed, with the SIM's country assumed for a number without a
 *  country code. */
data class DialString(val digits: String = "", val serviceCodeMode: Boolean = false) {

    fun append(char: Char): DialString = copy(digits = digits + char)

    fun backspace(): DialString = if (digits.isEmpty()) this else copy(digits = digits.dropLast(1))

    fun clear(): DialString = DialString()

    /** The menu's own "Service code" item: inserts the leading `*` every MMI code starts with and
     *  switches `*`/`#` to literal until [exitServiceCodeMode] (bound to Back on the dialer). */
    fun enterServiceCodeMode(): DialString = copy(digits = digits + "*", serviceCodeMode = true)

    fun exitServiceCodeMode(): DialString = copy(serviceCodeMode = false)

    val isEmpty: Boolean get() = digits.isEmpty()

    /** Whether the call key should have anything to dial at all. */
    val isDialable: Boolean get() = StringMatch.looksDialable(digits) || (serviceCodeMode && digits.isNotEmpty())

    fun formatted(style: NumberFormat, homeIso: String?): String = PhoneFormat.formatTyping(digits, style, homeIso)
}
