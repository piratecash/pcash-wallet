package cash.p.terminal.modules.send.beam

import java.math.BigDecimal

internal object BeamAmount {
    // Number of fractional digits BEAM atomic amounts (groth) are expressed in.
    const val DECIMALS = 8
    private val decimal = Regex("[0-9]+(?:[.,][0-9]{1,$DECIMALS})?")

    fun parse(text: String): Long? {
        if (text.length > 28 || !decimal.matches(text)) return null
        return try {
            toAtomic(BigDecimal(text.replace(',', '.')))
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Positive amounts that are a whole number of groth; trailing zeros beyond 8 decimals are fine. */
    fun toAtomic(value: BigDecimal): Long? = try {
        value.movePointRight(DECIMALS).stripTrailingZeros().longValueExact().takeIf { it > 0 }
    } catch (_: ArithmeticException) {
        null
    }

    fun format(atomic: Long): String = BigDecimal.valueOf(atomic, DECIMALS).toPlainString()
}
