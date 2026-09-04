package cash.p.terminal.core.adapters.zcash

import java.math.BigDecimal
import java.math.RoundingMode

const val ZCASH_DECIMAL_COUNT = 8

private val ZATOSHI_PER_ZEC = BigDecimal(100_000_000L)

fun Long.convertZatoshiToZec(scale: Int = ZCASH_DECIMAL_COUNT): BigDecimal =
    BigDecimal(this).divide(ZATOSHI_PER_ZEC, scale, RoundingMode.HALF_EVEN)

fun BigDecimal.convertZecToZatoshi(): Long =
    multiply(ZATOSHI_PER_ZEC).setScale(0, RoundingMode.HALF_EVEN).toLong()
